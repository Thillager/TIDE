package config;

/**
 * Steuert, welche Engine für die Code-Vervollständigung im Editor
 * verwendet wird.
 *
 * WORD_BASED → Die bisherige, "unintelligente" Vervollständigung: sie
 *              schlägt Wörter vor, die bereits irgendwo im geöffneten
 *              Dokument vorkommen (siehe DefaultCompletionProvider in
 *              EditorManager). Verbraucht keine zusätzlichen Ressourcen.
 *
 * LSP        → Intelligente Vervollständigung über das Language Server
 *              Protocol. Startet für jede benötigte Sprache (aktuell Java
 *              und Python) bei Bedarf vollautomatisch einen externen
 *              Language-Server-Prozess (siehe lsp.LspManager /
 *              lsp.LspProvisioner - ohne dass der Nutzer etwas eintragen
 *              muss). Nur wenn dieser Modus aktiv ist, wird überhaupt ein
 *              Server-Prozess gestartet.
 */
public enum CompletionMode {
    WORD_BASED,
    LSP;

    public static CompletionMode fromString(String value) {
        if (value == null) return WORD_BASED;
        try {
            return CompletionMode.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return WORD_BASED;
        }
    }
}
