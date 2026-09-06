package lsp;

import ui.ConsolePanel;

import java.awt.Color;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Startet einen Language-Server als externen Prozess und spricht mit ihm
 * über das Language Server Protocol (JSON-RPC 2.0 über stdio, Nachrichten
 * mit "Content-Length"-Header, wie im LSP-Standard vorgeschrieben).
 *
 * Ein LspClient existiert ausschließlich, während der Nutzer den
 * LSP-Vervollständigungsmodus aktiviert hat (siehe LspManager) – wird der
 * Modus deaktiviert, wird die Instanz beendet und es läuft kein
 * Server-Prozess mehr im Hintergrund.
 */
public class LspClient {

    private final String languageId;
    private final List<String> commandParts;
    private final java.io.File rootDir;
    private final ConsolePanel consolePanel;

    private Process process;
    private BufferedOutputStream out;
    private Thread readerThread;
    private Thread stderrThread;
    private volatile boolean alive = false;

    private final AtomicInteger idGen = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

    /** Bevorzugter Konstruktor: Kommando als Liste, damit z. B. automatisch installierte Server unter Pfaden mit Leerzeichen zuverlässig gestartet werden (kein String-Splitting nötig). */
    public LspClient(String languageId, List<String> commandParts, java.io.File rootDir, ConsolePanel consolePanel) {
        this.languageId   = languageId;
        this.commandParts = commandParts;
        this.rootDir      = rootDir;
        this.consolePanel = consolePanel;
    }

    /** Bequemlichkeits-Konstruktor für ein einfaches, mit Leerzeichen getrenntes Kommando (z. B. für manuelle/zukünftige Overrides). */
    public LspClient(String languageId, String commandLine, java.io.File rootDir, ConsolePanel consolePanel) {
        this(languageId, splitCommand(commandLine), rootDir, consolePanel);
    }

    public String getLanguageId() { return languageId; }

    public boolean isAlive() { return alive && process != null && process.isAlive(); }

    // ── Lebenszyklus ─────────────────────────────────────────────────────

    public void start() throws IOException {
        List<String> parts = commandParts;
        if (parts == null || parts.isEmpty()) throw new IOException("Kein Start-Kommando für Language-Server '" + languageId + "' konfiguriert.");

        ProcessBuilder pb = new ProcessBuilder(parts);
        if (rootDir != null && rootDir.isDirectory()) pb.directory(rootDir);
        process = pb.start();
        out = new BufferedOutputStream(process.getOutputStream());
        alive = true;

        readerThread = new Thread(this::readLoop, "LSP-" + languageId + "-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        stderrThread = new Thread(this::drainStderr, "LSP-" + languageId + "-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();

        initialize();
    }

    public void shutdown() {
        if (process == null) return;
        try {
            if (isAlive()) {
                sendRequest("shutdown", null).get(2, TimeUnit.SECONDS);
                sendNotification("exit", null);
            }
        } catch (Exception ignored) {
            // Server antwortet nicht mehr -> einfach hart beenden
        } finally {
            alive = false;
            try {
                if (process.isAlive() && !process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            } catch (Exception ex) {
                process.destroyForcibly();
            }
            for (CompletableFuture<Object> f : pending.values()) f.cancel(true);
            pending.clear();
            log("Language-Server beendet.", Color.LIGHT_GRAY);
        }
    }

    private static List<String> splitCommand(String cmd) {
        List<String> result = new ArrayList<>();
        if (cmd == null) return result;
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (cur.length() > 0) { result.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) result.add(cur.toString());
        return result;
    }

    private void initialize() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("processId", (double) ProcessHandle.current().pid());
        params.put("capabilities", new LinkedHashMap<>());
        params.put("rootUri", rootDir != null ? rootDir.toURI().toString() : null);
        try {
            // JDT LS ist eine vollständige OSGi-Anwendung und kann beim
            // (Kalt-)Start realistisch 10-30+ Sekunden brauchen - 6s war zu knapp.
            sendRequest("initialize", params).get(45, TimeUnit.SECONDS);
            sendNotification("initialized", new LinkedHashMap<>());
        } catch (TimeoutException ex) {
            log("Initialisierung fehlgeschlagen: Der Language-Server hat innerhalb von 45s nicht geantwortet (Kaltstart kann bei JDT LS länger dauern).", Color.RED);
        } catch (Exception ex) {
            String msg = ex.getMessage();
            log("Initialisierung fehlgeschlagen: " + (msg != null ? msg : ex.getClass().getSimpleName()), Color.RED);
        }
    }

    // ── JSON-RPC ─────────────────────────────────────────────────────────

    public CompletableFuture<Object> sendRequest(String method, Object params) {
        int id = idGen.getAndIncrement();
        CompletableFuture<Object> future = new CompletableFuture<>();
        pending.put(id, future);

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("jsonrpc", "2.0");
        msg.put("id", (double) id);
        msg.put("method", method);
        msg.put("params", params);
        write(msg);
        return future;
    }

    public void sendNotification(String method, Object params) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("jsonrpc", "2.0");
        msg.put("method", method);
        msg.put("params", params);
        write(msg);
    }

