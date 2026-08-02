package com.github.charlyb01.music_control.imixin;

import net.minecraft.sounds.SoundSource;

public interface GameOptionsAccess {
    void music_control$setSoundCategoryVolume(SoundSource soundCategory, double volume);
}
