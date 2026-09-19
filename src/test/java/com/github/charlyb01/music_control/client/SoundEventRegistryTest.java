package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.NodeOptions;
import com.github.charlyb01.music_control.api.ParentMix;
import com.github.charlyb01.music_control.categories.Music;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SoundEventRegistryTest {
    private static final Identifier VANILLA_EVENT = Identifier.parse("test:vanilla_event");
    private static final Identifier DIMENSION = Identifier.parse("test:dimension");
    private static final Identifier BIOME = Identifier.parse("test:biome");

    @AfterEach
    void resetState() {
        Music.MUSIC_BY_EVENT.clear();
        Music.EVENTS_OF_EVENT.clear();
        SoundEventRegistry.invalidateSelectionPlan();
        SoundEventRegistry.beginSituationalSelection();
    }

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

    @Test
    void finalVanillaReturnBecomesTheSelectionSignature() {
        net.minecraft.sounds.Music finalMusic = new net.minecraft.sounds.Music(
                Holder.direct(SoundEvent.createVariableRangeEvent(VANILLA_EVENT)),
                0, 0, true);

        SoundEventRegistry.captureVanillaMusic(finalMusic);

        assertEquals(VANILLA_EVENT, SoundEventRegistry.selectedVanillaEvent());
        assertEquals(List.of(VANILLA_EVENT), SoundEventRegistry.selectedContextSignature());
    }

    @Test
    void halfChoiceIsCachedUntilThePlanIsConsumed() {
        Music direct = new Music(Identifier.parse("test:direct_track"));
        Music inherited = new Music(Identifier.parse("test:inherited_track"));
        Music.MUSIC_BY_EVENT.put(BIOME, new HashSet<>(Set.of(direct)));
        Music.MUSIC_BY_EVENT.put(VANILLA_EVENT, new HashSet<>(Set.of(inherited)));

        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(BIOME,
                new NodeOptions(ParentMix.HALF, EmptyBehavior.VANILLA));
        builder.addNode(VANILLA_EVENT);
        builder.addParent(BIOME, VANILLA_EVENT);
        MusicGraphSnapshot graph = builder.build();

        AtomicInteger firstCalls = new AtomicInteger();
        RandomSource firstRandom = countingBooleanRandom(true, firstCalls);
        var first = SoundEventRegistry.selectionPlanOrCreate(
                graph, null, List.of(BIOME), firstRandom);

        AtomicInteger secondCalls = new AtomicInteger();
        RandomSource secondRandom = countingBooleanRandom(false, secondCalls);
        var cached = SoundEventRegistry.selectionPlanOrCreate(
                graph, null, List.of(BIOME), secondRandom);

        assertSame(first, cached);
        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());

        SoundEventRegistry.consumeSelectionPlan();
        SoundEventRegistry.selectionPlanOrCreate(
                graph, null, List.of(BIOME), secondRandom);
        assertEquals(1, secondCalls.get());
    }

    private static RandomSource countingBooleanRandom(
            boolean result,
            AtomicInteger calls) {
        return new LegacyRandomSource(42L) {
            @Override
            public boolean nextBoolean() {
                calls.incrementAndGet();
                return result;
            }
        };
    }
}
