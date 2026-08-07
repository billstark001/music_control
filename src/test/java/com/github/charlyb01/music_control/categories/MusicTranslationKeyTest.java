package com.github.charlyb01.music_control.categories;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicTranslationKeyTest {
    @Test
    void mapsVanillaBackgroundMusicToMinecraftTranslationKey() {
        assertEquals("music.game.creative.aria_math", MusicTranslationKeys.fromSound(
                Identifier.withDefaultNamespace("music/game/creative/aria_math")));
    }

    @Test
    void mapsVanillaRecordSoundToJukeboxSongTranslationKey() {
        assertEquals("jukebox_song.minecraft.cat", MusicTranslationKeys.fromSound(
                Identifier.withDefaultNamespace("records/cat")));
    }

    @Test
    void mapsVanillaDiscEventToJukeboxSongTranslationKey() {
        assertEquals("jukebox_song.minecraft.creator_music_box", MusicTranslationKeys.fromDiscEvent(
                Identifier.withDefaultNamespace("music_disc.creator_music_box")));
    }

    @Test
    void mapsCustomMusicToTheStandardNamespacedTranslationKey() {
        assertEquals("example.music.theme", MusicTranslationKeys.fromSound(
                Identifier.fromNamespaceAndPath("example", "music/theme")));
        assertEquals("jukebox_song.example.theme", MusicTranslationKeys.fromSound(
                Identifier.fromNamespaceAndPath("example", "records/theme")));
    }

    @Test
    void mapsMusicFilesOutsideConventionalDirectories() {
        assertEquals("example.ambient.theme", MusicTranslationKeys.fromSound(
                Identifier.fromNamespaceAndPath("example", "ambient/theme")));
    }
}
