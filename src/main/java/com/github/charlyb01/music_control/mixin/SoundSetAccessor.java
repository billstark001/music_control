package com.github.charlyb01.music_control.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;

@Mixin(WeighedSoundEvents.class)
public interface SoundSetAccessor {
    @Accessor
    List<Weighted<Sound>> getList();
}
