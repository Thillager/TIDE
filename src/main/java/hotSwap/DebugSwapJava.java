package hotSwap;

import config.LanguageManager;
import editor.CompilerErrorMarker;
import editor.EditorManager;
import runner.DebugRunner;
import ui.ConsolePanel;

import java.awt.Color;
import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DebugSwapJava implements DebugRunner.DebugStrategy {

	public static final int DEBUG_PORT_START = 5005;

	private final ConsolePanel consolePanel;
	private final EditorManager editorManager;
	private final CompilerErrorMarker errorMarker;
	private final File currentProjectFolder;

	/** Wird nach execute() gesetzt und von hotSwap() genutzt */
	private int activeDebugPort = -1;
	private File activeSourceRoot = null;

	public DebugSwapJava(ConsolePanel consolePanel, EditorManager editorManager,
		CompilerErrorMarker errorMarker, File currentProjectFolder) {
		this.consolePanel = consolePanel;
		this.editorManager = editorManager;
		this.errorMarker = errorMarker;
		this.currentProjectFolder = currentProjectFolder;
	}

	@Override
	public Process execute(String mainClass) throws InterruptedException {
		String mc = mainClass;
		if (mc.endsWith(".java")) mc = mc.substring(0, mc.length() - 5);

		File outFolder = new File(currentProjectFolder, "out");
		if (!outFolder.exists()) outFolder.mkdirs();

		consolePanel.log("[DEBUG JAVA] " + LanguageManager.t("compiling") + "...\n", Color.CYAN);

		File mainFile = findSourceFile(currentProjectFolder, mc);
		if (mainFile == null) {
			consolePanel.log("[DEBUG] " + LanguageManager.t("file_not_found") + ": " + mc + "\n", Color.RED);
			return null;
		}

		activeSourceRoot = calculateSourceRoot(mainFile, mc);

		List<File> javaFiles = new ArrayList<>();
		collectAllJavaFiles(currentProjectFolder, javaFiles);
		if (javaFiles.isEmpty()) {
			consolePanel.log("[DEBUG] " + LanguageManager.t("file_not_found") + "\n", Color.RED);
			return null;
		}

		File sourcesListFile = new File(currentProjectFolder, ".sources.txt");
		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
					new FileOutputStream(sourcesListFile), StandardCharsets.UTF_8))) {
			for (File f : javaFiles) writer.println(f.getAbsolutePath());
		} catch (IOException e) {
			consolePanel.log("[DEBUG] Error creating source list: " + e.getMessage() + "\n", Color.RED);
			return null;
		}

		String sep = System.getProperty("path.separator");
		String classpath = "out" + sep + "libs/*";

		String compileCmd = "javac -encoding UTF-8 -g -Xlint:all -deprecation -cp \"" + classpath + "\" "
		+ "-d out "
		+ "-sourcepath \"" + activeSourceRoot.getAbsolutePath() + "\" "
		+ "\"@" + sourcesListFile.getName() + "\"";

		activeDebugPort = findFreePort(DEBUG_PORT_START);
		if (activeDebugPort == -1) {
			consolePanel.log("[DEBUG] Kein freier Debug-Port (5005–5055) gefunden.\n", Color.RED);
			return null;
		}

		String debugCmd = "java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address="
		+ activeDebugPort + " -cp \"out" + sep + "libs/*\" " + mc;

		consolePanel.log("> " + compileCmd + " && " + debugCmd + "\n", Color.GRAY);
		consolePanel.log("[DEBUG] JDWP-Port: " + activeDebugPort + " — Prozess läuft, kein Neustart nötig.\n", Color.YELLOW);

		try {
			ProcessBuilder pb = isWindows()
			? new ProcessBuilder("cmd.exe", "/c", compileCmd + " && " + debugCmd)
			: new ProcessBuilder("bash", "-c", compileCmd + " && " + debugCmd);
			pb.directory(currentProjectFolder);
			pb.redirectErrorStream(true);
			Process p = pb.start();

			new Thread(() -> {
					try (BufferedReader r = new BufferedReader(
							new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
						String line;
						while ((line = r.readLine()) != null)
						consolePanel.log(line + "\n", Color.WHITE);
					} catch (IOException e) {
						consolePanel.log("[DEBUG] IO Error: " + e.getMessage() + "\n", Color.RED);
					}
				}).start();

			return p;
		} catch (IOException e) {
			consolePanel.log("[DEBUG] Fehler: " + e.getMessage() + "\n", Color.RED);
			return null;
		}
	}

	public void hotSwap() {
		if (activeDebugPort == -1 || activeSourceRoot == null) {
			consolePanel.log("[HOTSWAP] Kein laufender Debug-Prozess.\n", Color.ORANGE);
			return;
		}

		editorManager.saveCurrentFile();

		List<File> javaFiles = new ArrayList<>();
		collectAllJavaFiles(currentProjectFolder, javaFiles);

		HotSwapEngine engine = new HotSwapEngine(consolePanel, currentProjectFolder);
		new Thread(() -> engine.hotSwap(activeSourceRoot, javaFiles, activeDebugPort)).start();
	}

	public int getActiveDebugPort() { return activeDebugPort; }

	// ---- Hilfsmethoden ----

	private int findFreePort(int startPort) {
		for (int port = startPort; port < startPort + 50; port++) {
			try (ServerSocket s = new ServerSocket(port)) {
				s.setReuseAddress(true);
				return port;
			} catch (IOException e) {
				e.printStackTrace();
				}
		}
		return -1;
	}

	private File calculateSourceRoot(File sourceFile, String fqcn) {
		int dotCount = 0;
		for (char c : fqcn.toCharArray()) if (c == '.') dotCount++;
		File root = sourceFile.getParentFile();
		for (int i = 0; i < dotCount; i++)
		if (root != null && root.getParentFile() != null) root = root.getParentFile();
		return root;
	}

	private void collectAllJavaFiles(File dir, List<File> list) {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File f : files) {
			if (f.isDirectory()) {
				String name = f.getName();
				if (name.equals(".git") || name.equals("out") || name.equals("libs") || name.equals("target")) continue;
				collectAllJavaFiles(f, list);
			} else if (f.getName().endsWith(".java")) {
				list.add(f);
			}
		}
	}

	private File findSourceFile(File dir, String fqcn) {
		String rel = fqcn.replace(".", File.separator) + ".java";
		File direct = new File(dir, rel);
		if (direct.exists()) return direct;
		File srcMain = new File(dir, "src" + File.separator + "main" + File.separator + "java");
		if (srcMain.exists()) { File f = new File(srcMain, rel); if (f.exists()) return f; }
		if (!fqcn.contains(".")) return searchFileRecursively(dir, fqcn + ".java");
		return null;
	}

	private File searchFileRecursively(File dir, String fileName) {
		File[] files = dir.listFiles();
		if (files == null) return null;
		for (File f : files) {
			if (f.isDirectory()) {
				if (f.getName().equals(".git") || f.getName().equals("out")) continue;
				File found = searchFileRecursively(f, fileName);
				if (found != null) return found;
			} else if (f.getName().equals(fileName)) return f;
		}
		return null;
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
}
