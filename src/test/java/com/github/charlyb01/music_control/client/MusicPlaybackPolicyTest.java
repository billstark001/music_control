package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.categories.Music;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicPlaybackPolicyTest {
    @Test
    void graphSilenceOnlyOwnsDefaultEnvironmentalPlayback() {
        assertTrue(MusicPlaybackPolicy.enforcesGraphSilence(
                MusicControlClient.State.MINECRAFT, Music.DEFAULT_MUSICS));
        assertFalse(MusicPlaybackPolicy.enforcesGraphSilence(
                MusicControlClient.State.CUSTOM, Music.DEFAULT_MUSICS));
        assertFalse(MusicPlaybackPolicy.enforcesGraphSilence(
                MusicControlClient.State.MINECRAFT, "example_playlist"));
    }
}
