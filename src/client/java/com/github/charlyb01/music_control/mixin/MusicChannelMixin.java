package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.client.PendingMusicStream;
import com.github.charlyb01.music_control.imixin.MusicChannel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class MusicChannelMixin implements MusicChannel {
    @Shadow public abstract void play();
    @Shadow public abstract void pause();
    @Shadow public abstract void unpause();

    // All channel state belongs to the audio thread.
    @Unique private boolean music_control$music;
    @Unique private boolean music_control$userPaused;
    @Unique private boolean music_control$gamePaused;
    @Unique private boolean music_control$updatingUserPause;
    @Unique private boolean music_control$startDeferred;

    @Override
    public void music_control$configureMusic(boolean paused) {
        music_control$music = true;
        music_control$userPaused = paused;
    }

    @Override
    public void music_control$setUserPaused(boolean paused) {
        music_control$userPaused = paused;
        music_control$updatingUserPause = true;
        try {
            if (paused) pause();
            else unpause();
        } finally {
            music_control$updatingUserPause = false;
        }
    }

    @Inject(method = "pause", at = @At("HEAD"))
    private void music_control$recordGamePause(CallbackInfo ci) {
        if (music_control$music && !music_control$updatingUserPause) music_control$gamePaused = true;
    }

    @Inject(method = "unpause", at = @At("HEAD"), cancellable = true)
    private void music_control$resumeWhenUnpaused(CallbackInfo ci) {
        if (!music_control$music) return;
        if (!music_control$updatingUserPause) music_control$gamePaused = false;
        if (music_control$userPaused || music_control$gamePaused) {
            ci.cancel();
        } else if (music_control$startDeferred) {
            music_control$startDeferred = false;
            play();
            ci.cancel();
        }
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void music_control$deferPausedStart(CallbackInfo ci) {
        if (music_control$music && (music_control$userPaused || music_control$gamePaused)) {
            music_control$startDeferred = true;
            ci.cancel();
        }
    }

    @WrapOperation(method = "destroy", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/AudioStream;close()V"))
    private void music_control$closeStream(AudioStream stream, Operation<Void> original) {
        if (music_control$music) PendingMusicStream.closeAsync(stream);
        else original.call(stream);
    }
}
