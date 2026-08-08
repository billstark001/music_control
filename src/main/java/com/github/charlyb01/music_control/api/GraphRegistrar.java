package com.github.charlyb01.music_control.api;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.Level;

/** A reload-scoped builder exposed to compatibility integrations. */
public interface GraphRegistrar {
    void addNode(Identifier nodeId);
    void addNode(Identifier nodeId, NodeOptions options);
    void addParent(Identifier child, Identifier parent);
    void bindBiome(ResourceKey<Biome> biome, Identifier nodeId);
    void bindDimension(ResourceKey<Level> dimension, Identifier nodeId);
}
