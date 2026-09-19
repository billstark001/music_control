package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.platform.WorldCompat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = Minecraft.class, priority = 100)
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;
    @Unique
    private boolean music_control$usedBackgroundSelection;

    @Inject(method = "getSituationalMusic", at = @At("HEAD"))
    private void resetMusicSelectionMode(CallbackInfoReturnable<Music> cir) {
        this.music_control$usedBackgroundSelection = false;
        SoundEventRegistry.beginSituationalSelection();
    }

    // Mark the environmental branch; the final return is captured below so later
    // getSituationalMusic modifiers remain authoritative inputs to the graph.
    @WrapOperation(method = "getSituationalMusic", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BackgroundMusic;select(ZZ)Ljava/util/Optional;"))
    private Optional<Music> captureBackgroundMusic(BackgroundMusic instance, boolean creative, boolean underwater,
            Operation<Optional<Music>> original) {
        this.music_control$usedBackgroundSelection = true;
        return original.call(instance, creative, underwater);
    }

    @ModifyReturnValue(method = "getSituationalMusic", at = @At("RETURN"))
    private Music applyGraphToFinalMusic(@Nullable Music original) {
        if (!this.music_control$usedBackgroundSelection || this.player == null) {
            SoundEventRegistry.captureVanillaMusic(original);
            return original;
        }

        Level world = this.player.level();
        Minecraft client = (Minecraft) (Object) this;
        var registryEntry = world.getBiome(WorldCompat.getCameraBlockPosition(client));
        ResourceKey<Biome> registryKey = registryEntry.unwrapKey().orElse(null);
        return SoundEventRegistry.resolveBiomeMusic(
                registryKey, this.player, world, Optional.ofNullable(original)).orElse(null);
    }
}
