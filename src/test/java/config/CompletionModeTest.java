package config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompletionModeTest {

    @Test
    void parsesValidValues() {
        assertEquals(CompletionMode.WORD_BASED, CompletionMode.fromString("WORD_BASED"));
        assertEquals(CompletionMode.LSP, CompletionMode.fromString("LSP"));
    }

    @Test
    void invalidAndNullValuesFallBackToWordBased() {
        assertEquals(CompletionMode.WORD_BASED, CompletionMode.fromString(null));
        assertEquals(CompletionMode.WORD_BASED, CompletionMode.fromString("invalid"));
        assertEquals(CompletionMode.WORD_BASED, CompletionMode.fromString("lsp"));
    }
}
