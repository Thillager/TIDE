package update;

import ui.ConsolePanel;
import config.TIDEProperties;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class UpdateManager {

    private final JFrame parent;
    private final ConsolePanel consolePanel;
    private final String appVersion;
    private final String githubRepo;

    public UpdateManager(JFrame parent, ConsolePanel consolePanel, String appVersion, String githubRepo) {
        this.parent       = parent;
        this.consolePanel = consolePanel;
        this.appVersion   = appVersion;
        this.githubRepo   = githubRepo;
    }

    // Prüft auf GitHub ob eine neuere Version verfügbar ist
    public void checkForUpdates() {
        consolePanel.log("[INFO] Suche nach Updates...\n", Color.CYAN);
        new Thread(() -> {
            try {
                String apiUrl = "https://api.github.com/repos/" + githubRepo + "/releases/latest";
                HttpURLConnection conn = (HttpURLConnection) java.net.URI.create(apiUrl).toURL().openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "ThIDE-App");
                conn.setConnectTimeout(TIDEProperties.CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(TIDEProperties.READ_TIMEOUT_MS);

                if (conn.getResponseCode() != 200) {
                    consolePanel.log("[FEHLER] Konnte GitHub nicht erreichen (HTTP " + conn.getResponseCode() + ").\n", Color.RED);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                String json = sb.toString();

                String latestVersion = extractJsonValue(json, "tag_name");
                if (latestVersion == null) {
                    consolePanel.log("[FEHLER] Konnte Version nicht aus GitHub-Antwort lesen.\n", Color.RED);
                    return;
                }
                latestVersion = latestVersion.replaceAll("^v", "");

                String finalLatestVersion = latestVersion;
                SwingUtilities.invokeLater(() -> {
                    if (isNewerVersion(finalLatestVersion, appVersion)) {
                        int result = JOptionPane.showConfirmDialog(
                            parent,
                            "Neue Version verfuegbar: " + finalLatestVersion + "\n" +
                            "Aktuelle Version: " + appVersion + "\n\n" +
                            "Jetzt updaten?",
                            "Update verfuegbar",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);
                        if (result == JOptionPane.YES_OPTION) {
                            downloadAndInstallUpdate(finalLatestVersion, json);
                        }
                    } else {
                        JOptionPane.showMessageDialog(
                            parent,
                            "Du hast bereits die aktuellste Version (" + appVersion + ").",
                            "Kein Update noetig",
                            JOptionPane.INFORMATION_MESSAGE);
                        consolePanel.log("[INFO] Keine Updates verfuegbar. Version " + appVersion + " ist aktuell.\n", Color.GREEN);
                    }
                });

            } catch (Exception e) {
                consolePanel.log("[FEHLER] Update-Pruefung fehlgeschlagen: " + e.getMessage() + "\n", Color.RED);
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(parent,
                        "Update-Pruefung fehlgeschlagen:\n" + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    public boolean isRunningAsJar() {
        if ("true".equalsIgnoreCase(System.getProperty("ThIDE_PACKAGED"))) {
            return false;
        }

        try {
            java.net.URI uri = UpdateManager.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI();
            String path = uri.getPath();

            if (!path.endsWith(".jar")) return false;

            File jarFile   = new File(uri);
            File parentDir = jarFile.getParentFile();
            
            if (parentDir != null && parentDir.getName().equalsIgnoreCase("app")) {
                File launcherDir = parentDir.getParentFile();
                if (launcherDir != null) {
                    File[] launchers = launcherDir.listFiles(f ->
                        f.isFile() && (
                            f.getName().endsWith(".exe") ||
                            (!f.getName().contains(".") && f.canExecute())
                        )
                    );
                    if (launchers != null && launchers.length > 0) {
                        return false;
                    }
                }
                
                File libDir = parentDir.getParentFile();
                if (libDir != null && libDir.getName().equalsIgnoreCase("lib")) {
                    File installDir = libDir.getParentFile();
                    if (installDir != null) {
                        File binDir = new File(installDir, "bin");
                        if (binDir.exists() && binDir.isDirectory()) {
                            File[] binFiles = binDir.listFiles(f -> f.isFile() && f.canExecute());
                            if (binFiles != null && binFiles.length > 0) {
                                return false;
                            }
                        }
                    }
                }
            }

            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("win") && !os.contains("mac")) {
                if (path.startsWith("/opt/") || path.startsWith("/usr/")) {
                    return false; 
                }
            }

            return true;

        } catch (Exception ignored) {}
        return false;
    }

    public File getRunningJarFile() {
        try {
            java.net.URI uri = UpdateManager.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI();
            File f = new File(uri);
            if (f.getName().endsWith(".jar")) return f;
        } catch (Exception ignored) {}
        return null;
    }

    private void downloadAndInstallUpdate(String version, String releaseJson) {
        String os         = System.getProperty("os.name", "").toLowerCase();
        boolean isWindows = os.contains("win");
        boolean isLinux   = !isWindows && !os.contains("mac");
        boolean runAsJar  = isRunningAsJar();

        String downloadUrl;
        File destinationFile;

        if (runAsJar) {
            String jarAssetName = findAssetName(releaseJson, ".jar");
            if (jarAssetName == null) {
                consolePanel.log("[FEHLER] Kein .jar-Asset im Release gefunden.\n", Color.RED);
                return;
            }
            File currentJar = getRunningJarFile();
            if (currentJar == null) {
                consolePanel.log("[FEHLER] Konnte den Pfad der laufenden JAR nicht bestimmen.\n", Color.RED);
                return;
            }
            downloadUrl = "https://github.com/" + githubRepo + "/releases/download/v" + version + "/" + jarAssetName;
            destinationFile = new File(System.getProperty("java.io.tmpdir"), jarAssetName);
        } else {
            String installerName;
            if (isWindows) {
                installerName = findAssetName(releaseJson, ".msi");
            } else if (isLinux) {
                installerName = findAssetName(releaseJson, ".deb");
            } else {
                JOptionPane.showMessageDialog(parent,
                    "Auto-Update wird auf macOS noch nicht unterstuetzt.\n" +
                    "Bitte manuell von github.com/" + githubRepo + " herunterladen.",
                    "Nicht unterstuetzt", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (installerName == null) {
                consolePanel.log("[FEHLER] Kein passender Installer fuer dieses OS gefunden.\n", Color.RED);
                return;
            }

            downloadUrl = "https://github.com/" + githubRepo + "/releases/download/v" + version + "/" + installerName;
            destinationFile = new File(System.getProperty("java.io.tmpdir"), installerName);
        }

        // --- Dialog mit JProgressBar erstellen ---
        JDialog dialog = new JDialog(parent, "Update wird heruntergeladen...", true);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JLabel statusLabel = new JLabel("Download wird gestartet...");

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        dialog.setContentPane(panel);
        dialog.setSize(400, 120);
        dialog.setLocationRelativeTo(parent);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        consolePanel.log("[INFO] Lade Update herunter: " + destinationFile.getName() + "...\n", Color.CYAN);

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                HttpURLConnection conn = (HttpURLConnection) java.net.URI.create(downloadUrl).toURL().openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(TIDEProperties.CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(TIDEProperties.DOWNLOAD_TIMEOUT_MS);

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP-Fehlercode: " + conn.getResponseCode());
                }

                int totalBytes = conn.getContentLength();

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(destinationFile)) {

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
                    get(); // Wirft Exception, falls doInBackground() schiefging
                    consolePanel.log("[ERFOLG] Update heruntergeladen: " + destinationFile.getAbsolutePath() + "\n", Color.GREEN);

                    if (runAsJar) {
                        launchJarSelfReplace(destinationFile, getRunningJarFile());
                    } else if (isWindows) {
                        launchWindowsUpdate(destinationFile);
                    } else {
                        launchLinuxUpdate(destinationFile);
                    }

                } catch (Exception e) {
                    consolePanel.log("[FEHLER] Update fehlgeschlagen: " + e.getMessage() + "\n", Color.RED);
                    JOptionPane.showMessageDialog(parent,
                        "Update fehlgeschlagen:\n" + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
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

    private void launchJarSelfReplace(File newJar, File currentJar) throws IOException {
        String os         = System.getProperty("os.name", "").toLowerCase();
        boolean isWindows = os.contains("win");
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator
        + (isWindows ? "java.exe" : "java");

        if (isWindows) {
            File bat = new File(System.getProperty("java.io.tmpdir"), "thide_jar_update.bat");
            try (PrintWriter pw = new PrintWriter(bat)) {
                pw.println("@echo off");
                pw.println("echo Warte auf ThIDE-Beendigung...");
                pw.println("timeout /t 3 /nobreak > nul");
                pw.println("echo Ersetze JAR...");
                pw.println("copy /Y \"" + newJar.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"");
                pw.println("if %errorlevel% neq 0 (");
                pw.println("  echo JAR-Austausch fehlgeschlagen!");
                pw.println("  pause");
                pw.println("  exit /b 1");
                pw.println(")");
                pw.println("echo Starte neue Version...");
                pw.println("start \"\" \"" + java + "\" -jar \"" + currentJar.getAbsolutePath() + "\"");
                pw.println("del \"%~f0\"");
            }
            consolePanel.log("[INFO] Starte JAR-Update (Windows)...\n", Color.CYAN);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(parent,
                    "Die JAR wird jetzt aktualisiert.\nThIDE startet automatisch neu.",
                    "JAR-Update", JOptionPane.INFORMATION_MESSAGE);
                try {
                    new ProcessBuilder("cmd.exe", "/c", bat.getAbsolutePath()).start();
                } catch (IOException e) {
                    consolePanel.log("[FEHLER] Konnte JAR-Update-Skript nicht starten: " + e.getMessage() + "\n", Color.RED);
                    return;
                }
                System.exit(0);
            });
        } else {
            File sh = new File(System.getProperty("java.io.tmpdir"), "thide_jar_update.sh");
            try (PrintWriter pw = new PrintWriter(sh)) {
                pw.println("#!/bin/bash");
                pw.println("sleep 2");
                pw.println("cp -f \"" + newJar.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"");
                pw.println("chmod +x \"" + currentJar.getAbsolutePath() + "\"");
                pw.println("\"" + java + "\" -jar \"" + currentJar.getAbsolutePath() + "\" &");
                pw.println("rm -- \"$0\"");
            }
            sh.setExecutable(true);
            consolePanel.log("[INFO] Starte JAR-Update (Linux)...\n", Color.CYAN);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(parent,
                    "Die JAR wird jetzt aktualisiert.\nThIDE startet automatisch neu.",
                    "JAR-Update", JOptionPane.INFORMATION_MESSAGE);
                try {
                    new ProcessBuilder("bash", sh.getAbsolutePath()).start();
                } catch (IOException e) {
                    consolePanel.log("[FEHLER] Konnte JAR-Update-Skript nicht starten: " + e.getMessage() + "\n", Color.RED);
                    return;
                }
                System.exit(0);
            });
        }
    }

    private void launchWindowsUpdate(File msiFile) throws IOException {
        File batFile = new File(System.getProperty("java.io.tmpdir"), "tide_update.bat");
        try (PrintWriter pw = new PrintWriter(batFile)) {
            pw.println("@echo off");
            pw.println("echo Warte auf ThIDE-Beendigung...");
            pw.println("timeout /t 5 /nobreak > nul");
            pw.println("echo Installiere Update...");
            pw.println("msiexec /i \"" + msiFile.getAbsolutePath() + "\" /qn /norestart");
            pw.println("if %errorlevel% neq 0 (");
            pw.println("  echo Installation fehlgeschlagen!");
            pw.println("  pause");
            pw.println("  exit /b 1");
            pw.println(")");
            pw.println("echo Update erfolgreich installiert.");
            pw.println("del \"%~f0\"");
        }
        consolePanel.log("[INFO] Starte Windows-Update-Skript...\n", Color.CYAN);
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(parent,
                "Das Update wird jetzt installiert.\n" +
                "ThIDE wird sich gleich beenden.",
                "Update wird installiert",
                JOptionPane.INFORMATION_MESSAGE);
            try {
                new ProcessBuilder("cmd.exe", "/c", batFile.getAbsolutePath()).start();
            } catch (IOException e) {
                consolePanel.log("[FEHLER] Konnte Update-Skript nicht starten: " + e.getMessage() + "\n", Color.RED);
                return;
            }
            System.exit(0);
        });
    }

    private void launchLinuxUpdate(File debFile) throws IOException {
        boolean hasSudoNopass = false;
        try {
            Process p = new ProcessBuilder("sudo", "-n", "true")
            .redirectErrorStream(true).start();
            hasSudoNopass = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            && p.exitValue() == 0;
        } catch (Exception ignored) {}

        File shFile = new File(System.getProperty("java.io.tmpdir"), "tide_update.sh");
        try (PrintWriter pw = new PrintWriter(shFile)) {
            pw.println("#!/bin/bash");
            pw.println("sleep 2");
            if (hasSudoNopass) {
                pw.println("sudo dpkg -i \"" + debFile.getAbsolutePath() + "\"");
            } else {
                pw.println("if command -v pkexec &>/dev/null; then");
                pw.println("    pkexec dpkg -i \"" + debFile.getAbsolutePath() + "\"");
                pw.println("else");
                pw.println("    x-terminal-emulator -e bash -c \"sudo dpkg -i '" + debFile.getAbsolutePath() + "' && echo Fertig; read\"");
                pw.println("fi");
            }
            pw.println("which tide && tide &");
            pw.println("rm -- \"$0\"");
        }
        shFile.setExecutable(true);

        String sudoHint = hasSudoNopass
        ? "Kein Passwort noetig (sudo-Rechte erkannt)."
        : "Du wirst nach deinem Passwort gefragt.";

        consolePanel.log("[INFO] Starte Linux-Update-Skript"
            + (hasSudoNopass ? " (sudo, kein Passwort noetig)" : " (pkexec)") + "...\n", Color.CYAN);

        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(parent,
                "Das Update wird jetzt installiert.\n" +
                sudoHint + "\n" +
                "ThIDE wird sich gleich beenden.",
                "Update wird installiert",
                JOptionPane.INFORMATION_MESSAGE);
            try {
                new ProcessBuilder("bash", shFile.getAbsolutePath()).start();
            } catch (IOException e) {
                consolePanel.log("[FEHLER] Konnte Update-Skript nicht starten.\n", Color.RED);
                return;
            }
            System.exit(0);
        });
    }

    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private String findAssetName(String releaseJson, String extension) {
        int searchFrom = 0;
        while (true) {
            int idx = releaseJson.indexOf("\"name\":\"", searchFrom);
            if (idx == -1) return null;
            int start = idx + 8;
            int end   = releaseJson.indexOf("\"", start);
            if (end == -1) return null;
            String name = releaseJson.substring(start, end);
            if (name.endsWith(extension)) return name;
            searchFrom = end;
        }
    }

    private boolean isNewerVersion(String newVersion, String currentVersion) {
        try {
            String[] newParts     = newVersion.split("\\.");
            String[] currentParts = currentVersion.split("\\.");
            int length = Math.max(newParts.length, currentParts.length);
            for (int i = 0; i < length; i++) {
                int newPart     = i < newParts.length     ? Integer.parseInt(newParts[i].trim())     : 0;
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i].trim()) : 0;
                if (newPart > currentPart) return true;
                if (newPart < currentPart) return false;
            }
            return false;
        } catch (NumberFormatException e) {
            return !newVersion.equals(currentVersion);
        }
    }
}