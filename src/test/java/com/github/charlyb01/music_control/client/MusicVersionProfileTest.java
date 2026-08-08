package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.ParentMix;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicVersionProfileTest {
    @Test
    void loadsTheProfileForTheActiveBuildTarget() {
        MusicVersionProfile profile = MusicVersionProfile.current();

        assertEquals(System.getProperty("music_control.minecraft_target"), profile.minecraftVersion());
        for (MusicVersionProfile.Event event : MusicVersionProfile.Event.values()) {
            assertNotNull(profile.event(event));
            assertTrue(profile.nodes().containsKey(profile.event(event)));
        }
    }

    @Test
    void everyBiomeBindingTargetsADeclaredNode() {
        MusicVersionProfile profile = MusicVersionProfile.current();

        assertFalse(profile.biomeBindings().isEmpty());
        profile.biomeBindings().forEach((biome, node) -> {
            assertTrue(profile.nodes().containsKey(node));
            assertTrue(profile.biomesForEvent(node).contains(biome));
            assertTrue(profile.isSyntheticEvent(node));
        });
    }

    @Test
    void paleGardenUsesTheOnlyImplicitDefaultAndDeepDarkIsNotHardCoded() {
        MusicVersionProfile profile = MusicVersionProfile.current();
        Identifier paleGardenBiome = Identifier.parse("minecraft:pale_garden");
        Identifier paleGardenNode = profile.biomeBindings().get(paleGardenBiome);

        assertEquals(Identifier.parse("minecraft:music.overworld.pale_garden"), paleGardenNode);
        assertEquals(ParentMix.EXCLUSIVE, profile.nodes().get(paleGardenNode).options().parentMix());
        assertEquals(EmptyBehavior.VANILLA, profile.nodes().get(paleGardenNode).options().whenEmpty());
        assertTrue(profile.nodes().get(paleGardenNode).parents().isEmpty());
        assertNull(profile.biomeBindings().get(Identifier.parse("minecraft:deep_dark")));
    }

    @Test
    void modelsBuiltInConditionsAsProportionalNodes() {
        MusicVersionProfile profile = MusicVersionProfile.current();

        assertEquals(ParentMix.PROPORTIONAL,
                profile.nodes().get(profile.event(MusicVersionProfile.Event.PLAYER_FLYING))
                        .options().parentMix());
        assertEquals(profile.event(MusicVersionProfile.Event.NETHER),
                profile.dimensionBindings().get(Identifier.parse("minecraft:the_nether")));
    }

    @Test
    void definesEveryPortableParentAndKeepsTheNativeTaigaParentHidden() {
        MusicVersionProfile profile = MusicVersionProfile.current();
        Identifier oldGrowth = Identifier.parse("minecraft:music.overworld.old_growth_taiga");

        assertTrue(profile.isHidden(oldGrowth));
        assertTrue(profile.portableEvents().get(oldGrowth).includeBase());
        assertFalse(profile.portableMembers(oldGrowth).isEmpty());
        profile.portableEvents().keySet().forEach(parent -> {
            assertTrue(profile.nodes().containsKey(parent));
            assertFalse(profile.portableMembers(parent).isEmpty());
        });
    }
}
