package ui;

import config.LanguageManager;
import config.TIDEPreferences;
import config.TIDEProperties;
import config.Theme; // Importiert dein Theme-Objekt
import editor.EditorManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Locale;

import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.HashMap;

public class SettingsDialog {

    private final JFrame parent;
    private final EditorManager editorManager;
    private final Runnable onLanguageChanged;
    
    // Wir holen uns das aktuell aktive Theme beim Öffnen des Dialogs
    private final Theme currentTheme;

    public SettingsDialog(JFrame parent, EditorManager editorManager, Runnable onLanguageChanged) {
        this.parent            = parent;
        this.editorManager     = editorManager;
        this.onLanguageChanged = onLanguageChanged;
        
        // Holt das Theme aus den Preferences (Standard: DARK über byName abgefangen)
        this.currentTheme      = Theme.byName(TIDEPreferences.getTheme());
    }

    public void show() {
        JDialog dialog = new JDialog(parent, "Einstellungen / Settings", true);
        dialog.setSize(600, 750); 
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout(10, 10));
        
        // Dynamische Theme-Farbe für den Dialog-Hintergrund
        dialog.getContentPane().setBackground(currentTheme.background);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(currentTheme.background);
        content.setBorder(new EmptyBorder(15, 20, 10, 20));

        // ── Sprache ──────────────────────────────────────────────
        JPanel langPanel = createSection("Sprache / Language");

        // Sprach-Auswahl-Styling
        JComboBox<LanguageManager.Language> langBox = new JComboBox<>(LanguageManager.Language.values());
        langBox.setSelectedItem(LanguageManager.Language.valueOf(TIDEPreferences.getLanguage()));
        langBox.setMaximumSize(new Dimension(200, 28));
        langBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        langBox.setBackground(currentTheme.backgroundLight);
        langBox.setForeground(currentTheme.foreground);
        
        langPanel.add(langBox);
        content.add(langPanel);
        content.add(Box.createVerticalStrut(12));


        // ── Theme / Design (IDE Theme + Editor XML getrennt) ───────
        JPanel themePanel = createSection("Theme / Design", 280);
        themePanel.setLayout(new BoxLayout(themePanel, BoxLayout.Y_AXIS));

        // 1. IDE Look & Feel
        JLabel lblIdeTheme = new JLabel("IDE-Design (Look & Feel):");
        lblIdeTheme.setForeground(currentTheme.foregroundDim);
        lblIdeTheme.setAlignmentX(Component.LEFT_ALIGNMENT);
        themePanel.add(lblIdeTheme);
        themePanel.add(Box.createVerticalStrut(4));

        String[] builtInThemes = java.util.Arrays.stream(config.Theme.ALL)
                .map(t -> t.name).toArray(String[]::new);
        
        JComboBox<String> themeBox = new JComboBox<>(builtInThemes);
        themeBox.setMaximumSize(new Dimension(220, 28));
        themeBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        themeBox.setBackground(currentTheme.backgroundLight);
        themeBox.setForeground(currentTheme.foreground);
        themeBox.setSelectedItem(currentTheme.name); // Nimmt den Namen des aktuellen Themes
        themePanel.add(themeBox);

        themePanel.add(Box.createVerticalStrut(12));

        // 2. Editor Farbschema (XML Datei)
        JLabel lblEditorTheme = new JLabel("Editor-Farbschema (XML-Datei):");
        lblEditorTheme.setForeground(currentTheme.foregroundDim);
        lblEditorTheme.setAlignmentX(Component.LEFT_ALIGNMENT);
        themePanel.add(lblEditorTheme);
        themePanel.add(Box.createVerticalStrut(4));

