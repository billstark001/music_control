package com.github.charlyb01.music_control.platform;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

public final class WorldCompat {
    private WorldCompat() {}

    public static RandomSource createThreadLocalRandom() {
        return RandomSource.createThreadLocalInstance();
    }

    public static long getDayTime(Level level) {
        return level.getDefaultClockTime();
    }
}
