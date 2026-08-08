package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.Music;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = Minecraft.class, priority = 100)
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "getSituationalMusic", at = @At("HEAD"))
    private void resetMusicSelectionMode(CallbackInfoReturnable<Music> cir) {
        SoundEventRegistry.beginSituationalSelection();
    }

    // Intercept the BackgroundMusic.getCurrent call to modify biome music
    @WrapOperation(method = "getSituationalMusic", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BackgroundMusic;select(ZZ)Ljava/util/Optional;"))
    private Optional<Music> modifyBackgroundMusic(BackgroundMusic instance, boolean creative, boolean underwater,
            Operation<Optional<Music>> original) {
        Optional<Music> result = original.call(instance, creative, underwater);

        // If player is null, return original
        if (this.player == null) {
            return result;
        }

        Level world = this.player.level();
        var registryEntry = world.getBiome(this.player.blockPosition());
        ResourceKey<Biome> registryKey = registryEntry.unwrapKey().orElse(null);
        return SoundEventRegistry.resolveBiomeMusic(registryKey, this.player, world, result);
    }
}
