package lsp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
* Sehr kompakter, abhängigkeitsfreier TAR- bzw. TAR.GZ-Extractor.
*
* Wird verwendet, um automatisch heruntergeladene Language-Server-Archive
* (z. B. den Eclipse JDT Language Server, der als .tar.gz ausgeliefert
	* wird) ohne externe Bibliothek zu entpacken.
*
* Unterstützt das klassische ustar-Format sowie die beiden in der Praxis
* fast immer benötigten Erweiterungen für Pfade &gt; 100 Zeichen:
*   - GNU-Longname/-Longlink-Einträge (Typeflag 'L' / 'K')
*   - POSIX-PAX-Extended-Header (Typeflag 'x' / 'g', Feld "path")
* Symlinks/Hardlinks werden übersprungen (für Language-Server-Archive
	* nicht relevant); reguläre Dateien und Verzeichnisse werden entpackt,
* das Ausführbar-Bit von Dateien wird aus dem Unix-Modus übernommen.
*/
public final class TarExtractor {

	private TarExtractor() {}

	public static void extractTarGz(InputStream rawIn, File destDir) throws IOException {
		try (GZIPInputStream gzIn = new GZIPInputStream(rawIn);
			BufferedInputStream in = new BufferedInputStream(gzIn, 1 << 16)) {
			extractTar(in, destDir);
		}
	}

	public static void extractTar(InputStream in, File destDir) throws IOException {
		byte[] header = new byte[512];
		String pendingLongName = null;

		while (true) {
			int n = readFully(in, header);
			if (n == 0) {
				break;
			}

			if (n < 512) {
				throw new IOException("Unerwartetes Ende des TAR-Archivs.");
			}

			if (isAllZero(header)) {
				break;
			}

			char typeflag = (char) (header[156] & 0xFF);
			long size = parseOctal(header, 124, 12);

			String name = (pendingLongName != null) ? pendingLongName : parseName(header);
			pendingLongName = null;

			if (typeflag == 'L') {
				byte[] content = readExact(in, size);
				pendingLongName = zeroTerminatedString(content);
				skipPadding(in, size);
				continue;
			}
			if (typeflag == 'K') {
				// Long-Linkname für Symlinks - für uns irrelevant, nur überspringen.
				skipExact(in, size);
				skipPadding(in, size);
				continue;
			}
			if (typeflag == 'x' || typeflag == 'g') {
				byte[] content = readExact(in, size);
				String path = parsePaxPath(content);
				if (path != null) pendingLongName = path;
				skipPadding(in, size);
				continue;
			}

			if (typeflag == '5') { // Verzeichnis
				resolveSafely(destDir, name).mkdirs();
				skipExact(in, size);
				skipPadding(in, size);
			} else if (typeflag == '0' || typeflag == '\0') { // reguläre Datei
				File outFile = resolveSafely(destDir, name);
				File parent = outFile.getParentFile();
				if (parent != null) parent.mkdirs();
				try (OutputStream out = new FileOutputStream(outFile)) {
					copyExact(in, out, size);
				}
				skipPadding(in, size);
				int mode = (int) parseOctal(header, 100, 8);
				if ((mode & 0111) != 0) {
					outFile.setExecutable(true, false);
				}
			} else {
				// Symlink, Hardlink, Gerätedatei, … - für uns nicht relevant.
				skipExact(in, size);
				skipPadding(in, size);
			}
		}
	}

	/**
	* Baut den Zielpfad für einen Archiv-Eintrag und stellt sicher, dass er
	* innerhalb von {@code destDir} bleibt (Schutz vor "Zip Slip" /
		* Path-Traversal, z. B. durch Einträge wie "../../etwas" oder
		* absolute Pfade im Archiv).
	*
	* @throws IOException falls der Eintrag versucht, destDir zu verlassen
	*/
	private static File resolveSafely(File destDir, String name) throws IOException {
		Path destPath = destDir.toPath().normalize().toAbsolutePath();
		Path targetPath = destPath.resolve(name).normalize().toAbsolutePath();

		if (!targetPath.startsWith(destPath)) {
			throw new IOException(
				"Unsicherer Archiv-Eintrag außerhalb des Zielverzeichnisses: " + name);
		}
		return targetPath.toFile();
	}

	// ── Hilfsfunktionen ──────────────────────────────────────────────────

	private static int readFully(InputStream in, byte[] buf) throws IOException {
		int total = 0;

		while (total < buf.length) {
			int r = in.read(buf, total, buf.length - total);

			if (r < 0) {
				break;
			}

			if (r == 0) {
				continue;
			}

			total += r;
		}

		return total;
	}

