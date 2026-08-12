package ui;

import editor.CompilerErrorMarker;
import editor.EditorManager;
import git.GitManager;
import runner.ProjectRunner;
import runner.DebugRunner;
import update.UpdateManager;
import config.TIDEProperties;
import config.LanguageManager;
import config.TIDEPreferences;
import config.Theme;
import ui.SettingsDialog;

import com.formdev.flatlaf.ui.FlatLineBorder;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import thillager.gui.swing.ImageLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import java.util.Locale;
import javax.swing.border.*;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.InputEvent;

@SuppressWarnings({"serial", "this-escape"})
public class MainWindow extends JFrame {

	private static final String MODE_JAVA   = ProjectRunner.MODE_JAVA;
	private static final String MODE_PYTHON = ProjectRunner.MODE_PYTHON;
	private static final String MODE_C      = ProjectRunner.MODE_C;
	private static final String MODE_CPP    = ProjectRunner.MODE_CPP;
	private static final String MODE_BATCH  = ProjectRunner.MODE_BATCH;

	private static final String RUN_MODE_STANDARD = "Standard";
	private static final String RUN_MODE_DEBUG     = "Debug";

	// ---- State ----
	private File currentProjectFolder;
	private final Map<Component, File> openFiles = new HashMap<>();

	// ---- UI-Komponenten ----
	private JTabbedPane editorTabs;
	private JComboBox<String> modeSelector;
	private JComboBox<String> runModeSelector;
	private JTextField mainClassInput;
	private JButton btnTBuild;
	private JButton btnOpen;
	private JButton btnSave;
	private JButton btnTerminate;
	private JButton btnClear;
	private JButton btnAbout;
	private JButton btnRun;
	private JButton btnFormat;
	private JButton btnHotSwap;
	private JLabel modeLabel;
	private JLabel mainClassLabel;
	private SearchPanel searchPanel;
	private GlobalSearchPanel globalSearchPanel;

	// ---- Subsysteme ----
	private ConsolePanel consolePanel;
	private FileTreePanel fileTreePanel;
	private EditorManager editorManager;
	private CompilerErrorMarker errorMarker;
	private ProjectRunner projectRunner;
	private DebugRunner debugRunner;
	private GitManager gitManager;
	private UpdateManager updateManager;
	private AboutDialog aboutDialog;
	private WordManagerDialog wordManagerDialog;
	private SettingsDialog settingsDialog;

	private JSplitPane horizontalSplit;
	private JSplitPane verticalSplit;
	private JSplitPane editorOutlineSplit;
	private OutlinePanel outlinePanel;

	private Timer smoothScrollTimer;
	private double targetScrollY = 0;

	private boolean outlineInitialized = false;

	// ---- Aktives Theme ----
	// Wird einmal beim Start gesetzt und gilt für die gesamte Session.
	// Ein Neustart ist nötig um das Theme zu wechseln, weil FlatLaf und
	// RSyntaxTextArea beide tief in die Swing-Render-Pipeline eingreifen.
	public static final Theme THEME = Theme.byName(TIDEPreferences.getTheme());


	public MainWindow() {
		// FlatLaf-Theme anwenden (muss vor dem ersten Frame-Aufbau passieren)
		applyFlatLafTheme(THEME);

		LanguageManager.Language lang =
		LanguageManager.Language.valueOf(TIDEPreferences.getLanguage());

		Locale locale = lang == LanguageManager.Language.DE
		? Locale.GERMAN : Locale.ENGLISH;

		Locale.setDefault(locale);
		JComponent.setDefaultLocale(locale);

		setTitle("ThIDE v" + TIDEProperties.APP_VERSION);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		setSize(TIDEPreferences.getWindowWidth(), TIDEPreferences.getWindowHeight());
		setLocationRelativeTo(null);
		setExtendedState(JFrame.MAXIMIZED_BOTH);

		initSubsystems();
		initUI();


		SwingUtilities.invokeLater(() -> {
				horizontalSplit.setDividerLocation(TIDEPreferences.getDividerHProportion());
				verticalSplit.setDividerLocation(TIDEPreferences.getDividerVProportion());

				editorOutlineSplit.addComponentListener(new java.awt.event.ComponentAdapter() {
						private boolean done = false;

						@Override
						public void componentResized(java.awt.event.ComponentEvent e) {
							if (done) return;
							int w = editorOutlineSplit.getWidth();
							if (w > 0) {
								done = true;
								int savedWidth = TIDEPreferences.getOutlineWidth();
								int loc = w - savedWidth;
								if (loc > 0 && loc < w) {
									editorOutlineSplit.setDividerLocation(loc);
								}
								editorOutlineSplit.removeComponentListener(this);
							}
						}
					});
			});
	}

