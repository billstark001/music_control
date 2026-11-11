package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.client.PendingMusicStream;
import com.github.charlyb01.music_control.imixin.MusicChannelHandle;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ChannelAccess.ChannelHandle.class)
public abstract class MusicChannelHandleMixin implements MusicChannelHandle {
    @Shadow public abstract void execute(Consumer<Channel> action);

    @Unique private final PendingMusicStream music_control$pendingStream = new PendingMusicStream();

    @Override
    public void music_control$playStream(AudioStream stream) {
        if (!music_control$pendingStream.offer(stream)) return;
        execute(channel -> {
            AudioStream pending = music_control$pendingStream.take();
            if (pending == null) return;
            channel.attachBufferStream(pending);
            channel.play();
        });
    }

    @Override
    public void music_control$playBuffer(SoundBuffer buffer) {
        execute(channel -> {
            if (music_control$pendingStream.isReleased()) return;
            channel.attachStaticBuffer(buffer);
            channel.play();
        });
    }

    @Override
    public void music_control$cancelStart() {
        music_control$pendingStream.release();
    }

    @Inject(method = "release", at = @At("HEAD"))
    private void music_control$discardPendingStream(CallbackInfo ci) {
        // SoundEngine.stopAll() can drop the queued start before releasing the handle.
        music_control$cancelStart();
    }
}
