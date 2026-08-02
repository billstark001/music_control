package com.github.charlyb01.music_control.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.github.charlyb01.music_control.client.SoundEventRegistry.NAME_BIOME_MAP;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

@Mixin(Biomes.class)
public class BiomeKeysMixin {
    @Inject(method = "register", at = @At("RETURN"))
    private static void getVanillaBiomeNames(String name, CallbackInfoReturnable<ResourceKey<Biome>> cir) {
        ResourceKey<Biome> biomeRegistryKey = cir.getReturnValue();
        NAME_BIOME_MAP.put(biomeRegistryKey.identifier(), biomeRegistryKey);
    }
}