	// ── FlatLaf-Theme-Mapping ────────────────────────────────────────────────
	// Wählt anhand des Theme-Namens den passenden FlatLaf-LookAndFeel aus.
	// FlatMacDarkLaf/FlatMacLightLaf sind auf allen Plattformen nutzbar,
	// sie heißen nur "Mac" wegen des visuellen Stils.
	// ── FlatLaf-Theme-Mapping ────────────────────────────────────────────────
	// Wählt anhand des Theme-Namens den passenden FlatLaf-LookAndFeel aus.
	// FlatMacDarkLaf/FlatMacLightLaf sind auf allen Plattformen nutzbar,
	// sie heißen nur "Mac" wegen des visuellen Stils.
	public static void applyFlatLafTheme(Theme theme) {
		try {
			// Zuerst dem System sagen, dass FlatLaf die Fensterdekorationen übernehmen soll
			JFrame.setDefaultLookAndFeelDecorated(true);

			if (!loadCustomFlatLaf(theme)) {
				switch (theme.flatLafClass) {
					case "dark"      -> UIManager.setLookAndFeel(new FlatDarkLaf());
					case "light"     -> UIManager.setLookAndFeel(new FlatLightLaf());
					case "mac-dark"  -> UIManager.setLookAndFeel(new FlatMacDarkLaf());
					case "mac-light" -> UIManager.setLookAndFeel(new FlatMacLightLaf());
					default          -> UIManager.setLookAndFeel(new FlatDarkLaf());
				}
			}

			// ── NEU: Titelleisten-Styling (Anhand deines aktuellen Themes) ──
			// Nutzt ein passendes Grau/Hintergrundfarbe deines aktiven Themes
			if (theme.backgroundLight != null) UIManager.put("TitlePane.background",              theme.backgroundLight);
			if (theme.background != null) UIManager.put("TitlePane.inactiveBackground",      theme.background);
			if (theme.foreground != null) UIManager.put("TitlePane.foreground",              theme.foreground);
			if (theme.foregroundDim != null) UIManager.put("TitlePane.inactiveForeground",      theme.foregroundDim);
			// ── NEU: Titelleisten-Styling ──
			UIManager.put("TitlePane.unifiedBackground",       false); // <--- DAS HIER DEAKTIVIEREN!
			UIManager.put("TitlePane.background",              new Color(60, 63, 65)); // Hier dein festes Wunsch-Grau
			UIManager.put("TitlePane.inactiveBackground",      new Color(75, 78, 80));
			UIManager.put("TitlePane.foreground",              Color.WHITE);
			if (theme.foregroundDim != null) UIManager.put("TitlePane.inactiveForeground",      theme.foregroundDim);

			// FlatLaf UI-Tweaks
			//UIManager.put("Component.arc",                 8);
			UIManager.put("Button.arc",                    12);
			UIManager.put("TextComponent.arc",             8);
			UIManager.put("ScrollBar.thumbArc",            8);
			if (theme.backgroundLight != null) UIManager.put("TabbedPane.selectedBackground", theme.backgroundLight);
			UIManager.put("TabbedPane.showTabSeparators",  true);

			UIManager.put("ToolBar.paintButtons", true);
			UIManager.put("ToolBar.buttonBorderPainted", true);

			/*
			// Basis-Farben für alle Swing-Komponenten (nur wenn nicht null)
			if (theme.background != null) UIManager.put("Panel.background",              theme.background);
			if (theme.background != null) UIManager.put("ScrollPane.background",         theme.background);
			if (theme.background != null) UIManager.put("Tree.background",               theme.background);
			if (theme.background != null) UIManager.put("Tree.textBackground",           theme.background);
			if (theme.foreground != null) UIManager.put("Tree.textForeground",           theme.foreground);
			if (theme.background != null) UIManager.put("List.background",               theme.background);
			if (theme.background != null) UIManager.put("TextArea.background",           theme.background);
			if (theme.foreground != null) UIManager.put("TextArea.foreground",           theme.foreground);
			if (theme.backgroundLight != null) UIManager.put("TextField.background",          theme.backgroundLight);
			if (theme.foreground != null) UIManager.put("TextField.foreground",          theme.foreground);
			if (theme.backgroundLight != null) UIManager.put("ComboBox.background",           theme.backgroundLight);
			if (theme.foreground != null) UIManager.put("ComboBox.foreground",           theme.foreground);
			if (theme.background != null) UIManager.put("SplitPane.background",          theme.background);
			if (theme.toolbar != null) UIManager.put("ToolBar.background",            theme.toolbar);
			if (theme.foreground != null) UIManager.put("Label.foreground",              theme.foreground);

			*/

		} catch (Exception ex) {
			System.err.println("[ThIDE] FlatLaf-Theme konnte nicht geladen werden: " + ex.getMessage());
		}
	}