        JPanel themeRow = new JPanel(new BorderLayout(10, 0));
        themeRow.setOpaque(false);
        themeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        themeRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField txtThemePath = new JTextField(TIDEPreferences.getEditorThemePath());
        txtThemePath.setBackground(currentTheme.background);
        txtThemePath.setForeground(currentTheme.foreground);
        txtThemePath.setCaretColor(currentTheme.foreground);
        txtThemePath.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(currentTheme.border),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JButton btnBrowse = new JButton("Durchsuchen...");
        btnBrowse.setBackground(currentTheme.backgroundLight);
        btnBrowse.setForeground(currentTheme.foreground);
        btnBrowse.setFocusPainted(false);
        
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("XML Themes (*.xml)", "xml"));
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                txtThemePath.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        themeRow.add(txtThemePath, BorderLayout.CENTER);
        themeRow.add(btnBrowse, BorderLayout.EAST);
        themePanel.add(themeRow);

        themePanel.add(Box.createVerticalStrut(12));

        // 3. Custom FlatLaf-Theme (.properties Datei)
        JLabel lblFlatLafTheme = new JLabel("Custom FlatLaf-Theme (.properties-Datei):");
        lblFlatLafTheme.setForeground(currentTheme.foregroundDim);
        lblFlatLafTheme.setAlignmentX(Component.LEFT_ALIGNMENT);
        themePanel.add(lblFlatLafTheme);
        themePanel.add(Box.createVerticalStrut(4));

        JPanel flatLafRow = new JPanel(new BorderLayout(10, 0));
        flatLafRow.setOpaque(false);
        flatLafRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        flatLafRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField txtFlatLafPath = new JTextField(TIDEPreferences.getFlatLafThemePath());
        txtFlatLafPath.setBackground(currentTheme.background);
        txtFlatLafPath.setForeground(currentTheme.foreground);
        txtFlatLafPath.setCaretColor(currentTheme.foreground);
        txtFlatLafPath.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(currentTheme.border),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JButton btnBrowseFlatLaf = new JButton("Durchsuchen...");
        btnBrowseFlatLaf.setBackground(currentTheme.backgroundLight);
        btnBrowseFlatLaf.setForeground(currentTheme.foreground);
        btnBrowseFlatLaf.setFocusPainted(false);

        btnBrowseFlatLaf.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "FlatLaf Themes (*.properties)", "properties"));
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                txtFlatLafPath.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        flatLafRow.add(txtFlatLafPath, BorderLayout.CENTER);
        flatLafRow.add(btnBrowseFlatLaf, BorderLayout.EAST);
        themePanel.add(flatLafRow);

        JLabel flatLafHint = new JLabel(
            "<html>Eigenes FlatLaf-Theme (z.B. mit dem FlatLaf Theme Editor erstellt). "
            + "Ist hier ein Pfad gesetzt, hat er Vorrang vor dem IDE-Design oben.</html>");
        flatLafHint.setForeground(currentTheme.foregroundDim);
        flatLafHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        flatLafHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        themePanel.add(Box.createVerticalStrut(6));
        themePanel.add(flatLafHint);

        JLabel themeHint = new JLabel(
            "<html>Änderungen wirken nach einem Neustart von TIDE.</html>");
        themeHint.setForeground(currentTheme.foregroundDim);
        themeHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        themeHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        themePanel.add(Box.createVerticalStrut(6));
        themePanel.add(themeHint);

        content.add(themePanel);
        content.add(Box.createVerticalStrut(12));

        // ── Schriftgröße ─────────────────────────────────────────
        JPanel fontPanel = createSection("Editor-Schriftgröße / Font Size");

        int currentSize = TIDEPreferences.getEditorFontSize();
        JLabel fontSizeLabel = new JLabel(currentSize + " pt");
        fontSizeLabel.setForeground(currentTheme.foregroundDim);
        fontSizeLabel.setFont(new Font("Consolas", Font.PLAIN, 13));

        JSlider fontSlider = new JSlider(8, 28, currentSize);
        fontSlider.setBackground(currentTheme.background);
        fontSlider.setForeground(currentTheme.foregroundDim);
        fontSlider.setMajorTickSpacing(4);
        fontSlider.setMinorTickSpacing(1);
        fontSlider.setPaintTicks(true);
        fontSlider.setPaintLabels(true);
        fontSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        fontSlider.setAlignmentX(Component.LEFT_ALIGNMENT);

        fontSlider.addChangeListener(e -> fontSizeLabel.setText(fontSlider.getValue() + " pt"));

        JPanel fontRow = new JPanel(new BorderLayout(8, 0));
        fontRow.setBackground(currentTheme.background);
        fontRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        fontRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fontRow.add(fontSlider, BorderLayout.CENTER);
        fontRow.add(fontSizeLabel, BorderLayout.EAST);

        fontPanel.add(fontRow);
        content.add(fontPanel);
        content.add(Box.createVerticalStrut(12));

        // -- Auto stop --------------------------------------------
        JPanel auStPanel = createSection("Stop when to many resources are used");

        JCheckBox auStBox = new JCheckBox("Auto Stop", TIDEPreferences.getAuSt());
        auStBox.setBackground(currentTheme.background);
        auStBox.setForeground(currentTheme.foregroundDim);

        auStPanel.add(auStBox);
        content.add(auStPanel);
        content.add(Box.createVerticalStrut(12));

        // ── Autocomplete ─────────────────────────────────────────
        JPanel acPanel = createSection("Autocomplete");

        int currentDelay = TIDEPreferences.getAutocompleteDelay();
        JLabel acLabel = new JLabel("Verzögerung: " + currentDelay + " ms");
        acLabel.setForeground(currentTheme.foregroundDim);
        acLabel.setFont(new Font("Consolas", Font.PLAIN, 13));

        JSlider acSlider = new JSlider(0, 1000, currentDelay);
        acSlider.setBackground(currentTheme.background);
        acSlider.setForeground(currentTheme.foregroundDim);
        acSlider.setMajorTickSpacing(250);
        acSlider.setPaintTicks(true);
        acSlider.setPaintLabels(true);
        acSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        acSlider.setAlignmentX(Component.LEFT_ALIGNMENT);

        acSlider.addChangeListener(e -> acLabel.setText("Verzögerung: " + acSlider.getValue() + " ms"));

        JPanel acRow = new JPanel(new BorderLayout(8, 0));
        acRow.setBackground(currentTheme.background);
        acRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        acRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        acRow.add(acSlider, BorderLayout.CENTER);
        acRow.add(acLabel, BorderLayout.EAST);

        acPanel.add(acRow);
        content.add(acPanel);
        content.add(Box.createVerticalStrut(12));

        // ── Visuell ───────────────────────────────────────────────
        JPanel visualPanel = createSection("Visuell / Visual", 210);

        JCheckBox motionBlurBox = new JCheckBox(
            "Scroll-Effekte aktiviert (Motion Blur & Stretch)",
            TIDEPreferences.getMotionBlurEnabled());
        motionBlurBox.setBackground(currentTheme.background);
        motionBlurBox.setForeground(currentTheme.foregroundDim);
        motionBlurBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        motionBlurBox.setToolTipText("Motion Blur und Stretch-Effekt beim Scrollen ein-/ausschalten");
        visualPanel.add(motionBlurBox);

        // Scroll FPS Dropdown
        JPanel fpsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        fpsPanel.setBackground(currentTheme.background);

        JLabel lblFps = new JLabel("Scroll Animations-FPS: ");
        lblFps.setForeground(currentTheme.foreground);
        lblFps.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        Integer[] fpsOptions = { 60, 90, 144, 240 };
        JComboBox<Integer> cbFps = new JComboBox<>(fpsOptions);
        cbFps.setSelectedItem(TIDEPreferences.getScrollFPS());
        cbFps.setBackground(currentTheme.backgroundLight);
        cbFps.setForeground(Color.WHITE);

        fpsPanel.add(lblFps);
        fpsPanel.add(cbFps);
        visualPanel.add(fpsPanel);

        // Scroll-Geschwindigkeit Slider
        JPanel speedPanel = new JPanel(new BorderLayout(10, 0));
        speedPanel.setBackground(currentTheme.background);
        speedPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        speedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSpeedDesc = new JLabel("Scroll-Geschwindigkeit: ");
        lblSpeedDesc.setForeground(currentTheme.foreground);
        lblSpeedDesc.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        int currentSpeed = TIDEPreferences.getScrollSpeed();
        JLabel speedValueLabel = new JLabel(currentSpeed + "%");
        speedValueLabel.setForeground(currentTheme.foregroundDim);
        speedValueLabel.setFont(new Font("Consolas", Font.PLAIN, 13));
        speedValueLabel.setPreferredSize(new Dimension(45, 20));

        JSlider speedSlider = new JSlider(10, 250, currentSpeed);
        speedSlider.setBackground(currentTheme.background);
        speedSlider.setForeground(currentTheme.foregroundDim);
        speedSlider.setMajorTickSpacing(40);
        speedSlider.setPaintTicks(true);
        speedSlider.addChangeListener(e -> speedValueLabel.setText(speedSlider.getValue() + "%"));

        JPanel speedControls = new JPanel(new BorderLayout(5, 0));
        speedControls.setBackground(currentTheme.background);
        speedControls.add(speedSlider, BorderLayout.CENTER);
        speedControls.add(speedValueLabel, BorderLayout.EAST);

        speedPanel.add(lblSpeedDesc, BorderLayout.WEST);
        speedPanel.add(speedControls, BorderLayout.CENTER);

        visualPanel.add(Box.createVerticalStrut(8));
        visualPanel.add(speedPanel);

        JLabel restartHint = new JLabel(
            "<html>Hinweis: Nach Änderung der Scroll-FPS wird ein Neustart empfohlen.</html>");
        restartHint.setForeground(currentTheme.foregroundDim);
        restartHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        restartHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        visualPanel.add(Box.createVerticalStrut(4));
        visualPanel.add(restartHint);

        content.add(visualPanel);
        content.add(Box.createVerticalStrut(12));

        // ── Rendering / Hardwarebeschleunigung ────────────────────
        JPanel hwPanel = createSection("Rendering / Hardwarebeschleunigung", 90);

        JPanel hwRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        hwRow.setBackground(currentTheme.background);

        JLabel lblHw = new JLabel("Hardwarebeschleunigung: ");
        lblHw.setForeground(currentTheme.foreground);
        lblHw.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        String[] hwOptions   = { "Automatisch", "Immer AN", "Immer AUS" };
        String[] hwOptionKeys = { "auto", "on", "off" };

        JComboBox<String> hwBox = new JComboBox<>(hwOptions);
        String currentHw = TIDEPreferences.getHwAccelMode();
        for (int i = 0; i < hwOptionKeys.length; i++) {
            if (hwOptionKeys[i].equals(currentHw)) {
                hwBox.setSelectedIndex(i);
                break;
            }
        }
        hwBox.setBackground(currentTheme.backgroundLight);
        hwBox.setForeground(Color.WHITE);
        hwBox.setMaximumSize(new Dimension(160, 28));

        hwRow.add(lblHw);
        hwRow.add(hwBox);
        hwPanel.add(hwRow);

        JLabel hwHint = new JLabel(
            "<html>Automatisch: AN bei installierter App, AUS beim JAR-Start.<br>" +
            "Änderung wirkt nach Neustart von TIDE.</html>");
        hwHint.setForeground(currentTheme.foregroundDim);
        hwHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hwHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hwPanel.add(Box.createVerticalStrut(4));
        hwPanel.add(hwHint);

        content.add(hwPanel);
        content.add(Box.createVerticalStrut(12));

        // ── Konsole ───────────────────────────────────────────────
        JPanel consolePanel = createSection("Konsole / Console");

        JCheckBox autoScrollBox = new JCheckBox(
            "Auto-Scroll aktiviert",
            TIDEPreferences.getConsoleAutoScroll());
        autoScrollBox.setBackground(currentTheme.background);
        autoScrollBox.setForeground(currentTheme.foregroundDim);
        autoScrollBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        consolePanel.add(autoScrollBox);
        content.add(consolePanel);
        content.add(Box.createVerticalStrut(12));

        // ── Hotkeys ───────────────────────────────────────────────
        JPanel hotkeyPanel = createSection("Hotkeys", 230);

        Object[][] hotkeyDefs = {
            { "save",    "Speichern",      KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK },
            { "stop",    "Stop",     KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK },
            { "search",  "Lokale Suche",   KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK },
            { "gsearch", "Globale Suche",  KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK },
            { "run",     "Ausführen",      KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK },
            { "debug",   "Debuggen",       KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK },
        };

        Map<String, Integer> pendingHotkeys   = new HashMap<>();
        Map<String, Integer> pendingModifiers = new HashMap<>();
        JPanel tablePanel = new JPanel(new GridLayout(hotkeyDefs.length, 2, 10, 6));
        tablePanel.setBackground(currentTheme.background);
        tablePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (Object[] def : hotkeyDefs) {
            String action     = (String)  def[0];
            String label      = (String)  def[1];
            int    defaultKey = (Integer) def[2];
            int    defaultMod = (Integer) def[3];
            int    savedKey   = TIDEPreferences.getHotkey(action, defaultKey);
            int    savedMod   = TIDEPreferences.getHotkeyModifier(action, defaultMod);

            JLabel descLabel = new JLabel(label);
            descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            descLabel.setForeground(currentTheme.foregroundDim);

            String initModText = "";
            if ((savedMod & InputEvent.CTRL_DOWN_MASK)  != 0) initModText += "Strg+";
            if ((savedMod & InputEvent.SHIFT_DOWN_MASK) != 0) initModText += "Shift+";
            if ((savedMod & InputEvent.ALT_DOWN_MASK)   != 0) initModText += "Alt+";

            JTextField keyField = new JTextField(initModText + KeyEvent.getKeyText(savedKey));
            keyField.setFont(new Font("Consolas", Font.BOLD, 12));
            keyField.setForeground(currentTheme.accent); // Nutzt das Farb-Highlight des Themes
            keyField.setBackground(currentTheme.backgroundLight);
            keyField.setCaretColor(currentTheme.accent);
            keyField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(currentTheme.border),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6)));
            keyField.setEditable(false);
            keyField.setMaximumSize(new Dimension(80, 26));

            keyField.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        int code = e.getKeyCode();
                        if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_SHIFT
                            || code == KeyEvent.VK_ALT || code == KeyEvent.VK_META) return;

                        int mod = 0;
                        if (e.isControlDown()) mod |= InputEvent.CTRL_DOWN_MASK;
                        if (e.isShiftDown())   mod |= InputEvent.SHIFT_DOWN_MASK;
                        if (e.isAltDown())     mod |= InputEvent.ALT_DOWN_MASK;

                        String modText = "";
                        if (e.isControlDown()) modText += "Strg+";
                        if (e.isShiftDown())   modText += "Shift+";
                        if (e.isAltDown())     modText += "Alt+";

                        keyField.setText(modText + KeyEvent.getKeyText(code));
                        keyField.setForeground(currentTheme.accentGreen); // Visuelles Feedback bei Erfolg

                        pendingHotkeys.put(action, code);
                        pendingModifiers.put(action, mod);
                    }
                });

            keyField.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override public void focusGained(java.awt.event.FocusEvent e) {
                        keyField.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(currentTheme.accentGreen),
                                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
                    }
                    @Override public void focusLost(java.awt.event.FocusEvent e) {
                        keyField.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(currentTheme.border),
                                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
                    }
                });

            tablePanel.add(descLabel);
            tablePanel.add(keyField);
        }

        hotkeyPanel.add(tablePanel);
        content.add(hotkeyPanel);
        content.add(Box.createVerticalStrut(12));

        // ── Buttons ───────────────────────────────────────────────
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setBackground(currentTheme.background);

        JButton btnApply  = new JButton("Übernehmen");
        JButton btnCancel = new JButton("Abbrechen");

        btnApply.setForeground(currentTheme.accentGreen);
        btnCancel.setForeground(currentTheme.foregroundDim);
        btnApply.setBackground(currentTheme.backgroundLight);
        btnCancel.setBackground(currentTheme.backgroundLight);

        btnCancel.addActionListener(e -> dialog.dispose());

        btnApply.addActionListener(e -> {
                // Sprache
                LanguageManager.Language selectedLang = (LanguageManager.Language) langBox.getSelectedItem();
                LanguageManager.set(selectedLang);
                TIDEPreferences.saveLanguage(selectedLang.name());
                Locale locale = selectedLang.name().equals("DE") ? Locale.GERMAN : Locale.ENGLISH;
                Locale.setDefault(locale);
                JComponent.setDefaultLocale(locale);
                if (editorManager != null) editorManager.updateUIWithLocale(locale);

                // Schriftgröße
                int newSize = fontSlider.getValue();
                TIDEPreferences.saveEditorFontSize(newSize);
                if (editorManager != null) editorManager.applyFontSizeToAllEditors(newSize);

                // Design Speichern
                TIDEPreferences.saveTheme((String) themeBox.getSelectedItem());
                TIDEPreferences.saveEditorThemePath(txtThemePath.getText());
                TIDEPreferences.saveFlatLafThemePath(txtFlatLafPath.getText());

                // Autocomplete-Delay
                TIDEPreferences.saveAutocompleteDelay(acSlider.getValue());
                TIDEPreferences.saveAuSt(auStBox.isSelected());

                // Motion Blur & Scroll
                TIDEPreferences.saveMotionBlurEnabled(motionBlurBox.isSelected());
                TIDEPreferences.saveScrollSpeed(speedSlider.getValue());
                TIDEPreferences.saveScrollFPS((int) cbFps.getSelectedItem());

                // Hardwarebeschleunigung
                TIDEPreferences.saveHwAccelMode(hwOptionKeys[hwBox.getSelectedIndex()]);

                // Auto-Scroll
                TIDEPreferences.saveConsoleAutoScroll(autoScrollBox.isSelected());

                // Hotkeys
                for (Map.Entry<String, Integer> entry : pendingHotkeys.entrySet()) {
                    TIDEPreferences.saveHotkey(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<String, Integer> entry : pendingModifiers.entrySet()) {
                    TIDEPreferences.saveHotkeyModifier(entry.getKey(), entry.getValue());
                }

                if (onLanguageChanged != null) onLanguageChanged.run();

                dialog.dispose();
            });

        btnPanel.add(btnCancel);
        btnPanel.add(btnApply);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(currentTheme.background);

        int baseIncrement = 16;
        double multiplier = TIDEPreferences.getScrollSpeed() / 100.0;
        scrollPane.getVerticalScrollBar().setUnitIncrement((int)(baseIncrement * multiplier));

        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(btnPanel,   BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private JPanel createSection(String title, int height) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(currentTheme.background);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));

        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(currentTheme.border, 1), title);
        border.setTitleColor(currentTheme.foregroundDim);
        border.setTitleFont(new Font("Segoe UI", Font.PLAIN, 12));
        panel.setBorder(BorderFactory.createCompoundBorder(
                border, new EmptyBorder(6, 8, 8, 8)));
        return panel;
    }

    private JPanel createSection(String title) {
        return createSection(title, 120);
    }
}
