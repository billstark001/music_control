package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.categories.Music;

/** Priority rules between environmental graph policy and user-owned playback. */
public final class MusicPlaybackPolicy {
    private MusicPlaybackPolicy() {}

    public static boolean enforcesGraphSilence(
            MusicControlClient.State state,
            String category) {
        return state != MusicControlClient.State.CUSTOM
                && Music.DEFAULT_MUSICS.equals(category);
    }
}