	// ── Custom FlatLaf-Theme laden (.properties) ────────────────────────────
	// Priorität 1: Vom Nutzer in den Einstellungen hinterlegter, freier Pfad
	//              zu einer eigenen .properties-Datei (z.B. FlatLaf Theme Editor).
	// Priorität 2: Vom Theme selbst mitgebrachte Ressource unter
	//              src/main/resources/<flatLafPropertiesResource>
	//              (z.B. "Fire" → resources/themes/Fire.properties).
	// Gibt true zurück, wenn ein Custom-Theme erfolgreich gesetzt wurde –
	// in diesem Fall wird das eingebaute FlatLaf-LnF-Mapping übersprungen.
	private static boolean loadCustomFlatLaf(Theme theme) {
		// 1. Nutzerdefinierter Pfad aus den Einstellungen (überstimmt alles andere)
		String customPath = TIDEPreferences.getFlatLafThemePath();
		if (customPath != null && !customPath.isBlank()) {
			File flatLafFile = new File(customPath);
			if (flatLafFile.exists()) {
				try {
					UIManager.setLookAndFeel(new FlatPropertiesLaf(theme.name, flatLafFile));
					return true;
				} catch (Exception ex) {
					System.err.println("[ThIDE] Custom FlatLaf-Theme-Datei konnte nicht geladen werden: "
						+ ex.getMessage());
				}
			} else {
				System.err.println("[ThIDE] Custom FlatLaf-Theme-Datei nicht gefunden: " + customPath);
			}
		}

		// 2. Vom Theme mitgebrachtes .properties-Theme (z.B. Fire.properties)
		if (theme.flatLafPropertiesResource != null && !theme.flatLafPropertiesResource.isBlank()) {
			try (java.io.InputStream is = MainWindow.class.getClassLoader()
				.getResourceAsStream(theme.flatLafPropertiesResource)) {
				if (is != null) {
					UIManager.setLookAndFeel(new FlatPropertiesLaf(theme.name, is));
					return true;
				} else {
					System.err.println("[ThIDE] FlatLaf-Theme-Ressource nicht gefunden: "
						+ theme.flatLafPropertiesResource);
				}
			} catch (Exception ex) {
				System.err.println("[ThIDE] FlatLaf-Theme-Ressource konnte nicht geladen werden: "
					+ ex.getMessage());
			}
		}

		return false;
	}

	private void initSubsystems() {
		consolePanel      = new ConsolePanel();
		editorTabs        = new JTabbedPane();
		wordManagerDialog = new WordManagerDialog(this, consolePanel);
		editorManager     = new EditorManager(this, editorTabs, openFiles, consolePanel, wordManagerDialog);
		errorMarker       = new CompilerErrorMarker(editorTabs, openFiles, consolePanel);
		projectRunner     = new ProjectRunner(consolePanel, editorManager, errorMarker);
		debugRunner       = new DebugRunner(consolePanel, editorManager, errorMarker, projectRunner);
		gitManager        = new GitManager(this, consolePanel);
		updateManager     = new UpdateManager(this, consolePanel, TIDEProperties.APP_VERSION, TIDEProperties.GITHUB_REPO);
		aboutDialog       = new AboutDialog(this, TIDEProperties.APP_VERSION, TIDEProperties.GITHUB_REPO, updateManager);
		fileTreePanel     = new FileTreePanel(this, consolePanel, file -> editorManager.openFileInEditor(file));

		gitManager.setOnRefreshFileTree(() -> { fileTreePanel.updateFileTree(currentProjectFolder); revalidate(); repaint(); });
		gitManager.setOnSaveCurrentFile(() -> editorManager.saveCurrentFile());
		projectRunner.setOnRefreshFileTree(() -> fileTreePanel.updateFileTree(currentProjectFolder));
		outlinePanel = new OutlinePanel();
		editorManager.setOutlinePanel(outlinePanel);

		settingsDialog = new SettingsDialog(this, editorManager, () -> {
				btnOpen.setText(LanguageManager.t("open"));
				btnSave.setText(LanguageManager.t("save"));
				btnClear.setText(LanguageManager.t("clear"));
				btnAbout.setText(LanguageManager.t("about"));
				modeLabel.setText(LanguageManager.t("mode"));
				mainClassLabel.setText(LanguageManager.t("main"));
				btnFormat.setText(LanguageManager.t("format"));
				revalidate();
				repaint();
			});
	}

