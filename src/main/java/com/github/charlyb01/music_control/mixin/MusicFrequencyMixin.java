package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.config.ModConfig;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.sound.MusicSound;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MusicTracker.MusicFrequency.class)
public class MusicFrequencyMixin {

    @ModifyReturnValue(method = "getDelayBeforePlaying", at = @At("RETURN"))
    private int updateDelay(int original, @Nullable MusicSound music, Random random) {
        int delay = ModConfig.get().general.timer.maxDelay;
        if (delay <= 0)
            return original;

        // Check if this is CONSTANT frequency (returns 100 in the original code)
        MusicTracker.MusicFrequency self = (MusicTracker.MusicFrequency) (Object) this;
        return self == MusicTracker.MusicFrequency.CONSTANT
                ? delay * 20
                : random.nextBetween(delay * 10, delay * 20);
    }
}
