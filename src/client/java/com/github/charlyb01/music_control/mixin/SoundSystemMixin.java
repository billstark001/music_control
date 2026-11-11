package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.categories.MusicCategories;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.imixin.PauseResumeIMixin;
import com.google.common.collect.Multimap;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(SoundEngine.class)
public abstract class SoundSystemMixin implements PauseResumeIMixin {
    @Shadow private boolean loaded;
    @Shadow @Final private Options options;
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow @Final private Multimap<SoundSource, SoundInstance> instanceBySource;

    @Inject(method = "reload", at = @At("TAIL"))
    private void reinitializeMusicCategories(CallbackInfo ci) {
        MusicCategories.init(Minecraft.getInstance());
    }

    @Inject(method = "tickInGameSound()V", at = @At("HEAD"))
    private void delayIfNoSound(CallbackInfo ci) {
        if (this.options.getFinalSoundSourceVolume(SoundSource.MASTER) <= 0.0F
                || this.options.getFinalSoundSourceVolume(SoundSource.MUSIC) <= 0.0F) {
            MusicControlClient.shouldPlay = false;
        }
    }

    @Override
    public void music_control$pauseMusic() {
        if (!this.loaded) {
            return;
        }

        this.instanceBySource.get(SoundSource.MUSIC).forEach(soundInstance -> {
            ChannelAccess.ChannelHandle sourceManager = this.instanceToChannel.get(soundInstance);
            if (sourceManager != null) {
                sourceManager.execute(Channel::pause);
            }
        });
    }

    @Override
    public void music_control$resumeMusic() {
        if (!this.loaded) {
            return;
        }

        this.instanceBySource.get(SoundSource.MUSIC).forEach(soundInstance -> {
            ChannelAccess.ChannelHandle sourceManager = this.instanceToChannel.get(soundInstance);
            if (sourceManager != null) {
                sourceManager.execute(Channel::unpause);
            }
        });
    }
}