	private void initUI() {
		Theme t = THEME; // Kurzreferenz für lesbareren Code

		searchPanel = new SearchPanel(editorTabs, consolePanel);
		editorTabs.setBorder(null);

		// ── Toolbar ──────────────────────────────────────────────────────────
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		toolBar.setBorder(new EmptyBorder(8, 10, 8, 10));


		// ---- Buttons ----
		btnOpen  = new JButton(LanguageManager.t("open"));
		btnSave  = new JButton(LanguageManager.t("save"));

		btnTerminate = new JButton("X");
		btnTerminate.setForeground(t.accentRed);
		btnTerminate.setFont(btnTerminate.getFont().deriveFont(Font.BOLD, 14f));
		btnTerminate.setVisible(false);

		modeSelector = new JComboBox<>(new String[]{MODE_JAVA, MODE_PYTHON, MODE_C, MODE_CPP, MODE_BATCH});
		modeSelector.setPreferredSize(new Dimension(90, 28));
		modeSelector.setMaximumSize(new Dimension(90, 28));

		mainClassLabel = new JLabel(" Main-Class: ");
		mainClassLabel.setForeground(t.foreground);
		mainClassInput = new JTextField("Main", 10);
		mainClassInput.setPreferredSize(new Dimension(130, 28));
		mainClassInput.setMaximumSize(new Dimension(130, 28));

		runModeSelector = new JComboBox<>(new String[]{RUN_MODE_STANDARD, RUN_MODE_DEBUG});
		runModeSelector.setPreferredSize(new Dimension(100, 28));
		runModeSelector.setMaximumSize(new Dimension(100, 28));
		runModeSelector.setToolTipText("Standard = normal ausführen | Debug = mit Debugger starten");

		btnRun = new JButton("▶");
		btnRun.setForeground(new Color(0,255, 0));
		btnRun.setFont(btnRun.getFont().deriveFont(Font.BOLD));

		btnFormat = new JButton(LanguageManager.t("format"));
		btnFormat.setForeground(t.accent);

		btnHotSwap = new JButton("HotSwap");
		btnHotSwap.setForeground(t.accent);
		btnHotSwap.setFont(btnHotSwap.getFont().deriveFont(Font.BOLD));
		btnHotSwap.setToolTipText("Klassen im laufenden Prozess ersetzen (kein Neustart)");
		btnHotSwap.setVisible(false);

		btnTBuild = new JButton("TBuild");
		btnClear  = new JButton(LanguageManager.t("clear"));
		btnAbout  = new JButton(LanguageManager.t("about"));

		btnTBuild.setForeground(new Color(100, 150, 255));
		btnAbout.setForeground(t.foregroundDim);

		JButton btnSettings = new JButton("⚙");
		btnSettings.addActionListener(e -> settingsDialog.show());

		Color defaultBorderColor = UIManager.getColor("Button.borderColor");
		if (defaultBorderColor == null) defaultBorderColor = UIManager.getColor("Component.borderColor");
		if (defaultBorderColor == null) defaultBorderColor = Color.GRAY;

		FlatLineBorder roundedOutline = new FlatLineBorder(new Insets(1, 1, 1, 1), defaultBorderColor, 1.5f, 12);
		Border btnPadding = BorderFactory.createEmptyBorder(5, 10, 5, 10);
		Border customRoundedBorder = BorderFactory.createCompoundBorder(roundedOutline, btnPadding);


		//Color btnBg    = t.backgroundLight;
		//Color btnHover = t.backgroundHover;

		/* JButton[] borderButtons = {btnFormat, btnOpen, btnSave, btnTBuild, btnAbout, btnSettings, btnHotSwap, btnClear};
		for (JButton btn : borderButtons) {
			btn.setBorder(customRoundedBorder);
			btn.setMargin(new Insets(5, 10, 5, 10));
			btn.putClientProperty("JButton.outline", "default");
			btn.putClientProperty("JButton.buttonType", "roundRect");
			//btn.setBackground(btnBg);
			//btn.setContentAreaFilled(true);
			//btn.setOpaque(true);
			//btn.addMouseListener(new MouseAdapter() {
					//		@Override public void mouseEntered(MouseEvent e) { btn.setBackground(btnHover); }
					//		@Override public void mouseExited(MouseEvent e)  { btn.setBackground(btnBg); }
					//	});
			// btn.putClientProperty("JButton.arc", 999);
		} */

		// Ersetze die bestehende Buttons-Schleife durch diese Version:
		JButton[] borderButtons = {
			btnFormat, btnOpen, btnSave, btnTBuild, btnAbout, btnSettings, btnHotSwap, btnClear,
			btnRun, btnTerminate
		};
		for (JButton btn : borderButtons) {
			// 1. Zwingt den Button, seine eigene Fläche voll zu zeichnen (verhindert Toolbar-Transparenz)
			btn.putClientProperty("JToolBar.isToolbarButton", Boolean.FALSE);
			btn.setContentAreaFilled(true);
			btn.setOpaque(true);

			// 2. Weist FlatLaf an, den Button als Standard-Button mit Abrundung zu behandeln
			btn.putClientProperty("JButton.buttonType", "default");

			// 3. Setzt Rahmen, Hover-Rahmen und die runde Hover-Farbe über das FlatLaf Style-System
			btn.putClientProperty("FlatLaf.style",
				"margin: 5,10,5,10; " +
				"borderWidth: 1.5; " +
				"borderColor: #888888; " +          // Standard-Randfarbe (anpassbar)
				"hoverBorderColor: #AAAAAA; " +     // Hover-Randfarbe (anpassbar)
				"focusedBorderColor: #FF4D4D; " +
				"hoverBackground: #3D2626; " +
				"focusWidth: 0"
			);

			// 4. Sichtbarer, abgerundeter Swing-Rand (nutzt customRoundedBorder aus weiter oben)
			btn.setBorder(customRoundedBorder);
			btn.setBorderPainted(true);
			btn.setMargin(new Insets(5, 10, 5, 10));
			btn.setFocusPainted(false);
		}

		// ── Git-Dropdown ─────────────────────────────────────────────────────
		JMenuBar gitMenuBar = new JMenuBar();
		gitMenuBar.setOpaque(false);
		gitMenuBar.setBorder(null);
		JMenu gitMenu = new JMenu("Git ▾");
		gitMenu.setForeground(t.accent);
		gitMenu.setFont(gitMenu.getFont().deriveFont(Font.BOLD));

		JMenuItem gitCommit = new JMenuItem("Commit");
		JMenuItem gitPush   = new JMenuItem("Push");
		JMenuItem gitPull   = new JMenuItem("Pull");
		gitMenu.add(gitCommit); gitMenu.add(gitPush); gitMenu.add(gitPull);
		gitCommit.addActionListener(e -> gitManager.gitCommit());
		gitPush.addActionListener(e   -> gitManager.gitPush());
		gitPull.addActionListener(e   -> gitManager.gitPull());
		gitMenuBar.add(gitMenu);

		// ── Sprach-Selector ──────────────────────────────────────────────────
		JComboBox<LanguageManager.Language> langSel = new JComboBox<>(LanguageManager.Language.values());
		langSel.setPreferredSize(new Dimension(95, 28));
		langSel.setMaximumSize(new Dimension(95, 28));
		langSel.setToolTipText("Language / Sprache");
		langSel.addActionListener(e -> {
				LanguageManager.Language selected = (LanguageManager.Language) langSel.getSelectedItem();
				LanguageManager.set(selected);
				TIDEPreferences.saveLanguage(selected.name());

				Locale neueLocale = selected.name().equalsIgnoreCase("DE") ? Locale.GERMAN : Locale.ENGLISH;
				Locale.setDefault(neueLocale);
				JComponent.setDefaultLocale(neueLocale);

				if (editorManager != null) editorManager.updateUIWithLocale(neueLocale);

				btnOpen.setText(LanguageManager.t("open"));
				btnSave.setText(LanguageManager.t("save"));
				btnClear.setText(LanguageManager.t("clear"));
				btnAbout.setText(LanguageManager.t("about"));
				modeLabel.setText(LanguageManager.t("mode"));
				mainClassLabel.setText(LanguageManager.t("main"));
				btnFormat.setText(LanguageManager.t("format"));

				revalidate();
				repaint();
			});

		// ── Toolbar befüllen ─────────────────────────────────────────────────
		toolBar.add(btnOpen);
		toolBar.add(Box.createHorizontalStrut(5));
		toolBar.add(btnSave);
		toolBar.addSeparator(new Dimension(20, 30));
		modeLabel = new JLabel(LanguageManager.t("mode"));
		modeLabel.setForeground(t.foreground);
		toolBar.add(modeLabel);
		toolBar.add(modeSelector);
		toolBar.add(Box.createHorizontalStrut(5));
		toolBar.add(mainClassLabel);
		toolBar.add(mainClassInput);
		toolBar.addSeparator(new Dimension(20, 30));
		toolBar.add(runModeSelector);
		toolBar.add(Box.createHorizontalStrut(5));
		toolBar.add(btnRun);
		toolBar.add(Box.createHorizontalStrut(5));
		toolBar.add(btnTerminate);
		toolBar.add(Box.createHorizontalStrut(5));
		toolBar.add(btnHotSwap);
		toolBar.add(Box.createHorizontalGlue());
		toolBar.add(btnFormat);
		toolBar.add(Box.createHorizontalStrut(5));
		toolBar.add(btnTBuild);
		toolBar.add(Box.createHorizontalStrut(5));
		toolBar.add(gitMenuBar);
		toolBar.add(Box.createHorizontalStrut(10));
		toolBar.add(btnClear);
		toolBar.add(Box.createHorizontalStrut(5));
		toolBar.add(btnAbout);
		toolBar.add(Box.createHorizontalStrut(5));
		toolBar.add(btnSettings);

		add(toolBar, BorderLayout.NORTH);

		// ── FileTree MouseListener ───────────────────────────────────────────
		fileTreePanel.getViewport().getView().addMouseListener(new MouseAdapter() {
				@Override public void mousePressed(MouseEvent me) {
					if (me.isPopupTrigger()) fileTreePanel.showFileTreePopup(me, currentProjectFolder, null);
				}
				@Override public void mouseReleased(MouseEvent me) {
					if (me.isPopupTrigger()) fileTreePanel.showFileTreePopup(me, currentProjectFolder, null);
				}
			});

		// ── Layout ──────────────────────────────────────────────────────────
		JPanel editorContainer = new JPanel(new BorderLayout());
		globalSearchPanel = new GlobalSearchPanel(editorManager, editorTabs);
		editorContainer.add(globalSearchPanel, BorderLayout.SOUTH);
		editorContainer.add(searchPanel, BorderLayout.NORTH);
		editorContainer.add(editorTabs,  BorderLayout.CENTER);

		verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorContainer, consolePanel);
		verticalSplit.setResizeWeight(0.7);
		verticalSplit.setDividerSize(4);
		verticalSplit.setBorder(null);

		editorOutlineSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, verticalSplit, outlinePanel);
		editorOutlineSplit.setResizeWeight(1.0);
		editorOutlineSplit.setDividerSize(4);
		editorOutlineSplit.setBorder(null);
		editorOutlineSplit.setDividerLocation(0.8);
		outlineInitialized = true;
		editorOutlineSplit.setOpaque(false);
		editorOutlineSplit.setBackground(new Color(0, 0, 0, 0));

		horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileTreePanel, editorOutlineSplit);
		horizontalSplit.setDividerSize(4);
		horizontalSplit.setBorder(null);

		add(horizontalSplit, BorderLayout.CENTER);

		// ── Event Listeners ──────────────────────────────────────────────────
		btnOpen.addActionListener(e  -> openFolderDialog());
		btnSave.addActionListener(e  -> editorManager.saveCurrentFile());
		btnClear.addActionListener(e -> consolePanel.clear());
		btnTBuild.addActionListener(e -> projectRunner.handleTBuild());
		btnFormat.addActionListener(e -> editorManager.formatCurrentFile());
		btnAbout.addActionListener(e  -> aboutDialog.show());

		btnTerminate.addActionListener(e -> {
				projectRunner.stopRunningProcess();
				debugRunner.stopDebugProcess();
			});

		btnRun.addActionListener(e -> {
				String mode      = (String) modeSelector.getSelectedItem();
				String mainClass = mainClassInput.getText().trim();
				String runMode   = (String) runModeSelector.getSelectedItem();
				if (RUN_MODE_DEBUG.equals(runMode)) {
					debugRunner.startDebug(mainClass);
				} else {
					projectRunner.runProject(mode, mainClass);
				}
			});

		btnHotSwap.addActionListener(e -> debugRunner.hotSwap());
		modeSelector.addActionListener(e -> updateDynamicUI());

		consolePanel.getTerminalInput().addKeyListener(new KeyAdapter() {
				@Override public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_ENTER) {
						String cmd = consolePanel.getTerminalInput().getText().trim();
						if (!cmd.isEmpty()) {
							projectRunner.executeCommand(cmd, false);
							consolePanel.getTerminalInput().setText("");
						}
					}
				}
			});

		// ── Globale Hotkeys ──────────────────────────────────────────────────
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
				if (e.getID() != KeyEvent.KEY_PRESSED) return false;

				int mod = 0;
				if (e.isControlDown()) mod |= InputEvent.CTRL_DOWN_MASK;
				if (e.isShiftDown())   mod |= InputEvent.SHIFT_DOWN_MASK;
				if (e.isAltDown())     mod |= InputEvent.ALT_DOWN_MASK;

				int key = e.getKeyCode();

				if (key == TIDEPreferences.getHotkey("save", KeyEvent.VK_S)
					&& mod == TIDEPreferences.getHotkeyModifier("save", InputEvent.CTRL_DOWN_MASK)) {
					editorManager.saveCurrentFile();
					return true;
				}
				if (key == TIDEPreferences.getHotkey("search", KeyEvent.VK_F)
					&& mod == TIDEPreferences.getHotkeyModifier("search", InputEvent.CTRL_DOWN_MASK)) {
					searchPanel.setVisible(!searchPanel.isVisible());
					if (searchPanel.isVisible()) searchPanel.getSearchField().requestFocusInWindow();
					revalidate();
					return true;
				}
				if (key == TIDEPreferences.getHotkey("gsearch", KeyEvent.VK_F)
					&& mod == TIDEPreferences.getHotkeyModifier("gsearch", InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) {
					globalSearchPanel.setVisible(!globalSearchPanel.isVisible());
					if (globalSearchPanel.isVisible()) globalSearchPanel.getSearchField().requestFocusInWindow();
					revalidate();
					return true;
				}
				if (key == TIDEPreferences.getHotkey("stop", KeyEvent.VK_X)
					&& mod == TIDEPreferences.getHotkeyModifier("stop", InputEvent.CTRL_DOWN_MASK)) {
					projectRunner.stopRunningProcess();
					debugRunner.stopDebugProcess();
					return true;
				}
				if (key == TIDEPreferences.getHotkey("run", KeyEvent.VK_R)
					&& mod == TIDEPreferences.getHotkeyModifier("run", InputEvent.CTRL_DOWN_MASK)) {
					runModeSelector.setSelectedItem(RUN_MODE_STANDARD);
					projectRunner.runProject((String) modeSelector.getSelectedItem(), mainClassInput.getText().trim());
					return true;
				}
				if (key == TIDEPreferences.getHotkey("debug", KeyEvent.VK_R)
					&& mod == TIDEPreferences.getHotkeyModifier("debug", InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) {
					runModeSelector.setSelectedItem(RUN_MODE_DEBUG);
					debugRunner.startDebug(mainClassInput.getText().trim());
					return true;
				}
				return false;
			});

		// ── windowClosing: alle Prefs auf einmal speichern ───────────────────
		addWindowListener(new WindowAdapter() {
				@Override public void windowClosing(WindowEvent e) {
					TIDEPreferences.saveWindowWidth(getWidth());
					TIDEPreferences.saveWindowHeight(getHeight());
					if (currentProjectFolder != null)
					TIDEPreferences.saveLastFolder(currentProjectFolder.getAbsolutePath());

					// Outline-Breite (absolut in Pixel)
					int total   = editorOutlineSplit.getWidth();
					int divider = editorOutlineSplit.getDividerLocation();
					if (total > 0 && divider > 0)
					TIDEPreferences.saveOutlineWidth(total - divider);

					// FileTree-Proportion
					if (horizontalSplit.getWidth() > 0)
					TIDEPreferences.saveDividerHProportion(
						horizontalSplit.getDividerLocation() / (double) horizontalSplit.getWidth());

					// Editor/Console-Proportion
					if (verticalSplit.getHeight() > 0)
					TIDEPreferences.saveDividerVProportion(
						verticalSplit.getDividerLocation() / (double) verticalSplit.getHeight());
				}
			});

		// ── Sprache & letzter Ordner ─────────────────────────────────────────
		LanguageManager.set(LanguageManager.Language.valueOf(TIDEPreferences.getLanguage()));
		langSel.setSelectedItem(LanguageManager.Language.valueOf(TIDEPreferences.getLanguage()));

		String lastFolder = TIDEPreferences.getLastFolder();
		if (lastFolder != null) {
			currentProjectFolder = new File(lastFolder);
			fileTreePanel.updateFileTree(currentProjectFolder);
			loadTXml(currentProjectFolder);
			projectRunner.setCurrentProjectFolder(currentProjectFolder);
			debugRunner.setCurrentProjectFolder(currentProjectFolder);
			gitManager.setCurrentProjectFolder(currentProjectFolder);
			globalSearchPanel.setSubsystems(currentProjectFolder);
		}

		modeSelector.setSelectedItem(TIDEPreferences.getMode());

		projectRunner.setTerminateButton(btnTerminate);
		debugRunner.setTerminateButton(btnTerminate);
		debugRunner.setHotSwapButton(btnHotSwap);

		updateDynamicUI();

		// ── Outline bei Tab-Wechsel ──────────────────────────────────────────
		editorTabs.addChangeListener(e -> {
				Component tab = editorTabs.getSelectedComponent();
				if (tab instanceof org.fife.ui.rtextarea.RTextScrollPane sp) {
					Component view = sp.getViewport().getView();
					if (view instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea textArea) {
						File f = openFiles.get(tab);
						outlinePanel.refresh(textArea, f != null ? f.getName() : null);
					}
				} else {
					outlinePanel.refresh(null, null);
				}
			});

		// ── Smooth Scrolling ─────────────────────────────────────────────────
		for (Component c : getAllComponents(this)) {
			if (c instanceof JScrollPane sp) {
				sp.getVerticalScrollBar().setUnitIncrement(16);
				sp.getHorizontalScrollBar().setUnitIncrement(16);
			}
		}
		for (Component c : getAllComponents(this)) {
			if (c instanceof JScrollPane sp) {
				enableSmoothScrolling(sp);
			}
		}

		Image windowImg = ImageLoader.holeBild("/app-image.png", Image.class);
		setIconImage(windowImg);
	}

	private java.util.List<Component> getAllComponents(Container container) {
		java.util.List<Component> list = new java.util.ArrayList<>();
		for (Component c : container.getComponents()) {
			list.add(c);
			if (c instanceof Container) list.addAll(getAllComponents((Container) c));
		}
		return list;
	}

	public Timer enableSmoothScrolling(JScrollPane scrollPane) {
		return enableSmoothScrolling(scrollPane, null, null);
	}

	public Timer enableSmoothScrolling(JScrollPane scrollPane,
		float[] sharedDynIntensity,
		int[]   sharedScrollDir) {
		JScrollBar bar = scrollPane.getVerticalScrollBar();

		double[] velocity     = { 0.0 };
		int[]    scrollDir    = sharedScrollDir    != null ? sharedScrollDir    : new int[]  { 0 };
		float[]  dynIntensity = sharedDynIntensity != null ? sharedDynIntensity : new float[]{ 0.0f };
		long[]   lastTick     = { System.nanoTime() };

		final double FRICTION_PER_SECOND = 0.000002;

		Timer timer = new Timer(7, null);

		timer.addActionListener(e -> {
				long now = System.nanoTime();
				double dt = (now - lastTick[0]) / 1_000_000_000.0;
				lastTick[0] = now;
				if (dt > 0.1) dt = 0.1;

				velocity[0] *= Math.pow(FRICTION_PER_SECOND, dt);

				if (Math.abs(velocity[0]) < 3.0) {
					velocity[0] = 0.0;
					dynIntensity[0] *= (float) Math.pow(0.001, dt);
					if (dynIntensity[0] < 0.01f) {
						dynIntensity[0] = 0.0f;
						timer.stop();
					}
					scrollPane.paintImmediately(0, 0, scrollPane.getWidth(), scrollPane.getHeight());
					return;
				}

				double current = bar.getValue();
				double nextPos = current + velocity[0] * dt * 60.0;

				int max = bar.getMaximum() - bar.getVisibleAmount();
				if (nextPos < 0)        { nextPos = 0;   velocity[0] = 0.0; }
				else if (nextPos > max) { nextPos = max;  velocity[0] = 0.0; }

				bar.setValue((int) Math.round(nextPos));

				if      (velocity[0] > 0.1)  scrollDir[0] = 1;
				else if (velocity[0] < -0.1) scrollDir[0] = -1;

				float targetIntensity = (float) Math.min(Math.abs(velocity[0]) / 20.0, 1.0);
				double riseRate = Math.pow(0.0001, dt * 60);
				double fallRate = Math.pow(0.05,   dt * 60);
				if (targetIntensity > dynIntensity[0])
				dynIntensity[0] += (float)((targetIntensity - dynIntensity[0]) * (1.0 - riseRate));
				else
				dynIntensity[0] += (float)((targetIntensity - dynIntensity[0]) * (1.0 - fallRate));

				scrollPane.repaint();
			});

		scrollPane.addMouseWheelListener(e -> {
				e.consume();

				int fps     = config.TIDEPreferences.getScrollFPS();
				int delayMs = Math.max(1, 1000 / fps);

				if (timer.getDelay() != delayMs) {
					timer.setDelay(delayMs);
					velocity[0] = 0.0;
					dynIntensity[0] = 0.0f;
					scrollDir[0] = 0;
					lastTick[0] = System.nanoTime();
				}

				lastTick[0] = System.nanoTime();

				int increment = bar.getUnitIncrement() > 0 ? bar.getUnitIncrement() : 16;
				double speedMultiplier = config.TIDEPreferences.getScrollSpeed() / 100.0;
				double rot  = e.getPreciseWheelRotation();
				double push = rot * increment * 4.0 * speedMultiplier;
				velocity[0] += push;

				double maxSpeed = 80.0 * speedMultiplier;
				if (velocity[0] >  maxSpeed) velocity[0] =  maxSpeed;
				if (velocity[0] < -maxSpeed) velocity[0] = -maxSpeed;

				if (!timer.isRunning()) timer.start();
			});

		bar.setUnitIncrement(16);
		return timer;
	}

	private void openFolderDialog() {
		JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			currentProjectFolder = chooser.getSelectedFile();
			fileTreePanel.updateFileTree(currentProjectFolder);
			consolePanel.log("[INFO] Ordner geöffnet: " + currentProjectFolder.getName() + "\n", Color.CYAN);
			loadTXml(currentProjectFolder);
			projectRunner.setCurrentProjectFolder(currentProjectFolder);
			debugRunner.setCurrentProjectFolder(currentProjectFolder);
			gitManager.setCurrentProjectFolder(currentProjectFolder);
			gitManager.checkGitStatusOnOpen();
			globalSearchPanel.setSubsystems(currentProjectFolder);
		}
	}

	private void updateDynamicUI() {
		String mode = (String) modeSelector.getSelectedItem();
		boolean isJava = MODE_JAVA.equals(mode);
		btnTBuild.setVisible(isJava);
		mainClassLabel.setVisible(isJava);
		mainClassInput.setVisible(isJava);
		revalidate();
		repaint();
	}

	private void loadTXml(File folder) {
		File txml = new File(folder, "T.xml");
		if (!txml.exists()) {
			consolePanel.log("[WARNUNG] Keine T.xml im Projektordner gefunden.\n", Color.ORANGE);
			setTitle("ThIDE v" + TIDEProperties.APP_VERSION + " - " + folder.getName());
			return;
		}
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
			dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			Document doc = dbf.newDocumentBuilder().parse(txml);
			String mainClass = getXmlTag(doc, "mainClass");
			String appName   = getXmlTag(doc, "appName");
			String version   = getXmlTag(doc, "version");
			if (mainClass != null && !mainClass.isEmpty()) mainClassInput.setText(mainClass);
			String titleAppName = (appName != null && !appName.isEmpty()) ? appName : folder.getName();
			String titleVersion = (version != null && !version.isEmpty()) ? version : "";
			setTitle("ThIDE v" + TIDEProperties.APP_VERSION + " - " + titleAppName
				+ (titleVersion.isEmpty() ? "" : " v" + titleVersion));
			consolePanel.log("[INFO] T.xml geladen — Main-Class: " + mainClass + " | App: " + titleAppName + "\n", Color.GREEN);
		} catch (Exception e) {
			consolePanel.log("[FEHLER] T.xml konnte nicht gelesen werden: " + e.getMessage() + "\n", Color.RED);
		}
	}

	private String getXmlTag(Document doc, String tagName) {
		NodeList nl = doc.getElementsByTagName(tagName);
		if (nl.getLength() > 0) {
			String val = nl.item(0).getTextContent();
			return val != null ? val.trim() : null;
		}
		return null;
	}
	// Helper: Color -> hex (füge irgendwo in der Klasse hinzu)
private static String colorToHex(Color c) {
    return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
}
}