    private void write(Map<String, Object> msg) {
        if (out == null) return;
        try {
            String json = Json.write(msg);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            String header = "Content-Length: " + body.length + "\r\n\r\n";
            synchronized (writeLock) {
                out.write(header.getBytes(StandardCharsets.US_ASCII));
                out.write(body);
                out.flush();
            }
        } catch (IOException ex) {
            alive = false;
        }
    }

    private void readLoop() {
        try {
            InputStream in = process.getInputStream();
            while (true) {
                int contentLength = -1;
                while (true) {
                    String line = readHeaderLine(in);
                    if (line == null) return; // EOF: Prozess beendet
                    if (line.isEmpty()) break; // Leerzeile = Ende der Header
                    int idx = line.indexOf(':');
                    if (idx > 0 && line.substring(0, idx).trim().equalsIgnoreCase("Content-Length")) {
                        contentLength = Integer.parseInt(line.substring(idx + 1).trim());
                    }
                }
                if (contentLength < 0) continue;
                byte[] body = readFully(in, contentLength);
                if (body == null) return;
                handleMessage(new String(body, StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            // Prozess vermutlich beendet worden
        } finally {
            alive = false;
        }
    }

    private String readHeaderLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1, b;
        while ((b = in.read()) != -1) {
            if (prev == '\r' && b == '\n') {
                byte[] bytes = buf.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            buf.write(b);
            prev = b;
        }
        return buf.size() == 0 ? null : buf.toString(StandardCharsets.US_ASCII);
    }

    private byte[] readFully(InputStream in, int len) throws IOException {
        byte[] data = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(data, off, len - off);
            if (r < 0) return null;
            off += r;
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String json) {
        Object parsed;
        try {
            parsed = Json.parse(json);
        } catch (Exception ex) {
            return;
        }
        if (!(parsed instanceof Map)) return;
        Map<String, Object> msg = (Map<String, Object>) parsed;

        Object idObj = msg.get("id");
        boolean isResponse = idObj != null && (msg.containsKey("result") || msg.containsKey("error"));
        if (isResponse) {
            int id = (int) Math.round((Double) idObj);
            CompletableFuture<Object> future = pending.remove(id);
            if (future != null) {
                if (msg.containsKey("error")) {
                    future.completeExceptionally(new RuntimeException(String.valueOf(msg.get("error"))));
                } else {
                    future.complete(msg.get("result"));
                }
            }
            return;
        }

        // Eingehende Requests vom Server (mit id) höflich mit "Method not
        // found" beantworten; Notifications (z. B. Diagnostics) werden
        // aktuell ignoriert, da TIDE sie derzeit nicht darstellt.
        if (idObj != null) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("jsonrpc", "2.0");
            resp.put("id", idObj);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", -32601.0);
            error.put("message", "Method not found");
            resp.put("error", error);
            write(resp);
        }
    }

    // ── Textdokument-Synchronisation ─────────────────────────────────────

    public void didOpen(String uri, String languageId, int version, String text) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("uri", uri);
        doc.put("languageId", languageId);
        doc.put("version", (double) version);
        doc.put("text", text);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", doc);
        sendNotification("textDocument/didOpen", params);
    }

    public void didChange(String uri, int version, String text) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("uri", uri);
        doc.put("version", (double) version);
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("text", text); // volle Dokumentsynchronisation (kein Incremental Sync)
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", doc);
        params.put("contentChanges", List.of(change));
        sendNotification("textDocument/didChange", params);
    }

    public void didClose(String uri) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("uri", uri);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", doc);
        sendNotification("textDocument/didClose", params);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> requestCompletions(String uri, int line, int character, long timeoutMs) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("uri", uri);
        Map<String, Object> pos = new LinkedHashMap<>();
        pos.put("line", (double) line);
        pos.put("character", (double) character);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", doc);
        params.put("position", pos);
        try {
            Object result = sendRequest("textDocument/completion", params).get(timeoutMs, TimeUnit.MILLISECONDS);
            return extractItems(result);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Object result) {
        if (result instanceof List) {
            return (List<Map<String, Object>>) (List<?>) result;
        }
        if (result instanceof Map) {
            Object items = ((Map<String, Object>) result).get("items");
            if (items instanceof List) return (List<Map<String, Object>>) (List<?>) items;
        }
        return Collections.emptyList();
    }

    private void drainStderr() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {
                // Server-Debugausgaben werden bewusst nicht in die
                // TIDE-Konsole gespiegelt, um sie nicht mit LSP-internem
                // Rauschen zuzumüllen.
            }
        } catch (IOException ignored) {
        }
    }

    private void log(String msg, Color color) {
        if (consolePanel != null) consolePanel.log("[LSP:" + languageId + "] " + msg + "\n", color);
    }
}