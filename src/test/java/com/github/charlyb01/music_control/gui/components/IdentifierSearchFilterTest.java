package com.github.charlyb01.music_control.gui.components;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentifierSearchFilterTest {
    private static final Identifier VALUE = Identifier.parse("minecraft:music.deep_dark");

    @Test
    void supportsIdentifierFieldsAndNegation() {
        assertTrue(IdentifierSearchFilter.matches("@minecraft #deep", VALUE, true));
        assertFalse(IdentifierSearchFilter.matches("@minecraft !#deep", VALUE, true));
        assertTrue(IdentifierSearchFilter.matches("!@example", VALUE, true));
    }

    @Test
    void supportsOrAndTemporaryOperatorInversion() {
        assertTrue(IdentifierSearchFilter.matches("@example #deep", VALUE, false));
        assertFalse(IdentifierSearchFilter.matches("@example #deep", VALUE, true));
        assertTrue(IdentifierSearchFilter.matches("& @example #deep", VALUE, true));
        assertFalse(IdentifierSearchFilter.matches("& @example #missing", VALUE, false));
    }

    @Test
    void blankQueriesMatchEverything() {
        assertTrue(IdentifierSearchFilter.matches("", VALUE, true));
        assertTrue(IdentifierSearchFilter.matches("   ", VALUE, false));
    }
}
