package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.config.ModConfig;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MusicManager.MusicFrequency.class)
public class MusicFrequencyMixin {

    @ModifyReturnValue(method = "getNextSongDelay", at = @At("RETURN"))
    private int updateDelay(int original, @Nullable Music music, RandomSource random) {
        int delay = ModConfig.get().general.timer.maxDelay;
        if (delay <= 0)
            return original;

        // Check if this is CONSTANT frequency (returns 100 in the original code)
        MusicManager.MusicFrequency self = (MusicManager.MusicFrequency) (Object) this;
        return self == MusicManager.MusicFrequency.CONSTANT
                ? delay * 20
                : random.nextIntBetweenInclusive(delay * 10, delay * 20);
    }
}
