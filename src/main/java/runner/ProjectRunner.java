package runner;

import config.LanguageManager;
import config.TIDEPreferences;
import editor.CompilerErrorMarker;
import editor.EditorManager;
import ui.ConsolePanel;
import ui.SettingsDialog;
import ui.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ProjectRunner {

	public static final String MODE_JAVA   = "Java";
	public static final String MODE_PYTHON = "Python";
	public static final String MODE_C      = "C";
	public static final String MODE_CPP    = "C++";
	public static final String MODE_BATCH  = "Batch";
	public static final String MODE_CS	    = "C#";

	private final ConsolePanel consolePanel;
	private final EditorManager editorManager;
	private final CompilerErrorMarker errorMarker;
	private File currentProjectFolder;

	private JButton btnTerminate;

	private volatile Process runningProcess;

	private MainWindow mainWindow;

	public ProjectRunner(ConsolePanel consolePanel, EditorManager editorManager,
		CompilerErrorMarker errorMarker, MainWindow mainWindow) {
		this.consolePanel  = consolePanel;
		this.editorManager = editorManager;
		this.errorMarker   = errorMarker;
		this.mainWindow = mainWindow;
	}

	// 2. Einen einfachen Setter hinzufügen
	public void setTerminateButton(JButton btnTerminate) {
		this.btnTerminate = btnTerminate;
	}

	public void setCurrentProjectFolder(File folder) {
		this.currentProjectFolder = folder;
	}

	public void stopRunningProcess() {
		if (runningProcess != null && runningProcess.isAlive()) {
			runningProcess.descendants().forEach(ProcessHandle::destroy);
			runningProcess.destroyForcibly();
			runningProcess = null;
			consolePanel.log("[INFO] Prozess beendet.\n", Color.ORANGE);

			// Button direkt ausblenden
			if (btnTerminate != null) {
				SwingUtilities.invokeLater(() -> btnTerminate.setVisible(false));
			}
		} else {
			consolePanel.log("[INFO] Kein laufender Prozess.\n", Color.GRAY);
		}
	}

	private void startResourceMonitor() {
		new Thread(() -> {
				com.sun.management.OperatingSystemMXBean os =
				(com.sun.management.OperatingSystemMXBean)
				java.lang.management.ManagementFactory.getOperatingSystemMXBean();

				while (runningProcess != null && runningProcess.isAlive()) {
					try {
						Thread.sleep(1000);

						double cpuLoad = os.getCpuLoad() * 100;
						double ramLoad = (double)(os.getTotalMemorySize() - os.getFreeMemorySize())
						/ os.getTotalMemorySize() * 100;

						if (cpuLoad >= 95 || ramLoad >= 95) {
							consolePanel.log("[WARNUNG] Ressourcen kritisch (CPU: "
									+ (int)cpuLoad + "% RAM: " + (int)ramLoad
									+ "%) — Prozess wird beendet.\n", Color.ORANGE);
							stopRunningProcess();
							return;
						}
					} catch (InterruptedException e) {
						return;
					}
				}
			}, "TideResourceMonitor").start();
	}

	public void runProject(String mode, String mainClass) {
		File activeFile = editorManager.getActiveFile();

		if (currentProjectFolder == null) {
			consolePanel.log(LanguageManager.t("no_project") + "\n", Color.ORANGE);
			return;
		}

		if (activeFile != null) {
			// Prüfen, welche-Datei es sich handelt
			if (activeFile.getName().endsWith(".py")) {
				mode = MODE_PYTHON;
				mainWindow.setSelectedMode(MainWindow.MODE_PYTHON);
			} else if (activeFile.getName().endsWith(".java")) {
				mode = MODE_JAVA;
				mainWindow.setSelectedMode(MainWindow.MODE_JAVA);
			} else if (activeFile.getName().endsWith(".c")) {
				mode = MODE_C;
				mainWindow.setSelectedMode(MainWindow.MODE_C);
			} else if (activeFile.getName().endsWith(".cpp")) {
				mode = MODE_CPP;
				mainWindow.setSelectedMode(MainWindow.MODE_CPP);
			} else if (activeFile.getName().endsWith(".bat")) {
				mode = MODE_BATCH;
				mainWindow.setSelectedMode(MainWindow.MODE_BATCH);
				}
			}

			String mc = mainClass;
			if (mc.endsWith(".java")) mc = mc.substring(0, mc.length() - 5);
			editorManager.saveAllFiles();

			boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

			switch (mode) {
				case MODE_JAVA:
				if (mc.isEmpty()) {
					consolePanel.log(LanguageManager.t("no_main") + "\n", Color.RED);
					return;
				}

				File outFolder = new File(currentProjectFolder, "out");
				if (!outFolder.exists()) outFolder.mkdirs();

				consolePanel.log(LanguageManager.t("compiling") + "\n", Color.CYAN);

				// 1. Hauptdatei finden
				File mainFile = findSourceFile(currentProjectFolder, mc);
				if (mainFile == null) {
					consolePanel.log(LanguageManager.t("file_not_found") + ": " + mc + "\n", Color.RED);
					return;
				}

				// 2. Source-Root dynamisch berechnen
				File sourceRoot = calculateSourceRoot(mainFile, mc);

				// 3. Alle Java-Dateien im gesamten Projekt sammeln
				List<File> javaFiles = new ArrayList<>();
				collectAllJavaFiles(currentProjectFolder, javaFiles);

				if (javaFiles.isEmpty()) {
					consolePanel.log(LanguageManager.t("file_not_found") + "\n", Color.RED);
					return;
				}

				// 4. Argument-Datei erstellen (@sources.txt) mit absolutem Löschschutz
				File sourcesListFile = new File(currentProjectFolder, ".sources.txt");

				// HIER: Erzwinge das Löschen der alten Datei, falls sie blockiert ist
				if (sourcesListFile.exists()) {
					sourcesListFile.delete();
				}

				try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
							new FileOutputStream(sourcesListFile), StandardCharsets.UTF_8))) {
					for (File f : javaFiles) {
						String normalizedPath = f.getAbsolutePath().replace('\\', '/');

						// Absolute Sperre für ALLE Pfade, die "test" enthalten
						if (normalizedPath.contains("/src/test/") || normalizedPath.contains("/test/") || normalizedPath.endsWith("/test.java")) {
							continue;
						}

						writer.println(f.getAbsolutePath());
					}
				} catch (IOException e) {
					consolePanel.log("Error creating source list: " + e.getMessage() + "\n", Color.RED);
					return;
				}

				String separator = System.getProperty("path.separator");
				String classpath = "out" + separator + "libs/*";

				File libsDir = new File(currentProjectFolder, "libs");

				boolean hasJavaFx = false;
				StringBuilder javafxModules = new StringBuilder();

				if (libsDir.exists()) {
					File[] jars = libsDir.listFiles();

					if (jars != null) {
						for (File jar : jars) {
							String name = jar.getName().toLowerCase();

							if (name.startsWith("javafx-") && name.endsWith(".jar")) {
								hasJavaFx = true;

								String module =
								name.substring(0, name.indexOf(".jar"))
								.replaceAll("-\\d+.*$", "")
								.replace('-', '.');

								if (!javafxModules.isEmpty()) {
									javafxModules.append(",");
								}

								javafxModules.append(module);
							}
						}
					}
				}

				String compileCmd;
				String runCmd;

				if (hasJavaFx) {
					compileCmd =
					"javac " +
					"--module-path libs " +
					"--add-modules " + javafxModules +
					" -encoding UTF-8 -nowarn -Xlint:none " +
					"-cp \"" + classpath + "\" " +
					"-d out " +
					"-sourcepath \"" + sourceRoot.getAbsolutePath() + "\" " +
					"\"@" + sourcesListFile.getName() + "\"";

					runCmd =
					"java " +
					"--module-path libs " +
					"--add-modules " + javafxModules +
					" --enable-native-access=ALL-UNNAMED " +
					"-cp \"out" + separator + "libs/*\" " +
					mc;

				} else {
					compileCmd =
					"javac -encoding UTF-8 -nowarn -Xlint:none " +
					"-cp \"" + classpath + "\" " +
					"-d out " +
					"-sourcepath \"" + sourceRoot.getAbsolutePath() + "\" " +
					"\"@" + sourcesListFile.getName() + "\"";

					runCmd =
					"java --enable-native-access=ALL-UNNAMED " +
					"-cp \"out" + separator + "libs/*\" " +
					mc;
				}

				// 5. Ressourcen kopieren (mit Ausschluss von Test-Ressourcen)
				try {
					File resourcesDir = new File(currentProjectFolder, "src/main/resources");
					if (resourcesDir.exists()) {
						copyDirectory(resourcesDir.toPath(), new File(currentProjectFolder, "out").toPath());
					}

					File srcDir = new File(currentProjectFolder, "src");
					if (srcDir.exists()) {
						File outDir = new File(currentProjectFolder, "out");
						java.nio.file.Files.walk(srcDir.toPath()).forEach(path -> {
								try {
									String relativePathStr = srcDir.toPath().relativize(path).toString();
									// Ignoriere Ressourcen im Test-Pfad
									if (relativePathStr.startsWith("test") || relativePathStr.contains(File.separator + "test" + File.separator)) {
										return;
									}

									String fileName = path.getFileName().toString().toLowerCase();
									if (java.nio.file.Files.isRegularFile(path) && (fileName.endsWith(".xml") || fileName.endsWith(".png"))) {
										java.nio.file.Path relativePath = srcDir.toPath().relativize(path);

										if (relativePath.toString().startsWith("main" + File.separator + "resources")) {
											relativePath = new File(currentProjectFolder, "src/main/resources").toPath().relativize(path);
										}

										java.nio.file.Path destination = outDir.toPath().resolve(relativePath);
										java.nio.file.Files.createDirectories(destination.getParent());
										java.nio.file.Files.copy(path, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
									}
								} catch (IOException e) {
									consolePanel.log("[RUNNER WARN] Ressource konnte nicht kopiert werden: " + e.getMessage() + "\n", Color.ORANGE);
								}
							});
					}
				} catch (Exception e) {
					consolePanel.log("[RUNNER FEHLER] Fehler beim Ressourcen-Kopieren: " + e.getMessage() + "\n", Color.RED);
				}

				executeCommand(compileCmd + " && " + runCmd, false);
				break;

				case MODE_BATCH:
				File activeBat = editorManager.getActiveFile();
				if (activeBat == null ||
					!(activeBat.getName().endsWith(".bat") ||
						activeBat.getName().endsWith(".cmd"))) {
					consolePanel.log(LanguageManager.t("no_batch_file") + "\n", Color.RED);
					return;
				}
				consolePanel.log("[BATCH] " + activeBat.getName() + "...\n", Color.CYAN);
				if (isWindows) {
					executeCommand("cmd /c \"" + activeBat.getAbsolutePath() + "\"", false);
				} else {
					consolePanel.log("[BATCH] Batch-Dateien laufen nur unter Windows.\n", Color.RED);
				}
				break;

				case MODE_C:
				case MODE_CPP:
				File activeC = editorManager.getActiveFile();
				if (activeC != null) {
					String fileName = activeC.getName();

					// Prüfen, ob die richtige Dateiendung verwendet wird
					if (mode.equals(MODE_C) && !fileName.endsWith(".c")) {
						consolePanel.log(LanguageManager.t("no_c_file") + "\n", Color.RED);
						return;
					}

					if (mode.equals(MODE_CPP)
						&& !(fileName.endsWith(".cpp")
							|| fileName.endsWith(".cc")
							|| fileName.endsWith(".cxx"))) {
						consolePanel.log(LanguageManager.t("no_cpp_file") + "\n", Color.RED);
						return;
					}

					String compiler = mode.equals(MODE_C) ? "gcc" : "g++";
					String outputName = System.getProperty("os.name").toLowerCase().contains("win")
					? "program.exe"
					: "program";

					// Unter Unix muss das Programm mit ./ gestartet werden
					String runCommand = System.getProperty("os.name").toLowerCase().contains("win")
					? outputName
					: "./" + outputName;

					consolePanel.log(
						"[" + mode.toUpperCase() + "] "
						+ LanguageManager.t("compiling")
						+ " " + fileName + "...\n",
						Color.CYAN
					);

					executeCommand(
						compiler
						+ " \"" + activeC.getAbsolutePath() + "\""
						+ " -o " + outputName
						+ " && " + runCommand,
						false
					);
				} else {
					if (mode.equals(MODE_C)) {
						consolePanel.log(LanguageManager.t("no_c_file") + "\n", Color.RED);
					} else {
						consolePanel.log(LanguageManager.t("no_cpp_file") + "\n", Color.RED);
					}
				}
				break;



				case MODE_PYTHON:
				File activePy = editorManager.getActiveFile();
				if (activePy != null) {
					// Prüfen, ob es sich um eine Python-Datei handelt
					if (!activePy.getName().endsWith(".py")) {
						consolePanel.log(LanguageManager.t("no_python_file") + "\n", Color.RED);
						return;
					}

					// Python ausführen
					consolePanel.log("[PYTHON] " + activePy.getName() + "...\n", Color.CYAN);

					// Unter Windows und Linux/macOS funktioniert "python" oft,
					// auf manchen Systemen ist nur "python3" vorhanden.
					String pythonCmd = System.getProperty("os.name").toLowerCase().contains("win")
					? "python"
					: "python3";

					executeCommand(
						pythonCmd + " \"" + activePy.getAbsolutePath() + "\"",
						false
					);
				} else {
					consolePanel.log(LanguageManager.t("no_python_file") + "\n", Color.RED);
				}
				break;
			}
		}

		private static void copyDirectory(Path source, Path target) throws IOException {
			Files.walk(source).forEach(path -> {
					try {
						Path destination = target.resolve(source.relativize(path));

						if (Files.isDirectory(path)) {
							Files.createDirectories(destination);
						} else {
							Files.copy(
								path,
								destination,
								StandardCopyOption.REPLACE_EXISTING
							);
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
		}


		private File calculateSourceRoot(File sourceFile, String fqcn) {
			int dotCount = 0;
			for (char c : fqcn.toCharArray()) if (c == '.') dotCount++;

			File root = sourceFile.getParentFile();
			for (int i = 0; i < dotCount; i++) {
				if (root != null && root.getParentFile() != null) {
					root = root.getParentFile();
				}
			}
			return root;
		}

		private void collectAllJavaFiles(File dir, List<File> list) {
			File[] files = dir.listFiles();
			if (files == null) return;
			for (File f : files) {
				if (f.isDirectory()) {
					String name = f.getName();
					// .git, out, libs und target komplett ignorieren
					if (name.equals(".git") || name.equals("out") || name.equals("libs") || name.equals("target")) continue;

					// HIER: Ignoriere den Ordner, wenn er "test" heißt oder im Test-Pfad liegt
					String absolutePath = f.getAbsolutePath().replace('\\', '/');
					if (name.equals("test") || absolutePath.contains("/src/test")) {
						continue;
					}

					collectAllJavaFiles(f, list);
				} else if (f.getName().endsWith(".java")) {
					list.add(f);
				}
			}
		}


		public void executeCommand(String command, boolean isTBuild) {
			consolePanel.log("> " + command + "\n", Color.GRAY);
			if (!isTBuild) SwingUtilities.invokeLater(errorMarker::clearCompilerErrors);

			// HIER: Zeige den Button sofort, wenn der Befehl abgeschickt wird
			if (btnTerminate != null) {
				SwingUtilities.invokeLater(() -> btnTerminate.setVisible(true));
			}

			new Thread(() -> {
					try {
						ProcessBuilder pb;
						if (System.getProperty("os.name").toLowerCase().contains("win")) {
							pb = new ProcessBuilder("cmd.exe", "/c", command);
						} else {
							pb = new ProcessBuilder("bash", "-c", command);
						}
						if (currentProjectFolder != null) pb.directory(currentProjectFolder);
						pb.redirectErrorStream(true);
						Process p = pb.start();
						runningProcess = p;
						if (TIDEPreferences.getAuSt()) {
							startResourceMonitor();
						}
						else {

						}
						BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
						String line;
						StringBuilder fullOutput = new StringBuilder();
						while ((line = r.readLine()) != null) {
							if (line.startsWith("Hinweis:") || line.startsWith("Note:")) {
								if (line.contains("veraltete API") || line.contains("deprecated API") ||
									line.contains("-Xlint:deprecation")) {
									continue;
								}
							}

							fullOutput.append(line).append("\n");
							consolePanel.log(line + "\n", isTBuild ? Color.CYAN : Color.WHITE);
						}

						int exitCode = p.waitFor();
						if (exitCode != 0) {
							consolePanel.log("[ERROR CODE " + exitCode + "]\n", Color.RED);
							if (!isTBuild) errorMarker.markCompilerErrors(fullOutput.toString());
						}
					} catch (Exception e) {
						consolePanel.log("[TERMINAL ERROR] " + e.getMessage() + "\n", Color.RED);
					} finally {
						runningProcess = null;
						if (btnTerminate != null) {
							SwingUtilities.invokeLater(() -> btnTerminate.setVisible(false));
						}
					}
				}).start();
		}

		public File findSourceFile(File dir, String fqcn) {
			String relativePath = fqcn.replace(".", File.separator) + ".java";
			File direct = new File(dir, relativePath);
			if (direct.exists()) return direct;

			// Suche in src/main/java (Standard)
			File srcMain = new File(dir, "src" + File.separator + "main" + File.separator + "java");
			if (srcMain.exists()) {
				File f = new File(srcMain, relativePath);
				if (f.exists()) return f;
			}

			if (!fqcn.contains(".")) {
				return searchFileRecursively(dir, fqcn + ".java");
			}
			return null;
		}

		private File searchFileRecursively(File dir, String fileName) {
			File[] files = dir.listFiles();
			if (files == null) return null;
			for (File f : files) {
				if (f.isDirectory()) {
					// HIER: "test" und "target" zu den ignorierten Ordnern hinzufügen
					if (f.getName().equals(".git") || f.getName().equals("out") || f.getName().equals("test") || f.getName().equals("target")) continue;
					File found = searchFileRecursively(f, fileName);
					if (found != null) return found;
				} else if (f.getName().equals(fileName)) return f;
			}
			return null;
		}

		public void handleTBuild() {
			if (currentProjectFolder == null) {
				return;
			}

			File tbuildJar = new File(currentProjectFolder, "TBuild.jar");

			if (tbuildJar.exists()) {
				executeCommand("java -jar TBuild.jar", true);
				return;
			}

			int antwort = JOptionPane.showConfirmDialog(
				null,
				"TBuild nicht gefunden, möchtest du die Datei ins Projekt laden?",
				"Bestätigung",
				JOptionPane.YES_NO_OPTION
			);

			if (antwort != JOptionPane.YES_OPTION) {
				return;
			}

			JDialog dialog = new JDialog((java.awt.Frame) null, "TBuild wird heruntergeladen...", true);

			JProgressBar progressBar = new JProgressBar(0, 100);
			progressBar.setStringPainted(true);

			JLabel statusLabel = new JLabel("Download wird gestartet...");

			JPanel panel = new JPanel(new BorderLayout(10, 10));
			panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
			panel.add(statusLabel, BorderLayout.NORTH);
			panel.add(progressBar, BorderLayout.CENTER);

			dialog.setContentPane(panel);
			dialog.setSize(400, 100);
			dialog.setLocationRelativeTo(null);
			dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

			SwingWorker<Void, Integer> worker = new SwingWorker<>() {

				@Override
				protected Void doInBackground() throws Exception {

					URL url = java.net.URI.create(
						"https://github.com/Thillager/Tbuild/releases/latest/download/TBuild.jar"
					).toURL();

					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setInstanceFollowRedirects(true);

					int totalBytes = connection.getContentLength();

					try (
						InputStream in = connection.getInputStream();
						FileOutputStream out = new FileOutputStream(tbuildJar)
					) {
						byte[] buffer = new byte[8192];
						long downloadedBytes = 0;

						int bytesRead;
						while ((bytesRead = in.read(buffer)) != -1) {

							out.write(buffer, 0, bytesRead);
							downloadedBytes += bytesRead;

							if (totalBytes > 0) {
								int progress = (int) ((downloadedBytes * 100L) / totalBytes);
								setProgress(progress);
							}
						}
					}

					return null;
				}

				@Override
				protected void done() {
					dialog.dispose();

					try {
						get(); // wirft Exception falls Download fehlgeschlagen

						executeCommand("java -jar TBuild.jar", true);

					} catch (Exception e) {
						JOptionPane.showMessageDialog(
							null,
							"Fehler beim Herunterladen von TBuild:\n" + e.getMessage(),
							"Fehler",
							JOptionPane.ERROR_MESSAGE
						);

						e.printStackTrace();
					}
				}
			};

			worker.addPropertyChangeListener(evt -> {
					if ("progress".equals(evt.getPropertyName())) {
						int progress = (Integer) evt.getNewValue();

						progressBar.setValue(progress);
						statusLabel.setText("Download: " + progress + "%");
					}
				});

			worker.execute();

			dialog.setVisible(true);
		}

		private Runnable onRefreshFileTree;
		public void setOnRefreshFileTree(Runnable r) { this.onRefreshFileTree = r; }
	}
