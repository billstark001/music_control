package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.categories.MusicCategories;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.imixin.MusicChannel;
import com.github.charlyb01.music_control.imixin.MusicChannelHandle;
import com.github.charlyb01.music_control.imixin.PauseResumeIMixin;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Mixin(SoundEngine.class)
public abstract class SoundSystemMixin implements PauseResumeIMixin {
    @Shadow private boolean loaded;
    @Shadow @Final private ChannelAccess channelAccess;
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow @Final private Multimap<SoundSource, SoundInstance> instanceBySource;

    @Inject(method = "reload", at = @At("TAIL"))
    private void reinitializeMusicCategories(CallbackInfo ci) {
        MusicCategories.init(Minecraft.getInstance());
    }

    @WrapOperation(
            method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private void music_control$stopMusicChannel(ChannelAccess.ChannelHandle handle, Consumer<Channel> stop,
                                               Operation<Void> original, @Local(argsOnly = true) SoundInstance sound) {
        if (sound.getSource() == SoundSource.MUSIC) {
            music_control$releaseMusicChannel(handle);
        } else {
            original.call(handle, stop);
        }
    }

    @WrapOperation(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private void music_control$configureMusicChannel(ChannelAccess.ChannelHandle handle, Consumer<Channel> configure,
                                                      Operation<Void> original, @Local(argsOnly = true) SoundInstance sound) {
        if (sound.getSource() != SoundSource.MUSIC) {
            original.call(handle, configure);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean paused = MusicControlClient.isPaused && minecraft != null && minecraft.level != null;
        original.call(handle, (Consumer<Channel>) channel -> {
            ((MusicChannel) (Object) channel).music_control$configureMusic(paused);
            configure.accept(channel);
        });
    }

    @WrapOperation(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"),
            require = 2, allow = 2)
    private <T> CompletableFuture<Void> music_control$submitMusic(
            CompletableFuture<T> loading, Consumer<T> start, Operation<CompletableFuture<Void>> original,
            @Local(argsOnly = true) SoundInstance sound, @Local ChannelAccess.ChannelHandle handle) {
        if (sound.getSource() != SoundSource.MUSIC) return original.call(loading, start);

        MusicChannelHandle music = (MusicChannelHandle) handle;
        Consumer<T> startMusic = audio -> {
            if (audio instanceof AudioStream stream) {
                music.music_control$playStream(stream);
            } else {
                music.music_control$playBuffer((SoundBuffer) audio);
            }
        };
        return original.call(loading, startMusic).whenComplete((ignored, failure) -> {
            if (failure != null) music_control$releaseMusicChannel(handle);
        });
    }

    @Unique
    private void music_control$releaseMusicChannel(ChannelAccess.ChannelHandle handle) {
        ((MusicChannelHandle) handle).music_control$cancelStart();
        var access = (ChannelAccessAccessor) channelAccess;
        // A native stop on an unstarted source has no effect. Releasing the handle
        // invalidates any queued start and frees the source before the replacement plays.
        access.music_control$getExecutor().execute(() -> {
            if (access.music_control$getChannels().remove(handle)) handle.release();
        });
    }

    @Override
    public void music_control$pauseMusic() {
        music_control$setUserPaused(true);
    }

    @Override
    public void music_control$resumeMusic() {
        music_control$setUserPaused(false);
    }

    @Unique
    private void music_control$setUserPaused(boolean paused) {
        if (!this.loaded) return;
        this.instanceBySource.get(SoundSource.MUSIC).forEach(soundInstance -> {
            ChannelAccess.ChannelHandle handle = this.instanceToChannel.get(soundInstance);
            if (handle != null) {
                handle.execute(channel -> ((MusicChannel) (Object) channel).music_control$setUserPaused(paused));
            }
        });
    }
}