	private static boolean isAllZero(byte[] buf) {
		for (byte b : buf) if (b != 0) return false;
		return true;
	}

	private static long parseOctal(byte[] header, int offset, int length) {
		// GNU-Base256-Kodierung (High-Bit im ersten Byte gesetzt) wird für
		// die hier relevanten, moderat großen Dateien nicht benötigt.
		int start = offset, end = offset + length;
		while (start < end && (header[start] == ' ' || header[start] == 0)) start++;
		long value = 0;
		for (int i = start; i < end; i++) {
			byte b = header[i];
			if (b < '0' || b > '7') break;
			value = (value << 3) + (b - '0');
		}
		return value;
	}

	private static String parseName(byte[] header) {
		String name = new String(header, 0, 100, StandardCharsets.UTF_8).trim();
		int nul = name.indexOf('\0');
		if (nul >= 0) name = name.substring(0, nul);

		String magic = new String(header, 257, 6, StandardCharsets.US_ASCII);
		if (magic.startsWith("ustar")) {
			String prefix = new String(header, 345, 155, StandardCharsets.UTF_8);
			int pnul = prefix.indexOf('\0');
			if (pnul >= 0) prefix = prefix.substring(0, pnul);
			prefix = prefix.trim();
			if (!prefix.isEmpty()) name = prefix + "/" + name;
		}
		return name;
	}

	private static String zeroTerminatedString(byte[] content) {
		int len = content.length;
		for (int i = 0; i < content.length; i++) {
			if (content[i] == 0) { len = i; break; }
		}
		return new String(content, 0, len, StandardCharsets.UTF_8);
	}

	/** Parst PAX-Extended-Header-Records ("<len> key=value\n"...) und liefert den "path"-Wert, falls vorhanden. */
	private static String parsePaxPath(byte[] content) {
		String text = new String(content, StandardCharsets.UTF_8);
		int pos = 0;
		while (pos < text.length()) {
			int spaceIdx = text.indexOf(' ', pos);
			if (spaceIdx < 0) break;
			int recLen;
			try {
				recLen = Integer.parseInt(text.substring(pos, spaceIdx).trim());
			} catch (NumberFormatException ex) {
				break;
			}
			if (recLen <= 0 || pos + recLen > text.length()) break;
			String record = text.substring(spaceIdx + 1, pos + recLen); // "key=value\n"
			int eq = record.indexOf('=');
			if (eq > 0) {
				String key = record.substring(0, eq);
				String value = record.substring(eq + 1);
				if (value.endsWith("\n")) value = value.substring(0, value.length() - 1);
				if (key.equals("path")) return value;
			}
			pos += recLen;
		}
		return null;
	}

	private static byte[] readExact(InputStream in, long size) throws IOException {
		if (size < 0 || size > MAX_ENTRY_SIZE) {
			throw new IOException("TAR entry too large: " + size);
		}
		byte[] data = new byte[(int) size];
		int total = 0;
		while (total < data.length) {
			int r = in.read(data, total, data.length - total);
			if (r < 0) {
				throw new IOException("Unerwartetes Ende des TAR-Archivs.");
			}
			total += r;
		}
		return data;
	}

	private static final long MAX_ENTRY_SIZE = 256L * 1024 * 1024; // 256 MiB

	private static void skipExact(InputStream in, long size) throws IOException {
		long remaining = size;
		byte[] buf = new byte[8192];
		while (remaining > 0) {
			int toRead = (int) Math.min(buf.length, remaining);
			int r = in.read(buf, 0, toRead);
			if (r < 0) {
				throw new IOException("Unerwartetes Ende des TAR-Archivs.");
			}
			remaining -= r;
		}
	}

	private static void copyExact(InputStream in, OutputStream out, long size) throws IOException {
		byte[] buf = new byte[8192];
		long remaining = size;
		while (remaining > 0) {
			int toRead = (int) Math.min(buf.length, remaining);
			int r = in.read(buf, 0, toRead);
			if (r < 0) {
				throw new IOException("Unerwartetes Ende des TAR-Archivs.");
			}
			out.write(buf, 0, r);
			remaining -= r;
		}
	}

	private static void skipPadding(InputStream in, long size) throws IOException {
		long padded = (size % 512 == 0) ? size : size + (512 - (size % 512));
		long pad = padded - size;
		if (pad > 0) skipExact(in, pad);
	}
}