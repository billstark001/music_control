package com.github.charlyb01.music_control.categories;

import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.config.BiomeSwitchBehavior;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicIdentifierTest {
    private static final Identifier OLD_A = Identifier.parse("test:old_a");
    private static final Identifier OLD_B = Identifier.parse("test:old_b");
    private static final Identifier CURRENT_EVENT = Identifier.parse("test:current_event");
    private static final Identifier NEXT_EVENT = Identifier.parse("test:next_event");
    private static final Identifier SHARED_TRACK = Identifier.parse("test:shared_track");

    @AfterEach
    void resetState() {
        MusicCategories.PLAYED_MUSICS.clear();
        MusicCategories.RECENT_MUSICS.clear();
        Music.MUSIC_BY_NAMESPACE.clear();
        Music.MUSIC_BY_EVENT.clear();
        Music.EVENTS_OF_EVENT.clear();
        MusicControlClient.currentEvent = null;
        MusicControlClient.currentMusic = null;
        MusicControlClient.isCurrentEventEmpty = false;
        SoundEventRegistry.beginSituationalSelection();
    }

    @Test
    void oneTrackPoolTrimsOnlyTheNoRepeatWindowAndPreservesVisibleHistory() {
        Music only = new Music(Identifier.parse("test:only"));
        MusicCategories.PLAYED_MUSICS.addAll(List.of(OLD_A, OLD_B));
        MusicCategories.RECENT_MUSICS.addAll(List.of(OLD_A, OLD_B));

        Identifier selected = MusicIdentifier.getFromList(
                new HashSet<>(Set.of(only)), RandomSource.create(42), 10);

        assertEquals(only.getIdentifier(), selected);
        assertEquals(List.of(OLD_A, OLD_B, only.getIdentifier()),
                MusicCategories.PLAYED_MUSICS);
        assertEquals(List.of(only.getIdentifier()), MusicCategories.RECENT_MUSICS);

        MusicCategories.recordHistory(OLD_A);
        assertEquals(List.of(OLD_B, only.getIdentifier(), OLD_A),
                MusicCategories.PLAYED_MUSICS);
    }

    @Test
    void conditionalBiomeSwitchKeepsATrackPresentInTheResolvedNextPool() {
        Music shared = new Music(SHARED_TRACK);
        Music.MUSIC_BY_NAMESPACE.put(Music.ALL_MUSICS, new HashSet<>(Set.of(shared)));
        Music.MUSIC_BY_EVENT.put(NEXT_EVENT, new HashSet<>(Set.of(shared)));
        MusicControlClient.currentEvent = CURRENT_EVENT;
        MusicControlClient.currentMusic = SHARED_TRACK;

        assertFalse(MusicIdentifier.shouldChangeMusic(
                BiomeSwitchBehavior.IF_INCOMPATIBLE,
                NEXT_EVENT,
                true,
                RandomSource.create(42)));
        assertTrue(MusicIdentifier.shouldChangeMusic(
                BiomeSwitchBehavior.ALWAYS,
                NEXT_EVENT,
                true,
                RandomSource.create(42)));
        assertFalse(MusicIdentifier.shouldChangeMusic(
                BiomeSwitchBehavior.NEVER,
                NEXT_EVENT,
                true,
                RandomSource.create(42)));
        assertFalse(MusicIdentifier.shouldChangeMusic(
                BiomeSwitchBehavior.ALWAYS,
                NEXT_EVENT,
                false,
                RandomSource.create(42)));
    }
}
