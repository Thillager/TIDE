package lsp;

import ui.ConsolePanel;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Zentrale Stelle, die dafür sorgt, dass Language-Server-Prozesse
 * ausschließlich existieren, wenn der Nutzer den LSP-Modus in den
 * Einstellungen aktiviert hat:
 *
 *  - Ist der Modus AUS, ruft niemand requestAsync() auf -> kein
 *    Server-Prozess läuft, kein zusätzlicher Ressourcenverbrauch.
 *  - Ist der Modus AN und eine passende Datei wird geöffnet, wird die
 *    Beschaffung/Installation (siehe LspProvisioner - vollautomatisch,
 *    ohne jede Eingabe) und der Start des Servers in einem
 *    Hintergrund-Thread angestoßen, damit die Oberfläche währenddessen
 *    nicht einfriert. Der Editor-Tab arbeitet bis dahin ganz normal mit
 *    der wortbasierten Vervollständigung weiter und wird automatisch auf
 *    LSP hochgestuft, sobald der Server bereitsteht (siehe
 *    EditorManager.upgradeTabsForLanguage).
 *  - Pro Sprache läuft höchstens ein Server-Prozess, auch wenn mehrere
 *    Dateien derselben Sprache gleichzeitig geöffnet werden
 *    (computeIfAbsent auf der Provisioning-Future).
 *  - Wird der Modus wieder ausgeschaltet oder TIDE beendet, werden alle
 *    laufenden bzw. noch in Arbeit befindlichen Server sofort über
 *    shutdownAll() beendet bzw. abgebrochen.
 */
public final class LspManager {

    private static final LspManager INSTANCE = new LspManager();
    public static LspManager getInstance() { return INSTANCE; }

    private final Map<String, LspClient> clients = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<LspClient>> provisioning = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "LSP-Provisioner");
        t.setDaemon(true);
        return t;
    });

    private LspManager() {}

    /** Liefert einen bereits laufenden Client für die Sprache, oder null, wenn (noch) keiner läuft. Blockiert nie. */
    public LspClient getIfRunning(String languageId) {
        LspClient existing = clients.get(languageId);
        return (existing != null && existing.isAlive()) ? existing : null;
    }

    /**
     * Stößt – falls nötig – die automatische Beschaffung/Installation und
     * den Start des Language-Servers für die angegebene Sprache im
     * Hintergrund an und ruft onReady auf dem EDT auf, sobald ein
     * lauffähiger Client bereitsteht. Läuft bereits ein Client, wird
     * onReady sofort (asynchron) aufgerufen. Schlägt die Beschaffung fehl
     * (kein Internet, nicht unterstütztes System, …), wird onReady NICHT
     * aufgerufen – der Aufrufer bleibt in diesem Fall beim bisherigen
     * Vervollständigungsmodus.
     */
    public void requestAsync(String languageId, File rootDir, ConsolePanel consolePanel, Consumer<LspClient> onReady) {
        LspClient existing = clients.get(languageId);
        if (existing != null && existing.isAlive()) {
            SwingUtilities.invokeLater(() -> onReady.accept(existing));
            return;
        }

        CompletableFuture<LspClient> future = provisioning.computeIfAbsent(languageId,
            lang -> CompletableFuture.supplyAsync(() -> provisionAndStart(lang, rootDir, consolePanel), executor));

        future.whenComplete((client, error) -> {
            provisioning.remove(languageId);
            if (client != null && client.isAlive()) {
                clients.put(languageId, client);
                SwingUtilities.invokeLater(() -> onReady.accept(client));
            }
        });
    }

    private LspClient provisionAndStart(String languageId, File rootDir, ConsolePanel consolePanel) {
        List<String> command = switch (languageId) {
            case "java"   -> LspProvisioner.ensureJava(consolePanel);
            case "python" -> LspProvisioner.ensurePython(consolePanel);
            default       -> null;
        };
        if (command == null) return null;

        try {
            LspClient client = new LspClient(languageId, command, rootDir, consolePanel);
            client.start();
            if (consolePanel != null) {
                consolePanel.log("[LSP] Language-Server für '" + languageId + "' bereit.\n", Color.CYAN);
            }
            return client;
        } catch (Exception ex) {
            if (consolePanel != null) {
                consolePanel.log("[LSP] Start des Language-Servers für '" + languageId
                    + "' fehlgeschlagen: " + ex.getMessage() + "\n", Color.RED);
            }
            return null;
        }
    }

    /** Beendet sofort alle laufenden Language-Server-Prozesse und bricht laufende Beschaffungen ab. */
    public synchronized void shutdownAll() {
        for (LspClient client : clients.values()) {
            client.shutdown();
        }
        clients.clear();
        for (CompletableFuture<LspClient> f : provisioning.values()) {
            f.cancel(true);
        }
        provisioning.clear();
    }

    public boolean hasActiveClients() {
        return !clients.isEmpty();
    }
}
