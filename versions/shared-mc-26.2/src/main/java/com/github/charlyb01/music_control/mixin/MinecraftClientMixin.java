package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.config.DimensionEventChance;
import com.github.charlyb01.music_control.config.ModConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(value = Minecraft.class, priority = 100)
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    // Intercept the BackgroundMusic.getCurrent call to modify biome music
    @WrapOperation(method = "getSituationalMusic", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BackgroundMusic;select(ZZ)Ljava/util/Optional;"))
    private Optional<Music> modifyBackgroundMusic(BackgroundMusic instance, boolean creative, boolean underwater,
            Operation<Optional<Music>> original) {
        Optional<Music> result = original.call(instance, creative, underwater);

        // If player is null, return original
        if (this.player == null) {
            return result;
        }

        // Check if we should modify creative music
        if (creative && ModConfig.get().general.event.creativeEventFallback) {
            Music customMusic = getMusicFromMap(result.orElse(null));
            if (customMusic != null && customMusic != result.orElse(null)) {
                return Optional.of(customMusic);
            }
        }

        // Check if we should modify biome music based on dimension
        Level world = this.player.level();
        if (world.dimension().equals(Level.END) &&
                ModConfig.get().general.event.dimensionEventChance.equals(DimensionEventChance.FALLBACK)) {
            Music customMusic = getMusicFromMap(result.orElse(null));
            if (customMusic != null && customMusic != result.orElse(null)) {
                return Optional.of(customMusic);
            }
        }

        // Apply custom biome music if available
        Holder<Biome> registryEntry = world.getBiome(this.player.blockPosition());
        ResourceKey<Biome> registryKey = registryEntry.unwrapKey().orElse(null);
        if (registryKey != null && SoundEventRegistry.BIOME_MUSIC_MAP.containsKey(registryKey)) {
            Music musicSound = Musics
                    .createGameMusic(Holder.direct(SoundEventRegistry.BIOME_MUSIC_MAP.get(registryKey)));
            return Optional.of(musicSound);
        }

        return result;
    }

    @Unique
    private Music getMusicFromMap(final Music original) {
        if (this.player == null)
            return original;

        Holder<Biome> registryEntry = this.player.level().getBiome(this.player.blockPosition());
        ResourceKey<Biome> registryKey = registryEntry.unwrapKey().orElse(null);
        if (registryKey == null || !SoundEventRegistry.BIOME_MUSIC_MAP.containsKey(registryKey))
            return original;

        return Musics.createGameMusic(Holder.direct(SoundEventRegistry.BIOME_MUSIC_MAP.get(registryKey)));
    }
}
