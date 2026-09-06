package config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class TIDEPreferencesTest {

    private final Preferences prefs =
            Preferences.userNodeForPackage(TIDEPreferences.class);

    private Map<String, String> originalPreferences;

    @BeforeEach
    void snapshotPreferences() throws Exception {
        originalPreferences = new LinkedHashMap<>();

        for (String key : prefs.keys()) {
            originalPreferences.put(key, prefs.get(key, null));
        }
    }

    @AfterEach
    void restorePreferences() throws Exception {
        // Remove every value that a test may have created or changed.
        prefs.clear();

        // Restore exactly the values that existed before the test.
        // Missing keys stay missing.
        for (Map.Entry<String, String> entry : originalPreferences.entrySet()) {
            prefs.put(entry.getKey(), entry.getValue());
        }

        prefs.flush();
    }

    @Test
    void storesAndLoadsBasicPreferences() {
        String suffix = "_test_" + System.nanoTime();

        TIDEPreferences.saveLastFolder("C:/TIDE/test-project" + suffix);
        TIDEPreferences.saveLanguage("EN");
        TIDEPreferences.saveMode("Java");

        assertEquals("C:/TIDE/test-project" + suffix,
                TIDEPreferences.getLastFolder());
        assertEquals("EN", TIDEPreferences.getLanguage());
        assertEquals("Java", TIDEPreferences.getMode());
    }

    @Test
    void storesAndLoadsNumericPreferences() {
        TIDEPreferences.saveWindowWidth(1234);
        TIDEPreferences.saveWindowHeight(777);
        TIDEPreferences.saveDividerH(321);
        TIDEPreferences.saveDividerV(654);
        TIDEPreferences.saveEditorFontSize(17);
        TIDEPreferences.saveAutocompleteDelay(250);
        TIDEPreferences.saveOutlineWidth(275);
        TIDEPreferences.saveScrollFPS(120);
        TIDEPreferences.saveScrollSpeed(150);

        assertEquals(1234, TIDEPreferences.getWindowWidth());
        assertEquals(777, TIDEPreferences.getWindowHeight());
        assertEquals(321, TIDEPreferences.getDividerH());
        assertEquals(654, TIDEPreferences.getDividerV());
        assertEquals(17, TIDEPreferences.getEditorFontSize());
        assertEquals(250, TIDEPreferences.getAutocompleteDelay());
        assertEquals(275, TIDEPreferences.getOutlineWidth());
        assertEquals(120, TIDEPreferences.getScrollFPS());
        assertEquals(150, TIDEPreferences.getScrollSpeed());
    }

    @Test
    void storesAndLoadsBooleanAndStringSettings() {
        TIDEPreferences.saveConsoleAutoScroll(false);
        TIDEPreferences.saveMotionBlurEnabled(false);
        TIDEPreferences.saveAuSt(false);
        TIDEPreferences.saveHwAccelMode("opengl");
        TIDEPreferences.saveTheme("Nord");
        TIDEPreferences.saveEditorThemePath("themes/custom.xml");
        TIDEPreferences.saveFlatLafThemePath("themes/custom.properties");

        assertFalse(TIDEPreferences.getConsoleAutoScroll());
        assertFalse(TIDEPreferences.getMotionBlurEnabled());
        assertFalse(TIDEPreferences.getAuSt());
        assertEquals("opengl", TIDEPreferences.getHwAccelMode());
        assertEquals("Nord", TIDEPreferences.getTheme());
        assertEquals("themes/custom.xml", TIDEPreferences.getEditorThemePath());
        assertEquals("themes/custom.properties",
                TIDEPreferences.getFlatLafThemePath());
    }

    @Test
    void storesAndLoadsDividerProportions() {
        TIDEPreferences.saveDividerHProportion(0.25);
        TIDEPreferences.saveDividerVProportion(0.70);

        assertEquals(0.25, TIDEPreferences.getDividerHProportion(), 0.000001);
        assertEquals(0.70, TIDEPreferences.getDividerVProportion(), 0.000001);
    }

    @Test
    void storesAndLoadsHotkeys() {
        String action = "unitTestAction_" + System.nanoTime();

        assertEquals(42, TIDEPreferences.getHotkey(action, 42));
        assertEquals(7, TIDEPreferences.getHotkeyModifier(action, 7));

        TIDEPreferences.saveHotkey(action, 65);
        TIDEPreferences.saveHotkeyModifier(action, 2);

        assertEquals(65, TIDEPreferences.getHotkey(action, 42));
        assertEquals(2, TIDEPreferences.getHotkeyModifier(action, 7));
    }

    @Test
    void completionModeHandlesNullAndUnknownValues() {
        TIDEPreferences.saveCompletionMode(null);
        assertEquals(CompletionMode.WORD_BASED, TIDEPreferences.getCompletionMode());

        TIDEPreferences.saveCompletionMode(CompletionMode.LSP);
        assertEquals(CompletionMode.LSP, TIDEPreferences.getCompletionMode());
    }
}
