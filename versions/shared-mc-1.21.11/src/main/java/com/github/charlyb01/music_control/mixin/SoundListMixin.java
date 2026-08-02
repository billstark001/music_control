package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.categories.Music;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundEventRegistration;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;

@Mixin(SoundManager.Preparations.class)
public class SoundListMixin {
    @Shadow @Final
    Map<Identifier, WeighedSoundEvents> registry;

    @Inject(method = "handleRegistration", at = @At("TAIL"))
    private void addEverySound(Identifier id, SoundEventRegistration entry, CallbackInfo ci, @Local(ordinal = 0) ResourceProvider resourceFactory) {
        if (entry.isReplace()) {
            Music.EVENTS_OF_EVENT.remove(id);
        }
        for (Sound sound : entry.getSounds()) {
            final Identifier identifier = sound.getLocation();
            if (!identifier.getPath().contains("music") && !identifier.getPath().contains("records")) continue;

            switch (sound.getType()) {
                case FILE -> {
                    if (SoundManager.validateSoundResource(sound, id, resourceFactory)) {
                        WeighedSoundEvents newWeightedSoundSet = new WeighedSoundEvents(id, entry.getSubtitle());

                        this.registry.put(identifier, newWeightedSoundSet);
                        newWeightedSoundSet.addSound(sound);
                    }
                }
                case SOUND_EVENT -> {
                    if (!Music.EVENTS_OF_EVENT.containsKey(id)) {
                        Music.EVENTS_OF_EVENT.put(id, new HashSet<>());
                    }
                    Music.EVENTS_OF_EVENT.get(id).add(identifier);
                }
            }
        }
    }
}
