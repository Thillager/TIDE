package config;

public class TIDEProperties {

	// ── Versionierung ──────────────────────────────────────────
	public static final String APP_VERSION = "5.2.0";

	// ── GitHub ─────────────────────────────────────────────────
	// Aktuell in MainWindow.java als GITHUB_REPO hardcoded
	public static final String GITHUB_REPO = "Thillager/TIDE";

	// ── UI-Defaults ────────────────────────────────────────────
	public static final int    WINDOW_WIDTH      = 1600;
	public static final int    WINDOW_HEIGHT     = 900;
	public static final int    WINDOW_MIN_WIDTH  = 1000;
	public static final int    WINDOW_MIN_HEIGHT = 700;
	public static final int    CONSOLE_HEIGHT    = 250;
	public static final int    FILETREE_WIDTH    = 250;
	public static final String EDITOR_FONT       = "Consolas";
	public static final int    EDITOR_FONT_SIZE  = 15;

	// ── Autocomplete ───────────────────────────────────────────
	// Aktuell in EditorManager.java als Magic Number
	public static final int AUTOCOMPLETE_DELAY   = 100;
	public static final int AUTOCOMPLETE_MIN_LEN = 2;

	// ── Update / Netzwerk ──────────────────────────────────────
	public static final int CONNECT_TIMEOUT_MS = 8_000;
	public static final int READ_TIMEOUT_MS    = 8_000;
	public static final int DOWNLOAD_TIMEOUT_MS = 60_000;

	// 
	public static final int CONSOLE_MAX_CHARS = 100_000;
	public static final int CONSOLE_TRIM_CHARS = 80_000;

	public static config.Theme CURRENT_THEME = config.Theme.byName(TIDEPreferences.getTheme());
}
