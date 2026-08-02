package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.imixin.GameOptionsAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;

@Mixin(Options.class)
public class GameOptionsMixin implements GameOptionsAccess {
    @Shadow @Final private Map<SoundSource, OptionInstance<Double>> soundSourceVolumes;

    @Override
    public void music_control$setSoundCategoryVolume(final SoundSource soundCategory, final double volume) {
        OptionInstance<Double> simpleOption = this.soundSourceVolumes.get(soundCategory);
        if (simpleOption != null) {
            simpleOption.set(volume);
        }
    }
}
