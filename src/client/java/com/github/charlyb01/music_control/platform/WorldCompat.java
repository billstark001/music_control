package com.github.charlyb01.music_control.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

public final class WorldCompat {
    private WorldCompat() {}

    public static RandomSource createThreadLocalRandom() {
#if MC_VERSION < 260102
        return RandomSource.createNewThreadLocalInstance();
#else
        return RandomSource.createThreadLocalInstance();
#endif
    }

    public static BlockPos getCameraBlockPosition(Minecraft client) {
#if MC_VERSION < 260200
        return client.gameRenderer.getMainCamera().blockPosition();
#else
        return client.gameRenderer.mainCamera().blockPosition();
#endif
    }

    public static long getDayTime(Level level) {
#if MC_VERSION < 260102
        return level.getDayTime();
#else
        return level.getDefaultClockTime();
#endif
    }
}
