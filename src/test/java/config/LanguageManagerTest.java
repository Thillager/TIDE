package config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanguageManagerTest {

    @Test
    void switchesBetweenGermanAndEnglish() {
        LanguageManager.Language old = LanguageManager.get();
        try {
            LanguageManager.set(LanguageManager.Language.DE);
            assertEquals("Speichern", LanguageManager.t("save"));
            assertEquals("Oeffnen", LanguageManager.t("open"));

            LanguageManager.set(LanguageManager.Language.EN);
            assertEquals("Save", LanguageManager.t("save"));
            assertEquals("Open", LanguageManager.t("open"));
        } finally {
            LanguageManager.set(old);
        }
    }

    @Test
    void returnsKeyForUnknownTranslation() {
        LanguageManager.Language old = LanguageManager.get();
        try {
            LanguageManager.set(LanguageManager.Language.DE);
            assertEquals("does.not.exist", LanguageManager.t("does.not.exist"));
        } finally {
            LanguageManager.set(old);
        }
    }

    @Test
    void languageMetadataIsCorrect() {
        assertEquals("de", LanguageManager.Language.DE.code);
        assertEquals("Deutsch", LanguageManager.Language.DE.name);
        assertEquals("en", LanguageManager.Language.EN.code);
        assertEquals("English", LanguageManager.Language.EN.name);
    }
}
