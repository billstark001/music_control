package com.github.charlyb01.music_control.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundEventRegistryTest {
    private static final Identifier VANILLA_EVENT = Identifier.parse("test:vanilla_event");
    private static final Identifier DIMENSION = Identifier.parse("test:dimension");
    private static final Identifier BIOME = Identifier.parse("test:biome");

    @Test
    void vanillaSelectedGraphNodeIsTheBaseContextAndDuplicateBindingsAreIgnored() {
        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(VANILLA_EVENT);
        builder.addNode(DIMENSION);
        builder.addNode(BIOME);
        MusicGraphSnapshot graph = builder.build();

        assertEquals(
                List.of(VANILLA_EVENT, DIMENSION, BIOME),
                SoundEventRegistry.initialContext(
                        graph, VANILLA_EVENT, DIMENSION, BIOME));
        assertEquals(
                List.of(VANILLA_EVENT, DIMENSION),
                SoundEventRegistry.initialContext(
                        graph, VANILLA_EVENT, DIMENSION, VANILLA_EVENT));
    }
}
