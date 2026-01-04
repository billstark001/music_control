package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.config.DimensionEventChance;
import com.github.charlyb01.music_control.config.ModConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.MusicType;
import net.minecraft.world.World;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(value = MinecraftClient.class, priority = 100)
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    // Intercept the BackgroundMusic.getCurrent call to modify biome music
    @WrapOperation(method = "getMusicInstance", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BackgroundMusic;getCurrent(ZZ)Ljava/util/Optional;"))
    private Optional<MusicSound> modifyBackgroundMusic(BackgroundMusic instance, boolean creative, boolean underwater,
            Operation<Optional<MusicSound>> original) {
        Optional<MusicSound> result = original.call(instance, creative, underwater);

        // If player is null, return original
        if (this.player == null) {
            return result;
        }

        // Check if we should modify creative music
        if (creative && ModConfig.get().general.event.creativeEventFallback) {
            MusicSound customMusic = getMusicFromMap(result.orElse(null));
            if (customMusic != null && customMusic != result.orElse(null)) {
                return Optional.of(customMusic);
            }
        }

        // Check if we should modify biome music based on dimension
        World world = this.player.getEntityWorld();
        if (world.getRegistryKey().equals(World.END) &&
                ModConfig.get().general.event.dimensionEventChance.equals(DimensionEventChance.FALLBACK)) {
            MusicSound customMusic = getMusicFromMap(result.orElse(null));
            if (customMusic != null && customMusic != result.orElse(null)) {
                return Optional.of(customMusic);
            }
        }

        // Apply custom biome music if available
        RegistryEntry<Biome> registryEntry = world.getBiome(this.player.getBlockPos());
        RegistryKey<Biome> registryKey = registryEntry.getKey().orElse(null);
        if (registryKey != null && SoundEventRegistry.BIOME_MUSIC_MAP.containsKey(registryKey)) {
            MusicSound musicSound = MusicType
                    .createIngameMusic(RegistryEntry.of(SoundEventRegistry.BIOME_MUSIC_MAP.get(registryKey)));
            return Optional.of(musicSound);
        }

        return result;
    }

    @Unique
    private MusicSound getMusicFromMap(final MusicSound original) {
        if (this.player == null)
            return original;

        RegistryEntry<Biome> registryEntry = this.player.getEntityWorld().getBiome(this.player.getBlockPos());
        RegistryKey<Biome> registryKey = registryEntry.getKey().orElse(null);
        if (registryKey == null || !SoundEventRegistry.BIOME_MUSIC_MAP.containsKey(registryKey))
            return original;

        return MusicType.createIngameMusic(RegistryEntry.of(SoundEventRegistry.BIOME_MUSIC_MAP.get(registryKey)));
    }
}
