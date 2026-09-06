package lsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TarExtractorTest {

	@TempDir
	Path tempDir;

	@Test
	void extractsRegularFile() throws Exception {
		byte[] tar = createTarFile("hello.txt", "Hallo ThIDE!");

		TarExtractor.extractTar(
			new ByteArrayInputStream(tar),
			tempDir.toFile()
		);

		Path extracted = tempDir.resolve("hello.txt");

		assertTrue(Files.exists(extracted));
		assertEquals(
			"Hallo ThIDE!",
			Files.readString(extracted)
		);
	}

	@Test
	void extractsDirectoryAndFileInsideIt() throws Exception {
		byte[] directoryHeader = createHeader(
			"testdir/",
			0,
			'5'
		);

		byte[] fileContent = "Testinhalt".getBytes(StandardCharsets.UTF_8);

		byte[] fileHeader = createHeader(
			"testdir/file.txt",
			fileContent.length,
			'0'
		);

		ByteArrayOutputStream archive = new ByteArrayOutputStream();

		archive.write(directoryHeader);

		archive.write(fileHeader);
		archive.write(fileContent);

		int padding = (512 - (fileContent.length % 512)) % 512;
		archive.write(new byte[padding]);

		// TAR-Ende
		archive.write(new byte[1024]);

		TarExtractor.extractTar(
			new ByteArrayInputStream(archive.toByteArray()),
			tempDir.toFile()
		);

		Path directory = tempDir.resolve("testdir");
		Path file = directory.resolve("file.txt");

		assertTrue(
			Files.isDirectory(directory),
			"Das Verzeichnis sollte extrahiert worden sein."
		);

		assertTrue(
			Files.isRegularFile(file),
			"Die Datei sollte extrahiert worden sein."
		);

		assertEquals(
			"Testinhalt",
			Files.readString(file)
		);
	}

	@Test
	void rejectsPathTraversal() {
		byte[] tar = createTarFile("../evil.txt", "gefährlich");

		assertThrows(
			IOException.class,
			() -> TarExtractor.extractTar(
				new ByteArrayInputStream(tar),
				tempDir.toFile()
			)
		);

		assertFalse(Files.exists(tempDir.getParent().resolve("evil.txt")));
	}

	@Test
	void rejectsAbsolutePath() {
		byte[] tar = createTarFile(
			"/absolute/path/evil.txt",
			"gefährlich"
		);

		assertThrows(
			IOException.class,
			() -> TarExtractor.extractTar(
				new ByteArrayInputStream(tar),
				tempDir.toFile()
			)
		);
	}

	@Test
	void rejectsTruncatedTarHeader() {
		byte[] truncated = new byte[100];

		assertThrows(
			IOException.class,
			() -> TarExtractor.extractTar(
				new ByteArrayInputStream(truncated),
				tempDir.toFile()
			)
		);
	}

	@Test
	void rejectsTruncatedFileContent() {
		byte[] tar = createTarFile("test.txt", "Dieser Inhalt wird abgeschnitten.");

		// Nur den Header behalten, aber den Dateiinhalt abschneiden.
		byte[] truncated = new byte[512 + 5];
		System.arraycopy(tar, 0, truncated, 0, truncated.length);

		assertThrows(
			IOException.class,
			() -> TarExtractor.extractTar(
				new ByteArrayInputStream(truncated),
				tempDir.toFile()
			)
		);
	}

	@Test
	void rejectsEntryLargerThanMaximum() {
		byte[] header = createHeader(
			"huge.bin",
			256L * 1024 * 1024 + 1,
			'0'
		);

		assertThrows(
			IOException.class,
			() -> TarExtractor.extractTar(
				new ByteArrayInputStream(header),
				tempDir.toFile()
			)
		);
	}

	@Test
	void skipsSymlinkEntries() throws Exception {
		byte[] tar = createHeader(
			"link",
			0,
			'2'
		);

		byte[] archive = concatenate(
			tar,
			new byte[1024]
		);

		assertDoesNotThrow(() ->
			TarExtractor.extractTar(
				new ByteArrayInputStream(archive),
				tempDir.toFile()
			)
		);

		assertFalse(Files.exists(tempDir.resolve("link")));
	}

	// -------------------------------------------------------------------------
	// Hilfsmethoden zum Erzeugen kleiner TAR-Testarchive
	// -------------------------------------------------------------------------

	private static byte[] createTarFile(
		String name,
		String content
	) {
		byte[] data = content.getBytes(StandardCharsets.UTF_8);

		byte[] header = createHeader(
			name,
			data.length,
			'0'
		);

		return createArchive(header, data);
	}

	private static byte[] createTarDirectory(String name) {
		byte[] header = createHeader(
			name,
			0,
			'5'
		);

		return createArchive(header);
	}

	private static byte[] createArchive(
		byte[]... parts
	) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {
			for (byte[] part : parts) {
				out.write(part);
			}

			// TAR-Ende: zwei 512-Byte-Nullblöcke.
			out.write(new byte[1024]);

			return out.toByteArray();

		} catch (IOException e) {
			throw new AssertionError("Konnte Test-TAR nicht erzeugen.", e);
		}
	}

	private static byte[] concatenate(
		byte[] first,
		byte[] second
	) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {
			out.write(first);
			out.write(second);

			// Entferne die 1024 Bytes TAR-Endemarkierung
			// des ersten Archivs nicht explizit:
			//
			// TarExtractor beendet sich beim ersten Nullblock.
			//
			// Deshalb bauen wir hier ein neues Archiv aus den
			// eigentlichen Einträgen.

			return out.toByteArray();

		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static byte[] createArchive(
		byte[] header,
		byte[] data
	) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {
			out.write(header);
			out.write(data);

			int padding = (512 - (data.length % 512)) % 512;

			if (padding > 0) {
				out.write(new byte[padding]);
			}

			out.write(new byte[1024]);

			return out.toByteArray();

		} catch (IOException e) {
			throw new AssertionError(
				"Konnte Test-TAR nicht erzeugen.",
				e
			);
		}
	}

	private static byte[] createHeader(
		String name,
		long size,
		char type
	) {
		byte[] header = new byte[512];

		writeString(
			header,
			0,
			100,
			name
		);

		writeOctal(
			header,
			100,
			8,
			0755
		);

		writeOctal(
			header,
			108,
			8,
			0
		);

		writeOctal(
			header,
			116,
			8,
			0
		);

		writeOctal(
			header,
			124,
			12,
			size
		);

		writeOctal(
			header,
			136,
			12,
			System.currentTimeMillis() / 1000
		);

		// Für die Checksumme zunächst Leerzeichen.
		for (int i = 148; i < 156; i++) {
			header[i] = ' ';
		}

		header[156] = (byte) type;

		writeString(
			header,
			257,
			6,
			"ustar"
		);

		writeString(
			header,
			263,
			2,
			"00"
		);

		long checksum = 0;

		for (byte b : header) {
			checksum += b & 0xFF;
		}

		writeOctal(
			header,
			148,
			8,
			checksum
		);

		return header;
	}

	private static void writeString(
		byte[] buffer,
		int offset,
		int length,
		String value
	) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

		int count = Math.min(
			bytes.length,
			length
		);

		System.arraycopy(
			bytes,
			0,
			buffer,
			offset,
			count
		);
	}

	private static void writeOctal(
		byte[] buffer,
		int offset,
		int length,
		long value
	) {
		String octal = Long.toOctalString(value);

		int digits = Math.min(
			octal.length(),
			length - 1
		);

		int start = offset + length - 1 - digits;

		for (int i = 0; i < digits; i++) {
			buffer[start + i] =
			(byte) octal.charAt(i);
		}

		buffer[offset + length - 1] = 0;
	}
}