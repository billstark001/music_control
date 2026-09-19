package com.github.charlyb01.music_control.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BiomeSwitchBehaviorTest {
    private final Gson gson = new Gson();

    @Test
    void migratesLegacyBooleanValues() {
        assertEquals(BiomeSwitchBehavior.ALWAYS,
                gson.fromJson("true", BiomeSwitchBehavior.class));
        assertEquals(BiomeSwitchBehavior.NEVER,
                gson.fromJson("false", BiomeSwitchBehavior.class));

        GeneralConfig config = gson.fromJson(
                "{\"timer\":{\"changeMusicOnBiomeSwitch\":true}}",
                GeneralConfig.class);
        assertEquals(BiomeSwitchBehavior.ALWAYS,
                config.timer.changeMusicOnBiomeSwitch);
    }

    @Test
    void readsAndWritesCurrentEnumValues() {
        assertEquals(BiomeSwitchBehavior.IF_INCOMPATIBLE,
                gson.fromJson("\"IF_INCOMPATIBLE\"", BiomeSwitchBehavior.class));
        assertEquals(BiomeSwitchBehavior.IF_INCOMPATIBLE,
                gson.fromJson("\"if-incompatible\"", BiomeSwitchBehavior.class));
        assertEquals("\"IF_INCOMPATIBLE\"",
                gson.toJson(BiomeSwitchBehavior.IF_INCOMPATIBLE));
    }
}
