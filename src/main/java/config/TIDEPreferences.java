package config;

import java.util.prefs.Preferences;

public class TIDEPreferences {
	private static final Preferences prefs = Preferences.userNodeForPackage(TIDEPreferences.class);

	// Speichern
	public static void saveLastFolder(String path)   { prefs.put("lastFolder", path); }
	public static void saveLanguage(String lang)     { prefs.put("language", lang); }
	public static void saveMode(String mode)         { prefs.put("mode", mode); }
	public static void saveWindowWidth(int w)        { prefs.putInt("windowWidth", w); }
	public static void saveWindowHeight(int h)       { prefs.putInt("windowHeight", h); }
	public static void saveDividerH(int pos)         { prefs.putInt("dividerH", pos); }
	public static void saveDividerV(int pos)         { prefs.putInt("dividerV", pos); }

	// Laden (mit Standardwert falls noch nichts gespeichert)
	public static String getLastFolder()   { return prefs.get("lastFolder", null); }
	public static String getLanguage()     { return prefs.get("language", "DE"); }
	public static String getMode()         { return prefs.get("mode", "Java"); }
	public static int getWindowWidth()     { return prefs.getInt("windowWidth", 1600); }
	public static int getWindowHeight()    { return prefs.getInt("windowHeight", 900); }
	public static int getDividerH()        { return prefs.getInt("dividerH", -1); }
	public static int getDividerV()        { return prefs.getInt("dividerV", -1); }

	public static int    getEditorFontSize()          { return prefs.getInt("editorFontSize", TIDEProperties.EDITOR_FONT_SIZE); }
	public static void   saveEditorFontSize(int size) { prefs.putInt("editorFontSize", size); }

	public static int    getAutocompleteDelay()           { return prefs.getInt("autocompleteDelay", TIDEProperties.AUTOCOMPLETE_DELAY); }
	public static void   saveAutocompleteDelay(int delay) { prefs.putInt("autocompleteDelay", delay); }

	public static boolean getConsoleAutoScroll()             { return prefs.getBoolean("consoleAutoScroll", true); }
	public static void    saveConsoleAutoScroll(boolean val) { prefs.putBoolean("consoleAutoScroll", val); }

	public static void saveDividerHProportion(double value) {
		prefs.putDouble("dividerHProportion", value);
	}

	public static void saveDividerVProportion(double value) {
		prefs.putDouble("dividerVProportion", value);
	}

	public static double getDividerHProportion() {
		return prefs.getDouble("dividerHProportion", 0.20); // Dateibaum 20%
	}

	public static double getDividerVProportion() {
		return prefs.getDouble("dividerVProportion", 0.70); // Editor 70%, Terminal 30%
	}

	public static int getOutlineWidth() {
		return prefs.getInt("outlineWidth", 200);
	}

	public static void saveOutlineWidth(int width) {
		prefs.putInt("outlineWidth", width);
	}

	public static int getHotkey(String action, int defaultKey) {
		return prefs.getInt("hotkey_" + action, defaultKey);
	}
	public static void saveHotkey(String action, int keyCode) {
		prefs.putInt("hotkey_" + action, keyCode);
	}

	public static int getHotkeyModifier(String action, int defaultModifier) {
		return prefs.getInt("hotkey_mod_" + action, defaultModifier);
	}
	public static void saveHotkeyModifier(String action, int modifier) {
		prefs.putInt("hotkey_mod_" + action, modifier);
	}

	// ── Motion Blur ───────────────────────────────────────────
	public static boolean getMotionBlurEnabled() {
		return prefs.getBoolean("motionBlurEnabled", true);
	}
	public static void saveMotionBlurEnabled(boolean enabled) {
		prefs.putBoolean("motionBlurEnabled", enabled);
	}

	public static boolean getAuSt() {
		return prefs.getBoolean("AutoStop", true);
	}
	public static void saveAuSt(boolean enabled) {
		prefs.putBoolean("AutoStop", enabled);
	}

	// ── Scroll FPS Einstellungen ───────────────────────────────────────────
	public static int getScrollFPS() {
		return prefs.getInt("scrollFPS", 60);
	}

	public static void saveScrollFPS(int fps) {
		prefs.putInt("scrollFPS", fps);
	}

	// ── Scroll Geschwindigkeit ─────────────────────────────────────────────
	public static int getScrollSpeed() {
		return prefs.getInt("scrollSpeed", 100); 
	}

	public static void saveScrollSpeed(int speed) {
		prefs.putInt("scrollSpeed", speed);
	}

	// ── Hardwarebeschleunigung ─────────────────────────────────────────────
	public static String getHwAccelMode() {
		return prefs.get("hwAccelMode", "auto");
	}

	public static void saveHwAccelMode(String mode) {
		if (mode != null) {
			prefs.put("hwAccelMode", mode);
		}
	}

		public static String getTheme() {
		return prefs.get("theme", "dark");
	}

	public static void saveTheme(String theme) {
		prefs.put("theme", theme);
	}
	
	public static String getEditorThemePath() {
		return prefs.get("editorThemePath", "");
	}

	public static void saveEditorThemePath(String path) {
		prefs.put("editorThemePath", path);
	}

		public static String getFlatLafThemePath() {
		return prefs.get("flatLafThemePath", "");
	}

	public static void saveFlatLafThemePath(String path) {
		prefs.put("flatLafThemePath", path);
	}
}