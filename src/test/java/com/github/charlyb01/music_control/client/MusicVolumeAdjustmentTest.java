package com.github.charlyb01.music_control.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicVolumeAdjustmentTest {
    @Test
    void adjustsTheRawCategoryVolume() {
        assertEquals(0.80, MusicVolumeAdjustment.adjust(0.75, 5), 1.0E-9);
        assertEquals(0.70, MusicVolumeAdjustment.adjust(0.75, -5), 1.0E-9);
    }

    @Test
    void clampsToVanillaVolumeBounds() {
        assertEquals(1.0, MusicVolumeAdjustment.adjust(0.98, 5), 1.0E-9);
        assertEquals(0.0, MusicVolumeAdjustment.adjust(0.02, -5), 1.0E-9);
    }

    @Test
    void repeatedIncreasesReachFullVolume() {
        double volume = 0.20;
        for (int press = 0; press < 16; press++) {
            volume = MusicVolumeAdjustment.adjust(volume, 5);
        }
        assertEquals(1.0, volume, 1.0E-9);
    }
}
