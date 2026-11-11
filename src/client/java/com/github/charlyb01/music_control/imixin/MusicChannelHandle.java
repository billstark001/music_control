package com.github.charlyb01.music_control.imixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.AudioStream;

public interface MusicChannelHandle {
    void music_control$playStream(AudioStream stream);
    void music_control$playBuffer(SoundBuffer buffer);
    void music_control$cancelStart();
}
