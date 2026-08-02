package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.categories.MusicCategories;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelEventHandler.class)
public class WorldEventHandlerMixin {
    @WrapOperation(method = "playJukeboxSong", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setNowPlaying(Lnet/minecraft/network/chat/Component;)V"))
    private void useRightRecordName(Gui instance, Component description, Operation<Void> original,
                                    @Local(ordinal = 0) SoundInstance soundInstance) {
        Identifier soundId = soundInstance.getSound().getLocation();
        original.call(instance, Component.translatable(soundId.toString()));

        if (MusicControlClient.currentCategory.equals(Music.ALL_MUSICS)
                || MusicControlClient.currentCategory.equals(Music.ALL_MUSIC_DISCS)) {
            MusicCategories.PLAYED_MUSICS.add(soundId);
        }
    }
}
