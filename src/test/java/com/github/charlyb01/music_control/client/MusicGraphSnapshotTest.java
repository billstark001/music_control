package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.NodeOptions;
import com.github.charlyb01.music_control.api.ParentMix;
import com.github.charlyb01.music_control.categories.Music;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.HashSet;

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
}
