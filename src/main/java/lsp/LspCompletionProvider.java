package lsp;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CompletionProvider, der Vorschläge live von einem laufenden Language
 * Server bezieht (Language Server Protocol), statt sie – wie
 * DefaultCompletionProvider – aus bereits im Dokument gesehenen Wörtern zu
 * erraten.
 *
 * Eine Instanz dieser Klasse existiert nur für Editor-Tabs, für die der
 * LSP-Modus aktiv UND ein Server für die jeweilige Sprache erfolgreich
 * gestartet wurde (siehe EditorManager / LspManager). Sie hält die
 * Verbindung zum Server synchron (didOpen/didChange/didClose) und fragt
 * bei jeder Vervollständigungsanfrage aktuelle Vorschläge ab.
 */
public class LspCompletionProvider extends DefaultCompletionProvider {

    private static final long COMPLETION_TIMEOUT_MS = 800;
    private static final int  CHANGE_DEBOUNCE_MS    = 300;

    private final LspClient client;
    private final RSyntaxTextArea textArea;
    private final String uri;
    private final AtomicInteger version = new AtomicInteger(1);
    private final DocumentListener syncListener;
    private final Timer debounceTimer;
    private volatile boolean closed = false;

    public LspCompletionProvider(LspClient client, RSyntaxTextArea textArea, File file, String languageId) {
        this.client   = client;
        this.textArea = textArea;
        this.uri      = file.toURI().toString();

        client.didOpen(uri, languageId, version.get(), textArea.getText());

        debounceTimer = new Timer(CHANGE_DEBOUNCE_MS, e -> pushChange());
        debounceTimer.setRepeats(false);

        syncListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { debounceTimer.restart(); }
            @Override public void removeUpdate(DocumentEvent e)  { debounceTimer.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { /* nur Attribut-, kein Textwechsel */ }
        };
        textArea.getDocument().addDocumentListener(syncListener);
    }

    private void pushChange() {
        if (closed) return;
        client.didChange(uri, version.incrementAndGet(), textArea.getText());
    }

    @Override
    protected List<Completion> getCompletionsImpl(JTextComponent comp) {
        if (closed || !client.isAlive()) return Collections.emptyList();

        // Falls noch eine Änderung im Debounce-Timer wartet, JETZT sofort
        // synchron nachholen. Sonst fragen wir den Server nach Vorschlägen
        // für eine Cursor-Position, die er (mit seinem veralteten
        // Dokumentstand) noch gar nicht kennt - das führt zu unsinnigen
        // Vorschlägen (z.B. bereits getippte Wörter erneut) oder leeren
        // Ergebnissen, wenn Strg+Leer kurz nach dem Tippen gedrückt wird.
        if (debounceTimer.isRunning()) {
            debounceTimer.stop();
            pushChange();
        }

        int line;
        int character;
        try {
            int caret = textArea.getCaretPosition();
            line = textArea.getLineOfOffset(caret);
            character = caret - textArea.getLineStartOffset(line);
        } catch (Exception ex) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> items = client.requestCompletions(uri, line, character, COMPLETION_TIMEOUT_MS);
        List<Completion> result = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            Object insertText = item.get("insertText");
            Object label       = item.get("label");
            String text = insertText != null ? String.valueOf(insertText)
                        : label != null       ? String.valueOf(label)
                        : null;
            if (text == null || text.isBlank()) continue;

            Object detailObj = item.get("detail");
            String detail = detailObj != null ? String.valueOf(detailObj) : null;

            result.add(new BasicCompletion(this, text, detail));
        }
        return result;
    }

    /**
     * Beendet die Bindung dieses einen Editor-Tabs an den Language-Server
     * (didClose, Listener entfernen). Der Server-Prozess selbst läuft
     * weiter, falls noch andere Tabs derselben Sprache offen sind – er
     * wird zentral über LspManager.shutdownAll() gestoppt.
     */
    public void close() {
        if (closed) return;
        closed = true;
        debounceTimer.stop();
        textArea.getDocument().removeDocumentListener(syncListener);
        if (client.isAlive()) client.didClose(uri);
    }
}