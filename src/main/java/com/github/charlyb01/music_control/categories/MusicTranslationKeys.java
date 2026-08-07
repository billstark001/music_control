package com.github.charlyb01.music_control.categories;

import net.minecraft.resources.Identifier;

public final class MusicTranslationKeys {
    private MusicTranslationKeys() {}

    public static String fromSound(final Identifier identifier) {
        final String path = identifier.getPath();
        if (path.startsWith("records/")) {
            return "jukebox_song." + identifier.getNamespace() + "."
                    + path.substring("records/".length()).replace('/', '.');
        }
        return identifier.toShortLanguageKey().replace('/', '.');
    }

    public static String fromDiscEvent(final Identifier identifier) {
        final String path = identifier.getPath();
        final String song = path.startsWith("music_disc.")
                ? path.substring("music_disc.".length())
                : path;
        return "jukebox_song." + identifier.getNamespace() + "." + song.replace('/', '.');
    }
}
