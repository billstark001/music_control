package com.github.charlyb01.music_control.platform;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

public final class WorldCompat {
    private WorldCompat() {}

    public static RandomSource createThreadLocalRandom() {
        return RandomSource.createNewThreadLocalInstance();
    }

    public static long getDayTime(Level level) {
        return level.getDayTime();
    }
}
