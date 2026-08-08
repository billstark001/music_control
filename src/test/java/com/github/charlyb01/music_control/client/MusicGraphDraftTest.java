package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.NodeOptions;
import com.github.charlyb01.music_control.api.ParentMix;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicGraphDraftTest {
    private static final Identifier CHILD = Identifier.parse("test:child");
    private static final Identifier PARENT = Identifier.parse("test:parent");

    @Test
    void editsAreTransactionalAndExportAValidatedSnapshot() {
        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(CHILD);
        builder.addNode(PARENT);
        MusicGraphSnapshot source = builder.build();
        MusicGraphDraft draft = new MusicGraphDraft(source);

        draft.setParentMix(CHILD, ParentMix.PROPORTIONAL);
        draft.setWhenEmpty(CHILD, EmptyBehavior.PARENTS);
        assertTrue(draft.addParent(CHILD, PARENT));
        draft.bindBiome(Identifier.parse("test:biome"), CHILD);
        draft.bindDimension(Identifier.parse("test:dimension"), CHILD);

        assertEquals(NodeOptions.defaults(), source.nodes().get(CHILD).options());
        assertTrue(source.nodes().get(CHILD).parents().isEmpty());

        MusicGraphSnapshot edited = draft.snapshot();
        assertEquals(new NodeOptions(ParentMix.PROPORTIONAL, EmptyBehavior.PARENTS),
                edited.nodes().get(CHILD).options());
        assertEquals(CHILD, edited.nodeForBiome(Identifier.parse("test:biome")));
        assertEquals(CHILD, edited.nodeForDimension(Identifier.parse("test:dimension")));
    }

    @Test
    void parentPickerCanRejectCyclesBeforeSave() {
        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(CHILD);
        builder.addNode(PARENT);
        MusicGraphDraft draft = new MusicGraphDraft(builder.build());

        assertTrue(draft.addParent(CHILD, PARENT));
        assertFalse(draft.canAddParent(PARENT, CHILD));
        assertFalse(draft.addParent(PARENT, CHILD));
    }

    @Test
    void addingAParentCandidateAlsoCreatesItsGraphNode() {
        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        builder.addNode(CHILD);
        MusicGraphDraft draft = new MusicGraphDraft(builder.build());

        assertTrue(draft.addParent(CHILD, PARENT));

        MusicGraphSnapshot saved = draft.snapshot();
        assertTrue(saved.nodes().containsKey(PARENT));
        assertEquals(Set.of(PARENT), saved.nodes().get(CHILD).parents());
    }
}
