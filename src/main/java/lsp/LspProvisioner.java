package lsp;

import ui.ConsolePanel;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Beschafft und installiert Language-Server bei Bedarf vollautomatisch im
 * Hintergrund – der Nutzer muss dafür nichts eintragen, herunterladen oder
 * manuell installieren:
 *
 *  - Python: legt einmalig eine eigene, isolierte virtuelle Umgebung unter
 *            ~/.tide/lsp/python-venv an (python3 -m venv …) und installiert
 *            darin "python-lsp-server" via pip. Eine eigene venv wird
 *            bewusst verwendet, weil viele aktuelle Linux-Distributionen
 *            (PEP 668, "externally-managed-environment") ein einfaches
 *            "pip install --user" verweigern; in einer venv gibt es diese
 *            Einschränkung nicht.
 *
 *  - Java:   lädt den offiziellen Eclipse JDT Language Server
 *            (jdt-language-server-latest.tar.gz, die von den jdtls-Machern
 *            selbst dokumentierte stabile Download-Adresse) einmalig nach
 *            ~/.tide/lsp/jdtls herunter und entpackt ihn dort mit
 *            TarExtractor.
 *
 * Beide Installationen werden unter ~/.tide/lsp gecacht: Bei jedem
 * weiteren Aufruf wird zuerst geprüft, ob bereits eine funktionierende
 * Installation existiert – es wird nur dann etwas heruntergeladen bzw.
 * installiert, wenn das nicht der Fall ist.
 *
 * Diese Klasse läuft ausschließlich in einem Hintergrund-Thread (siehe
 * LspManager.requestAsync) – Downloads/Installationen können je nach
 * Internetverbindung mehrere Sekunden bis wenige Minuten dauern und
 * dürfen daher nicht den EDT blockieren.
 */
public final class LspProvisioner {

    private static final String JDTLS_DOWNLOAD_URL =
        "https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz";

    private LspProvisioner() {}

    private static File cacheRoot() {
        return new File(System.getProperty("user.home"), ".tide" + File.separator + "lsp");
    }

    // ── Python ───────────────────────────────────────────────────────────

    public static List<String> ensurePython(ConsolePanel consolePanel) {
        File venvDir = new File(cacheRoot(), "python-venv");
        File venvPython = venvPythonExecutable(venvDir);

        if (venvPython.isFile() && isPylspAvailable(venvPython)) {
            return List.of(venvPython.getAbsolutePath(), "-m", "pylsp");
        }

        String baseInterpreter = findExecutable("python3", "python", "py");
        if (baseInterpreter == null) {
            log(consolePanel, "python", "Keine Python-Installation gefunden (python3/python/py im PATH) - intelligente Python-Vervollständigung nicht verfügbar.");
            return null;
        }

        if (!venvPython.isFile()) {
            log(consolePanel, "python", "Richte einmalig eine eigene, isolierte Python-Umgebung unter " + venvDir + " ein …");
            if (!runProcess(consolePanel, "python", List.of(baseInterpreter, "-m", "venv", venvDir.getAbsolutePath()))) {
                log(consolePanel, "python", "Anlegen der virtuellen Umgebung fehlgeschlagen.");
                return null;
            }
        }
        if (!venvPython.isFile()) {
            log(consolePanel, "python", "Virtuelle Umgebung konnte nicht erstellt werden (python-Interpreter fehlt in " + venvDir + ").");
            return null;
        }

        log(consolePanel, "python", "Installiere python-lsp-server (einmalig, danach nicht mehr nötig) …");
        boolean ok = runProcess(consolePanel, "python",
            List.of(venvPython.getAbsolutePath(), "-m", "pip", "install", "--quiet", "python-lsp-server"));
        if (!ok || !isPylspAvailable(venvPython)) {
            log(consolePanel, "python", "Installation von python-lsp-server fehlgeschlagen (kein Internetzugang?) - falle auf Wortvervollständigung zurück.");
            return null;
        }

        log(consolePanel, "python", "python-lsp-server erfolgreich installiert.");
        return List.of(venvPython.getAbsolutePath(), "-m", "pylsp");
    }

    private static File venvPythonExecutable(File venvDir) {
        boolean windows = isWindows();
        return windows ? new File(venvDir, "Scripts" + File.separator + "python.exe")
                       : new File(venvDir, "bin" + File.separator + "python");
    }

    private static boolean isPylspAvailable(File pythonExe) {
        return runProcessSilent(List.of(pythonExe.getAbsolutePath(), "-c", "import pylsp"));
    }

    // ── Java ─────────────────────────────────────────────────────────────

    public static List<String> ensureJava(ConsolePanel consolePanel) {
        File installDir = new File(cacheRoot(), "jdtls");
        File launcher = findJdtlsLauncher(installDir);

        if (launcher == null) {
            if (!downloadAndExtractJdtls(installDir, consolePanel)) {
                return null;
            }
            launcher = findJdtlsLauncher(installDir);
        }
        if (launcher == null) {
            log(consolePanel, "java", "Java-Language-Server konnte nicht gefunden werden (Entpacken fehlgeschlagen oder unerwartetes Archivlayout).");
            return null;
        }
        if (!launcher.canExecute()) launcher.setExecutable(true, false);

        File dataDir = new File(cacheRoot(), "jdtls-workspace");
        dataDir.mkdirs();

        List<String> command = new ArrayList<>();
        command.add(launcher.getAbsolutePath());
        command.add("-data");
        command.add(dataDir.getAbsolutePath());
        return command;
    }

    private static File findJdtlsLauncher(File installDir) {
        File script = new File(installDir, "bin" + File.separator + (isWindows() ? "jdtls.bat" : "jdtls"));
        return script.isFile() ? script : null;
    }

    private static boolean downloadAndExtractJdtls(File installDir, ConsolePanel consolePanel) {
        try {
            installDir.mkdirs();
            log(consolePanel, "java", "Lade Java-Language-Server (Eclipse JDT LS) herunter – das passiert nur beim allerersten Mal und kann je nach Verbindung einige Minuten dauern …");

            HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(JDTLS_DOWNLOAD_URL))
                .timeout(Duration.ofMinutes(15))
                .GET()
                .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                log(consolePanel, "java", "Download fehlgeschlagen (HTTP " + response.statusCode() + ").");
                return false;
            }
            try (InputStream in = response.body()) {
                TarExtractor.extractTarGz(in, installDir);
            }
            log(consolePanel, "java", "Java-Language-Server erfolgreich installiert unter " + installDir);
            return true;
        } catch (Exception ex) {
            log(consolePanel, "java", "Download/Installation fehlgeschlagen: " + ex.getMessage() + " - falle auf Wortvervollständigung zurück.");
            return false;
        }
    }

    // ── Gemeinsame Hilfsfunktionen ───────────────────────────────────────

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String findExecutable(String... candidates) {
        for (String candidate : candidates) {
            if (runProcessSilent(List.of(candidate, "--version"))) return candidate;
        }
        return null;
    }

    private static boolean runProcessSilent(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean runProcess(ConsolePanel consolePanel, String languageId, List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log(consolePanel, languageId, line);
                }
            }
            return p.waitFor(10, TimeUnit.MINUTES) && p.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void log(ConsolePanel consolePanel, String languageId, String msg) {
        if (consolePanel != null) consolePanel.log("[LSP:" + languageId + "] " + msg + "\n", Color.CYAN);
    }
}
