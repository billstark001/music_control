package com.github.charlyb01.music_control.gui;

import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.client.SoundEventRegistry;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicConfigDraftTest {
    private static final Identifier EVENT = Identifier.parse("test:music.event");
    private static final Identifier TRACK = Identifier.parse("test:music/track");

    @AfterEach
    void clearGlobalMusicState() {
        Music.MUSIC_BY_NAMESPACE.clear();
        Music.MUSIC_BY_EVENT.clear();
        Music.EVENTS_OF_EVENT.clear();
        SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.clear();
    }

    @Test
    void editsDoNotAffectPlaybackStateUntilApply() {
        Music music = new Music(TRACK);
        Music.MUSIC_BY_NAMESPACE.put(Music.ALL_MUSICS, new HashSet<>(Set.of(music)));
        Music.MUSIC_BY_EVENT.put(EVENT, new HashSet<>());
        music.addEvent(EVENT);
        MusicConfigDraft draft = new MusicConfigDraft();

        draft.removeSound(EVENT, TRACK);

        assertTrue(Music.MUSIC_BY_EVENT.get(EVENT).contains(music));
        assertFalse(SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.contains(EVENT));

        draft.apply();

        assertFalse(Music.MUSIC_BY_EVENT.get(EVENT).contains(music));
        assertTrue(SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.contains(EVENT));
    }
}
