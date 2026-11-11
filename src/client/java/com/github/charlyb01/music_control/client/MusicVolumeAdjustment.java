package com.github.charlyb01.music_control.client;

public final class MusicVolumeAdjustment {
    private MusicVolumeAdjustment() {}

    public static double adjust(double categoryVolume, int incrementPercent) {
        return Math.max(0.0, Math.min(1.0, categoryVolume + incrementPercent / 100.0));
    }
}
