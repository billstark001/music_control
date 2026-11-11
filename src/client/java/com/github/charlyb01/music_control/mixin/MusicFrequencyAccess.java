package com.github.charlyb01.music_control.mixin;

import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Calls Minecraft's own frequency policy from the mod-controlled tick loop. */
@Mixin(MusicManager.MusicFrequency.class)
public interface MusicFrequencyAccess {
    @Invoker("getNextSongDelay")
    int music_control$getNextSongDelay(Music music, RandomSource random);
}
