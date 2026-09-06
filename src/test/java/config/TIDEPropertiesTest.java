package config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TIDEPropertiesTest {

    @Test
    void projectIdentityIsConfigured() {
        assertEquals("5.1.0", TIDEProperties.APP_VERSION);
        assertEquals("Thillager/TIDE", TIDEProperties.GITHUB_REPO);
    }

    @Test
    void uiDefaultsAreSane() {
        assertTrue(TIDEProperties.WINDOW_WIDTH >= TIDEProperties.WINDOW_MIN_WIDTH);
        assertTrue(TIDEProperties.WINDOW_HEIGHT >= TIDEProperties.WINDOW_MIN_HEIGHT);
        assertTrue(TIDEProperties.CONSOLE_HEIGHT > 0);
        assertTrue(TIDEProperties.FILETREE_WIDTH > 0);
        assertNotNull(TIDEProperties.EDITOR_FONT);
        assertTrue(TIDEProperties.EDITOR_FONT_SIZE > 0);
    }

    @Test
    void timingAndConsoleLimitsArePositive() {
        assertTrue(TIDEProperties.CONNECT_TIMEOUT_MS > 0);
        assertTrue(TIDEProperties.READ_TIMEOUT_MS > 0);
        assertTrue(TIDEProperties.DOWNLOAD_TIMEOUT_MS >= TIDEProperties.READ_TIMEOUT_MS);
        assertTrue(TIDEProperties.CONSOLE_MAX_CHARS > TIDEProperties.CONSOLE_TRIM_CHARS);
    }
}
