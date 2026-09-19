package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.NodeOptions;
import com.github.charlyb01.music_control.api.ParentMix;
import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.categories.MusicIdentifier;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicGraphSnapshotTest {
    private static final Identifier CHILD = Identifier.parse("test:child");
    private static final Identifier PARENT = Identifier.parse("test:parent");

    @AfterEach
    void clearMusicPools() {
        Music.MUSIC_BY_EVENT.clear();
        Music.EVENTS_OF_EVENT.clear();
    }

    @Test
    void omittedOptionsHaveOneUnambiguousDefault() {
        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(CHILD);

        MusicGraphSnapshot graph = builder.build();

        assertEquals(ParentMix.EXCLUSIVE, graph.nodes().get(CHILD).options().parentMix());
        assertEquals(EmptyBehavior.VANILLA, graph.nodes().get(CHILD).options().whenEmpty());
        assertEquals(MusicGraphSnapshot.ResolutionKind.VANILLA, graph.probe(CHILD));
    }

    @Test
    void partialResourcePatchPreservesUnspecifiedNodeFields() {
        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.putNode(CHILD,
                new NodeOptions(ParentMix.HALF, EmptyBehavior.PARENTS),
                Set.of(PARENT));
        builder.putNode(PARENT, NodeOptions.defaults(), Set.of());

        builder.patchNode(CHILD, null, EmptyBehavior.SILENT, null);
        MusicGraphSnapshot.Node node = builder.build().nodes().get(CHILD);

        assertEquals(ParentMix.HALF, node.options().parentMix());
        assertEquals(EmptyBehavior.SILENT, node.options().whenEmpty());
        assertEquals(Set.of(PARENT), node.parents());
    }

    @Test
    void rejectsMissingParentsAndCyclesBeforePublication() {
        MusicGraphSnapshot.Builder missing = new MusicGraphSnapshot.Builder();
        missing.addNode(CHILD);
        missing.addParent(CHILD, PARENT);
        assertThrows(IllegalStateException.class, missing::build);

        MusicGraphSnapshot.Builder cyclic = new MusicGraphSnapshot.Builder();
        cyclic.addNode(CHILD);
        cyclic.addNode(PARENT);
        cyclic.addParent(CHILD, PARENT);
        cyclic.addParent(PARENT, CHILD);
        IllegalStateException error = assertThrows(IllegalStateException.class, cyclic::build);
        assertTrue(error.getMessage().contains("cycle"));
    }

    @Test
    void anEmptySilentNodeIsDistinguishedFromVanilla() {
        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(CHILD, new NodeOptions(ParentMix.EXCLUSIVE, EmptyBehavior.SILENT));

        assertEquals(MusicGraphSnapshot.ResolutionKind.SILENT, builder.build().probe(CHILD));
    }

    @Test
    void proportionalRootCombinesItsDirectPoolWithTheRuntimeVanillaPool() {
        Music direct = new Music(Identifier.parse("test:direct_track"));
        Music vanilla = new Music(Identifier.parse("test:vanilla_track"));
        Music.MUSIC_BY_EVENT.put(CHILD, new HashSet<>(Set.of(direct)));

        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(CHILD,
                new NodeOptions(ParentMix.PROPORTIONAL, EmptyBehavior.VANILLA));

        MusicGraphSnapshot.PoolSelection selection = builder.build().resolvePool(
                CHILD, Set.of(vanilla), RandomSource.create(42));

        assertFalse(selection.silent());
        assertEquals(Set.of(direct, vanilla), selection.pool());
    }

    @Test
    void emptyNodeCanResolveAPlayableParentPool() {
        Music inherited = new Music(Identifier.parse("test:inherited_track"));
        Music.MUSIC_BY_EVENT.put(PARENT, new HashSet<>(Set.of(inherited)));

        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(CHILD,
                new NodeOptions(ParentMix.EXCLUSIVE, EmptyBehavior.PARENTS));
        builder.addNode(PARENT);
        builder.addParent(CHILD, PARENT);

        MusicGraphSnapshot graph = builder.build();
        assertEquals(MusicGraphSnapshot.ResolutionKind.OVERRIDE, graph.probe(CHILD));

        MusicGraphSnapshot.PoolSelection selection = graph.resolvePool(
                CHILD, Set.of(), RandomSource.create(42));
        assertFalse(selection.silent());
        assertFalse(selection.vanilla());
        assertEquals(Set.of(inherited), selection.pool());
    }

    @Test
    void probeUsesTheSameParentsOnlyResultAsFullResolution() {
        Music direct = new Music(Identifier.parse("test:unused_direct_track"));
        Music vanilla = new Music(Identifier.parse("test:vanilla_track"));
        Music.MUSIC_BY_EVENT.put(CHILD, new HashSet<>(Set.of(direct)));

        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(CHILD,
                new NodeOptions(ParentMix.PARENTS_ONLY, EmptyBehavior.VANILLA));
        builder.addNode(PARENT,
                new NodeOptions(ParentMix.EXCLUSIVE, EmptyBehavior.SILENT));
        builder.addParent(CHILD, PARENT);
        MusicGraphSnapshot graph = builder.build();

        MusicGraphSnapshot.PoolSelection selection = graph.resolvePool(
                CHILD, Set.of(vanilla), RandomSource.create(42));

        assertFalse(selection.silent());
        assertTrue(selection.vanilla());
        assertEquals(Set.of(vanilla), selection.pool());
        assertEquals(MusicGraphSnapshot.ResolutionKind.VANILLA, graph.probe(CHILD));
    }

    @Test
    void completeContextResolutionKeepsSilenceTerminal() {
        Identifier silentNode = Identifier.parse("test:silent");
        Identifier laterNode = Identifier.parse("test:later_override");
        Music later = new Music(Identifier.parse("test:later_track"));
        Music.MUSIC_BY_EVENT.put(laterNode, new HashSet<>(Set.of(later)));

        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(silentNode,
                new NodeOptions(ParentMix.EXCLUSIVE, EmptyBehavior.SILENT));
        builder.addNode(laterNode);
        MusicGraphSnapshot graph = builder.build();

        MusicIdentifier.EventMusicPlan plan = MusicIdentifier.createEventMusicPlan(
                graph, null, List.of(silentNode, laterNode), RandomSource.create(42));

        assertTrue(plan.silent());
        assertFalse(plan.override());
        assertTrue(plan.pool().isEmpty());
    }

    @Test
    void oneOverridingParentMakesACombinedParentPoolAnOverride() {
        Identifier child = Identifier.parse("test:combined_child");
        Identifier vanillaParent = Identifier.parse("test:vanilla_parent");
        Identifier graphParent = Identifier.parse("test:graph_parent");
        Music vanilla = new Music(Identifier.parse("test:vanilla_track"));
        Music custom = new Music(Identifier.parse("test:custom_track"));
        Music.MUSIC_BY_EVENT.put(graphParent, new HashSet<>(Set.of(custom)));

        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(child,
                new NodeOptions(ParentMix.EXCLUSIVE, EmptyBehavior.PARENTS));
        builder.addNode(vanillaParent);
        builder.addNode(graphParent);
        builder.addParent(child, vanillaParent);
        builder.addParent(child, graphParent);
        MusicGraphSnapshot graph = builder.build();

        MusicGraphSnapshot.PoolSelection selection = graph.resolvePool(
                child, Set.of(vanilla), RandomSource.create(42));

        assertFalse(selection.silent());
        assertFalse(selection.vanilla());
        assertEquals(Set.of(vanilla, custom), selection.pool());
    }
}
