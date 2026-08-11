package editor;

import org.fife.ui.autocomplete.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import ui.ConsolePanel;
import ui.WordManagerDialog;
import ui.MainWindow;
import config.TIDEPreferences;
import config.TIDEProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.VolatileImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

import ui.OutlinePanel;

public class EditorManager {

    private final JFrame parent;
    private final JTabbedPane editorTabs;
    private final Map<Component, File> openFiles;
    private final ConsolePanel consolePanel;
    private final WordManagerDialog wordManagerDialog;
    private OutlinePanel outlinePanel;

    public EditorManager(JFrame parent, JTabbedPane editorTabs, Map<Component, File> openFiles,
            ConsolePanel consolePanel, WordManagerDialog wordManagerDialog) {
        this.parent            = parent;
        this.editorTabs        = editorTabs;
        this.openFiles         = openFiles;
        this.consolePanel      = consolePanel;
        this.wordManagerDialog = wordManagerDialog;
    }

    public void applyFontSizeToAllEditors(int size) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component tab = editorTabs.getComponentAt(i);
            if (tab instanceof RTextScrollPane sp) {
                Component view = sp.getViewport().getView();
                if (view instanceof RSyntaxTextArea textArea) {
                    textArea.setFont(new Font(TIDEProperties.EDITOR_FONT, Font.PLAIN, TIDEPreferences.getEditorFontSize()));
                }
            }
        }
    }

    public void updateUIWithLocale(Locale neueLocale) {
        Locale.setDefault(neueLocale);
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component tab = editorTabs.getComponentAt(i);
            if (tab instanceof JScrollPane scrollPane) {
                Component view = scrollPane.getViewport().getView();
                if (view instanceof RSyntaxTextArea textArea) {
                    textArea.setLocale(neueLocale);
                    translatePopupMenuRecursive(textArea.getPopupMenu(), neueLocale);
                    SwingUtilities.updateComponentTreeUI(textArea);
                }
            }
        }
    }

    public void setOutlinePanel(OutlinePanel outlinePanel) {
        this.outlinePanel = outlinePanel;
    }

    private void installPopupLocalizationHook(RSyntaxTextArea textArea) {
        JPopupMenu popup = textArea.getPopupMenu();
        if (popup != null) {
            popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    SwingUtilities.invokeLater(() ->
                        translatePopupMenuRecursive(popup, Locale.getDefault())
                    );
                }
                @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
                @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
            });
        }
    }

    private void translatePopupMenuRecursive(JPopupMenu popup, Locale locale) {
        if (popup == null) return;
        boolean english = locale.getLanguage().equalsIgnoreCase("en");
        for (Component c : popup.getComponents()) {
            if (c instanceof JMenu menu) {
                String txt = menu.getText();
                if (txt != null && !txt.isEmpty()) menu.setText(translateMenuItem(txt, english));
                translatePopupMenuRecursive(menu.getPopupMenu(), locale);
            } else if (c instanceof JMenuItem item) {
                String txt = item.getText();
                if (txt != null && !txt.isEmpty()) item.setText(translateMenuItem(txt, english));
            }
        }
        popup.revalidate();
        popup.repaint();
    }

    private String translateMenuItem(String text, boolean toEnglish) {
        String normalized = normalizeToEnglish(text);
        return toEnglish ? normalized : translateToGerman(normalized);
    }

    private String normalizeToEnglish(String text) {
        if (text == null || text.isEmpty()) return text;
        if (text.contains("Rückgängig") && text.contains("nicht")) return "Can't Undo";
        if (text.contains("Rückgängig"))                              return "Undo";
        if (text.contains("Wiederherstellen") || text.contains("Wiederholen")) return "Redo";
        if (text.contains("nicht möglich") || (text.contains("Kann nicht") && text.contains("wiederholen"))) return "Can't Redo";
        if (text.contains("Kann nicht wiederherstellen"))             return "Can't Redo";
        if (text.contains("Ausschneiden"))                            return "Cut";
        if (text.contains("Kopieren"))                                return "Copy";
        if (text.contains("Einfügen"))                                return "Paste";
        if (text.contains("Löschen"))                                 return "Delete";
        if (text.contains("Alles") && text.contains("auswählen"))    return "Select All";
        if (text.contains("markieren"))                               return "Select All";
        if (text.contains("Falten") && !text.contains("Falz"))       return "Folding";
        if (text.contains("Entfalten"))                               return "Unfolding";
        if (text.contains("Falz") && text.contains("umschalten"))    return "Toggle Current Fold";
        if (text.contains("Kommentare") && text.contains("einklappen")) return "Collapse All Comments";
        if (text.contains("Falze") && text.contains("einklappen"))   return "Collapse All Folds";
        if (text.contains("Falze") && text.contains("ausklappen"))   return "Expand All Folds";
        return text;
    }

    private String translateToGerman(String englishText) {
        return switch (englishText) {
            case "Undo"                  -> "Rückgängig";
            case "Can't Undo"            -> "Kann nicht rückgängig";
            case "Redo"                  -> "Wiederherstellen";
            case "Can't Redo"            -> "Kann nicht wiederherstellen";
            case "Cut"                   -> "Ausschneiden";
            case "Copy"                  -> "Kopieren";
            case "Paste"                 -> "Einfügen";
            case "Delete"                -> "Löschen";
            case "Select All"            -> "Alles markieren";
            case "Folding"               -> "Falten";
            case "Unfolding"             -> "Entfalten";
            case "Toggle Current Fold"   -> "Aktuellen Falz umschalten";
            case "Collapse All Comments" -> "Alle Kommentare einklappen";
            case "Collapse All Folds"    -> "Alle Falze einklappen";
            case "Expand All Folds"      -> "Alle Falze ausklappen";
            default                      -> englishText;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // THEME LADEN
    // ─────────────────────────────────────────────────────────────────────────
    private org.fife.ui.rsyntaxtextarea.Theme loadTheme(String themeSetting) {

        String customPath = TIDEPreferences.getEditorThemePath();
        if (customPath != null && !customPath.isBlank()) {
            File themeFile = new File(customPath);
            if (themeFile.exists()) {
                try (InputStream fis = new java.io.FileInputStream(themeFile)) {
                    return org.fife.ui.rsyntaxtextarea.Theme.load(fis);
                } catch (Exception e) {
                    consolePanel.log("[Theme] Datei-Theme konnte nicht geladen werden: "
                            + e.getMessage() + "\n", Color.ORANGE);
                }
            }
        }

        if (themeSetting == null || themeSetting.isBlank()) return null;

        String xmlName = themeSetting.toLowerCase().endsWith(".xml")
                ? themeSetting
                : themeSetting + ".xml";

        // Eigene, mitgelieferte Theme-XML im resources-Ordner (Klassenpfad-Wurzel),
        // z.B. src/main/resources/fire.xml für das Fire-Theme (Dateiname wird
        // klein geschrieben gesucht, damit "Fire" → "fire.xml" passt).
        String resourcesRootFile = "/" + xmlName.toLowerCase(Locale.ROOT);
        try (InputStream is = EditorManager.class.getResourceAsStream(resourcesRootFile)) {
            if (is != null) {
                return org.fife.ui.rsyntaxtextarea.Theme.load(is);
            }
        } catch (Exception e) {
            consolePanel.log("[Theme] Theme-XML im resources-Ordner konnte nicht geladen werden: "
                    + e.getMessage() + "\n", Color.ORANGE);
        }

        String ownResource = "/org/fife/ui/rsyntaxtextarea/themes/" + xmlName;
        try (InputStream is = EditorManager.class.getResourceAsStream(ownResource)) {
            if (is != null) {
                return org.fife.ui.rsyntaxtextarea.Theme.load(is);
            }
        } catch (Exception e) {
            consolePanel.log("[Theme] Eigene Ressource konnte nicht geladen werden: "
                    + e.getMessage() + "\n", Color.ORANGE);
        }

        String builtinResource = "themes/" + xmlName;
        try (InputStream is = org.fife.ui.rsyntaxtextarea.Theme.class
                .getResourceAsStream(builtinResource)) {
            if (is != null) {
                return org.fife.ui.rsyntaxtextarea.Theme.load(is);
            }
        } catch (Exception e) {
            consolePanel.log("[Theme] Eingebautes Theme konnte nicht geladen werden: "
                    + e.getMessage() + "\n", Color.ORANGE);
        }

        return null;
    }

    private void applyTheme(RSyntaxTextArea textArea) {
        String themeSetting = (MainWindow.THEME != null && MainWindow.THEME.syntaxTheme != null)
                ? MainWindow.THEME.syntaxTheme
                : TIDEPreferences.getTheme();

        try {
            org.fife.ui.rsyntaxtextarea.Theme rstaTheme = loadTheme(themeSetting);

            if (rstaTheme != null) {
                rstaTheme.apply(textArea);
            } else {
                try (InputStream fallback = org.fife.ui.rsyntaxtextarea.Theme.class
                        .getResourceAsStream("themes/dark.xml")) {
                    if (fallback != null) {
                        org.fife.ui.rsyntaxtextarea.Theme.load(fallback).apply(textArea);
                    }
                }
            }
        } catch (Exception e) {
            consolePanel.log("[FEHLER] Theme-Ladefehler: " + e.getMessage() + "\n", Color.RED);
        }
    }

    public void openFileInEditor(File file) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            if (file.equals(openFiles.get(editorTabs.getComponentAt(i)))) {
                editorTabs.setSelectedIndex(i);
                return;
            }
        }

        try {
            String content = Files.readString(file.toPath());
            RSyntaxTextArea textArea = new RSyntaxTextArea(20, 60);
            textArea.setLocale(Locale.getDefault());
            SwingUtilities.invokeLater(() -> installPopupLocalizationHook(textArea));
            textArea.setText(content);

            String fileName = file.getName().toLowerCase();
            if      (fileName.endsWith(".java"))                              textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            else if (fileName.endsWith(".py"))                                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
            else if (fileName.endsWith(".c")   || fileName.endsWith(".h"))   textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_C);
            else if (fileName.endsWith(".cpp") || fileName.endsWith(".hpp")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
            else if (fileName.endsWith(".xml"))                               textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
            else if (fileName.endsWith(".bat") || fileName.endsWith(".cmd")) textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
            else                                                               textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);

            textArea.setCodeFoldingEnabled(true);

            textArea.setFont(new Font(TIDEProperties.EDITOR_FONT, Font.PLAIN, TIDEPreferences.getEditorFontSize()));
            applyTheme(textArea);
            textArea.setFont(new Font(TIDEProperties.EDITOR_FONT, Font.PLAIN, TIDEPreferences.getEditorFontSize()));
            textArea.setCaretColor(Color.WHITE);

            float[] sharedDynIntensity = { 0.0f };
            int[]   sharedScrollDir    = { 0 };

            RTextScrollPane sp = new RTextScrollPane(textArea) {

                private int lastScrollValue = -1;
                private VolatileImage volatileBuffer = null;

                {
                    getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
                    getVerticalScrollBar().addAdjustmentListener(e -> {
                        int newVal = e.getValue();
                        if (lastScrollValue != -1) {
                            int delta = newVal - lastScrollValue;
                            if (delta != 0) sharedScrollDir[0] = delta > 0 ? 1 : -1;
                        }
                        lastScrollValue = newVal;
                    });
                }

                @Override
                public void paint(Graphics g) {
                    if (!TIDEPreferences.getMotionBlurEnabled()) {
                        super.paint(g);
                        return;
                    }

                    float dynIntensity = sharedDynIntensity[0];
                    if (dynIntensity < 0.01f) {
                        super.paint(g);
                        return;
                    }

                    int w = getWidth();
                    int h = getHeight();
                    GraphicsConfiguration gc = getGraphicsConfiguration();

                    if (volatileBuffer == null
                            || volatileBuffer.getWidth()  != w
                            || volatileBuffer.getHeight() != h
                            || volatileBuffer.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
                        if (volatileBuffer != null) volatileBuffer.flush();
                        volatileBuffer = gc.createCompatibleVolatileImage(w, h, Transparency.OPAQUE);
                    }

                    do {
                        if (volatileBuffer.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
                            volatileBuffer.flush();
                            volatileBuffer = gc.createCompatibleVolatileImage(w, h, Transparency.OPAQUE);
                        }
                        Graphics2D gBuf = volatileBuffer.createGraphics();
                        gBuf.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                        super.paint(gBuf);
                        gBuf.dispose();
                    } while (volatileBuffer.contentsLost());

                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

                    float baseAlpha = 1.0f - (0.10f * dynIntensity);
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, baseAlpha));
                    g2d.drawImage(volatileBuffer, 0, 0, null);

                    float blurAlpha = 0.75f * dynIntensity;
                    if (blurAlpha > 0.005f) {
                        int   trailDir = -sharedScrollDir[0];
                        float maxTrail = 40.0f * dynIntensity;

                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, blurAlpha * 0.40f));
                        g2d.drawImage(volatileBuffer, 0, Math.round(maxTrail * 0.30f * trailDir), null);

                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, blurAlpha * 0.20f));
                        g2d.drawImage(volatileBuffer, 0, Math.round(maxTrail * 0.80f * trailDir), null);
                    }

                    g2d.dispose();
                }
            };

            sp.getVerticalScrollBar().setUnitIncrement(0);
            javax.swing.Timer smoothScrollTimer = null;
            if (parent instanceof MainWindow mw) {
                smoothScrollTimer = mw.enableSmoothScrolling(sp, sharedDynIntensity, sharedScrollDir);
            }
            final javax.swing.Timer timerRef = smoothScrollTimer;
            sp.getVerticalScrollBar().setUnitIncrement(
                    textArea.getFontMetrics(textArea.getFont()).getHeight());
            sp.setBorder(null);

            JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            tabHeader.setOpaque(false);
            tabHeader.add(new JLabel(file.getName()));

            JButton closeBtn = new JButton("×");
            closeBtn.setBorder(null);
            closeBtn.setContentAreaFilled(false);
            closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            closeBtn.addActionListener(e -> {
                if (timerRef != null) timerRef.stop();
                openFiles.remove(sp);
                editorTabs.remove(sp);
            });
            tabHeader.add(closeBtn);

            editorTabs.addTab(file.getName(), sp);
            editorTabs.setTabComponentAt(editorTabs.getTabCount() - 1, tabHeader);
            editorTabs.setSelectedComponent(sp);
            openFiles.put(sp, file);
            textArea.requestFocusInWindow();

            if (outlinePanel != null) {
                outlinePanel.refresh(textArea, file.getName());
            }

            DefaultCompletionProvider provider = createCompletionProvider(textArea);
            AutoCompletion ac = new AutoCompletion(provider);
            ac.setAutoCompleteSingleChoices(false);
            ac.setAutoActivationEnabled(true);
            ac.setAutoActivationDelay(TIDEProperties.AUTOCOMPLETE_DELAY);
            ac.install(textArea);

            Set<String> knownWords = new HashSet<>();
            String[] initialKeywords = {"public", "private", "static", "void", "class", "import",
                    "String", "int", "boolean", "new", "return"};
            knownWords.addAll(Arrays.asList(initialKeywords));
            String existingContent = textArea.getText();
            if (existingContent != null) {
                for (String t : existingContent.split("[^\\w]+")) {
                    if (t.length() > TIDEProperties.AUTOCOMPLETE_MIN_LEN) knownWords.add(t);
                }
            }

            textArea.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!Character.isLetterOrDigit(c) && c != KeyEvent.CHAR_UNDEFINED) {
                        try {
                            int caret = textArea.getCaretPosition() - 1;
                            if (caret < 1) return;
                            String text = textArea.getText(0, caret);
                            int start   = caret - 1;
                            while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) start--;
                            start++;
                            if (start < caret) {
                                String word = text.substring(start, caret);
                                if (word.length() > TIDEProperties.AUTOCOMPLETE_MIN_LEN && knownWords.add(word)) {
                                    provider.addCompletion(new BasicCompletion(provider, word));
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });

            JButton manageWordsBtn = new JButton("Wörter");
            manageWordsBtn.setFont(manageWordsBtn.getFont().deriveFont(10f));
            manageWordsBtn.setForeground(new Color(180, 180, 255));
            manageWordsBtn.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
            manageWordsBtn.setContentAreaFilled(false);
            manageWordsBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            manageWordsBtn.setToolTipText("Gelernte Wörter verwalten / löschen");
            manageWordsBtn.addActionListener(ev -> wordManagerDialog.show(provider, knownWords));
            tabHeader.add(manageWordsBtn);

        } catch (Exception e) {
            consolePanel.log("Öffnen fehlgeschlagen\n", Color.RED);
        }
    }

    public void saveCurrentFile() {
        Component tab = editorTabs.getSelectedComponent();
        if (tab instanceof RTextScrollPane sp) {
            File file = openFiles.get(sp);
            RSyntaxTextArea ta = (RSyntaxTextArea) sp.getTextArea();
            try {
                Files.writeString(file.toPath(), ta.getText());
                consolePanel.log("[SAVE] " + file.getName() + " gespeichert.\n", Color.GREEN);
            } catch (IOException e) {
                consolePanel.log("Fehler beim Speichern\n", Color.RED);
            }
        }
    }

    public void saveAllFiles() {
        int saved = 0, errors = 0;
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component tab = editorTabs.getComponentAt(i);
            if (!(tab instanceof RTextScrollPane sp)) continue;
            File file = openFiles.get(sp);
            if (file == null) continue;
            RSyntaxTextArea ta = (RSyntaxTextArea) sp.getTextArea();
            try {
                Files.writeString(file.toPath(), ta.getText());
                saved++;
            } catch (IOException e) {
                consolePanel.log("[SAVE] Fehler beim Speichern von " + file.getName() + "\n", Color.RED);
                errors++;
            }
        }
        if (errors == 0) {
            consolePanel.log("[SAVE] " + saved + " Datei(en) gespeichert.\n", Color.GREEN);
        } else {
            consolePanel.log("[SAVE] " + saved + " gespeichert, " + errors + " Fehler.\n", Color.ORANGE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FORMAT – sprachspezifisch, Cursor-Position per Zeile+Spalte gesichert
    // ─────────────────────────────────────────────────────────────────────────
    public void formatCurrentFile() {
        Component tab = editorTabs.getSelectedComponent();
        if (!(tab instanceof RTextScrollPane sp)) return;

        RSyntaxTextArea ta = (RSyntaxTextArea) sp.getTextArea();

        // ── Cursor-Position als Zeile + Spalte sichern ────────────────────
        // Ein absoluter Offset wird nach setText() falsch, weil sich Zeilen
        // verschieben. Zeile + Spalte bleibt semantisch korrekt: wir landen
        // exakt an der gleichen Stelle im Code, egal ob Leerzeilen wegfallen.
        int savedLine = 0;
        int savedCol  = 0;
        try {
            int caretOffset = ta.getCaretPosition();
            savedLine = ta.getLineOfOffset(caretOffset);
            savedCol  = caretOffset - ta.getLineStartOffset(savedLine);
        } catch (Exception ignored) {}

        // ── Sprache ermitteln ──────────────────────────────────────────────
        File activeFile = getActiveFile();
        String fileName = activeFile != null ? activeFile.getName().toLowerCase() : "";
        boolean isPython = fileName.endsWith(".py");
        boolean isJavaLike = fileName.endsWith(".java")
                || fileName.endsWith(".c")
                || fileName.endsWith(".cpp")
                || fileName.endsWith(".h")
                || fileName.endsWith(".hpp");

        String formatted;
        if (isPython) {
            formatted = formatPython(ta.getText(), ta.getTabSize());
        } else if (isJavaLike) {
            formatted = formatBraces(ta.getText(), ta.getTabSize(), !ta.getTabsEmulated());
        } else {
            // Für alle anderen Dateien: nur Trailing-Whitespace entfernen
            formatted = stripTrailingWhitespace(ta.getText());
        }

        // ── Text ersetzen und Cursor wiederherstellen ──────────────────────
        ta.setText(formatted);

        try {
            int totalLines = ta.getLineCount();
            // Falls durch Formatierung Zeilen weggefallen sind → auf letzte begrenzen
            int targetLine = Math.min(savedLine, totalLines - 1);
            int lineStart  = ta.getLineStartOffset(targetLine);
            int lineLen    = ta.getLineEndOffset(targetLine) - lineStart;
            // Spalte auf die tatsächliche Zeilenlänge begrenzen
            int targetCol  = Math.min(savedCol, Math.max(0, lineLen - 1));
            ta.setCaretPosition(lineStart + targetCol);
        } catch (Exception ignored) {
            // Fallback: Anfang
            ta.setCaretPosition(0);
        }

        consolePanel.log("[FORMAT] " + (isPython ? "Python" : isJavaLike ? "Java/C" : "Datei")
                + " formatiert.\n", Color.GREEN);
    }

    // ── Python-Formatter ──────────────────────────────────────────────────────
    // Strategie: die logische Einrücktiefe liest sich aus dem vorhandenen Code.
    // Wir normalisieren nur die Einrückzeichen (Tabs → Spaces oder umgekehrt)
    // und entfernen trailing whitespace. Die Einrücktiefe selbst ändern wir NICHT –
    // Python-Einrückung ist semantisch, der Benutzer hat sie absichtlich gesetzt.
    private String formatPython(String code, int tabSize) {
        String[] lines = code.split("\n", -1);
        StringBuilder result = new StringBuilder(code.length() + 64);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Trailing whitespace entfernen
            String trimmedRight = trimRight(line);

            // Einrückung normalisieren: gemischte Tabs+Spaces → nur Spaces
            // Wir expandieren führende Tabs auf tabSize Spaces
            String normalized = expandLeadingTabs(trimmedRight, tabSize);

            result.append(normalized);
            if (i < lines.length - 1) result.append("\n");
        }

        return result.toString();
    }

    // ── Java/C-Brace-Formatter ────────────────────────────────────────────────
    // Gleiche Logik wie vorher, aber sauberer refaktoriert und mit Tab-Unterstützung.
    private String formatBraces(String code, int tabSize, boolean useTabs) {
        String   tabUnit = useTabs ? "\t" : " ".repeat(Math.max(1, tabSize));
        String[] lines   = code.split("\n", -1);
        StringBuilder result = new StringBuilder(code.length() + 256);
        int indent = 0;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = trimRight(lines[i]).stripLeading();

            // Schließende Klammern VOR der Einrückung verringern
            if (trimmed.startsWith("}") || trimmed.startsWith(")") || trimmed.startsWith("]")) {
                indent = Math.max(0, indent - 1);
            }

            if (!trimmed.isEmpty()) {
                result.append(tabUnit.repeat(indent)).append(trimmed);
            }
            if (i < lines.length - 1) result.append("\n");

            // Öffnende minus schließende Klammern auf dieser Zeile → Einrück-Delta
            long opens  = trimmed.chars().filter(c -> c == '{' || c == '(' || c == '[').count();
            long closes = trimmed.chars().filter(c -> c == '}' || c == ')' || c == ']').count();

            // Die bereits verarbeitete führende schließende Klammer nicht doppelt zählen
            if (trimmed.startsWith("}") || trimmed.startsWith(")") || trimmed.startsWith("]")) {
                closes = Math.max(0, closes - 1);
            }

            indent = Math.max(0, indent + (int)(opens - closes));
        }

        return result.toString();
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    /** Entfernt nur Whitespace am Zeilenende (rechts), lässt Einrückung links. */
    private static String trimRight(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    /** Expandiert führende Tabs zu Spaces (für Python-Normalisierung). */
    private static String expandLeadingTabs(String line, int tabSize) {
        if (line.isEmpty() || line.charAt(0) == ' ') return line; // Häufigster Fall: bereits Spaces
        StringBuilder sb = new StringBuilder(line.length() + 16);
        int col = 0;
        int i   = 0;
        // Nur den führenden Whitespace-Bereich verarbeiten
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\t') {
                int spaces = tabSize - (col % tabSize);
                sb.append(" ".repeat(spaces));
                col += spaces;
                i++;
            } else if (c == ' ') {
                sb.append(' ');
                col++;
                i++;
            } else {
                // Rest der Zeile unverändert anhängen
                sb.append(line, i, line.length());
                break;
            }
        }
        return sb.toString();
    }

    /** Entfernt nur Trailing-Whitespace, keine sonstige Formatierung. */
    private static String stripTrailingWhitespace(String code) {
        String[] lines = code.split("\n", -1);
        StringBuilder result = new StringBuilder(code.length());
        for (int i = 0; i < lines.length; i++) {
            result.append(trimRight(lines[i]));
            if (i < lines.length - 1) result.append("\n");
        }
        return result.toString();
    }

    public File getActiveFile() {
        Component tab = editorTabs.getSelectedComponent();
        return tab != null ? openFiles.get(tab) : null;
    }

    private DefaultCompletionProvider createCompletionProvider(RSyntaxTextArea textArea) {
        DefaultCompletionProvider provider = new DefaultCompletionProvider();
        Set<String> seen = new HashSet<>();

        for (String kw : new String[]{"public", "private", "static", "void", "class", "import",
                "String", "int", "boolean", "new", "return"}) {
            if (seen.add(kw)) provider.addCompletion(new BasicCompletion(provider, kw));
        }

        String content = textArea.getText();
        if (content != null && !content.isEmpty()) {
            for (String token : content.split("[^\\w]+")) {
                if (token.length() > TIDEProperties.AUTOCOMPLETE_MIN_LEN && seen.add(token)) {
                    provider.addCompletion(new BasicCompletion(provider, token));
                }
            }
        }

        return provider;
    }
}
