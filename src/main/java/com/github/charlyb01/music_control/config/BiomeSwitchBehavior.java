package com.github.charlyb01.music_control.config;

/** Determines whether an already-playing track survives a biome music-context change. */
public enum BiomeSwitchBehavior {
    ALWAYS("always"),
    IF_INCOMPATIBLE("if_incompatible"),
    NEVER("never");

    private final String key;

    BiomeSwitchBehavior(String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return "text.autoconfig.music_control.option.general.timer.changeMusicOnBiomeSwitch.value." + key;
    }
}
