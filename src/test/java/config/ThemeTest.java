package config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {

    @Test
    void allBuiltInThemesArePresent() {
        assertNotNull(Theme.ALL);
        assertTrue(Theme.ALL.length >= 6);

        for (Theme theme : Theme.ALL) {
            assertNotNull(theme);
            assertNotNull(theme.name);
            assertFalse(theme.name.isBlank());
            assertNotNull(theme.flatLafClass);
            assertNotNull(theme.syntaxTheme);
        }
    }

    @Test
    void byNameFindsBuiltInThemes() {
        for (Theme theme : Theme.ALL) {
            assertSame(theme, Theme.byName(theme.name));
        }
    }

    @Test
    void unknownAndNullThemeFallBackToDark() {
        assertSame(Theme.DARK, Theme.byName("does-not-exist"));
        assertSame(Theme.DARK, Theme.byName(null));
    }

    @Test
    void darkAndLightThemesHaveExpectedCoreValues() {
        assertEquals("Dark", Theme.DARK.name);
        assertEquals("dark", Theme.DARK.flatLafClass);
        assertEquals("dark", Theme.DARK.syntaxTheme);

        assertEquals("Light", Theme.LIGHT.name);
        assertEquals("light", Theme.LIGHT.flatLafClass);
        assertEquals("idea", Theme.LIGHT.syntaxTheme);
    }
}
