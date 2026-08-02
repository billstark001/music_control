package com.github.charlyb01.music_control.client;

import java.util.HashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;

public class SoundEventRegistry {
    public static final HashMap<ResourceKey<Biome>, SoundEvent> BIOME_MUSIC_MAP = new HashMap<>();
    public static final HashMap<Identifier, ResourceKey<Biome>> NAME_BIOME_MAP = new HashMap<>();

    public static Holder.Reference<SoundEvent> PLAYER_FLYING = registerReference("music.misc.flying");
    public static Holder.Reference<SoundEvent> PLAYER_DRIVING = registerReference("music.misc.driving");
    public static Holder.Reference<SoundEvent> PLAYER_RIDING = registerReference("music.misc.riding");
    public static Holder.Reference<SoundEvent> TIME_NIGHT = registerReference("music.misc.night");
    public static Holder.Reference<SoundEvent> WEATHER_RAIN = registerReference("music.misc.rain");
    public static Holder.Reference<SoundEvent> WEATHER_THUNDER = registerReference("music.misc.thunder");

    public static Holder.Reference<SoundEvent> SNOWY_PLAINS = registerReference("music.overworld.snowy_plains");
    public static Holder.Reference<SoundEvent> ICE_SPIKES = registerReference("music.overworld.ice_spikes");
    public static Holder.Reference<SoundEvent> SNOWY_TAIGA = registerReference("music.overworld.snowy_taiga");
    public static Holder.Reference<SoundEvent> SNOWY_BEACH = registerReference("music.overworld.snowy_beach");

    public static Holder.Reference<SoundEvent> WINDSWEPT_HILLS = registerReference("music.overworld.windswept_hills");
    public static Holder.Reference<SoundEvent> WINDSWEPT_GRAVELLY_HILLS = registerReference("music.overworld.windswept_gravelly_hills");
    public static Holder.Reference<SoundEvent> WINDSWEPT_FOREST = registerReference("music.overworld.windswept_forest");
    public static Holder.Reference<SoundEvent> TAIGA = registerReference("music.overworld.taiga");
    public static Holder.Reference<SoundEvent> OLD_GROWTH_PINE_TAIGA = registerReference("music.overworld.old_growth_pine_taiga");
    public static Holder.Reference<SoundEvent> OLD_GROWTH_SPRUCE_TAIGA = registerReference("music.overworld.old_growth_spruce_taiga");
    public static Holder.Reference<SoundEvent> STONY_SHORE = registerReference("music.overworld.stony_shore");

    public static Holder.Reference<SoundEvent> PLAINS = registerReference("music.overworld.plains");
    public static Holder.Reference<SoundEvent> SUNFLOWER_PLAINS = registerReference("music.overworld.sunflower_plains");
    public static Holder.Reference<SoundEvent> BIRCH_FOREST = registerReference("music.overworld.birch_forest");
    public static Holder.Reference<SoundEvent> OLD_GROWTH_BIRCH_FOREST = registerReference("music.overworld.old_growth_birch_forest");
    public static Holder.Reference<SoundEvent> DARK_FOREST = registerReference("music.overworld.dark_forest");
    //public static RegistryEntry.Reference<SoundEvent> PALE_GARDEN = registerReference("music.overworld.pale_garden");
    public static Holder.Reference<SoundEvent> MANGROVE_SWAMP = registerReference("music.overworld.mangrove_swamp");
    public static Holder.Reference<SoundEvent> BEACH = registerReference("music.overworld.beach");
    public static Holder.Reference<SoundEvent> MUSHROOM_FIELDS = registerReference("music.overworld.mushroom_fields");

    public static Holder.Reference<SoundEvent> SAVANNA = registerReference("music.overworld.savanna");
    public static Holder.Reference<SoundEvent> SAVANNA_PLATEAU = registerReference("music.overworld.savanna_plateau");
    public static Holder.Reference<SoundEvent> WINDSWEPT_SAVANNA = registerReference("music.overworld.windswept_savanna");
    public static Holder.Reference<SoundEvent> WOODED_BADLANDS = registerReference("music.overworld.wooded_badlands");
    public static Holder.Reference<SoundEvent> ERODED_BADLANDS = registerReference("music.overworld.eroded_badlands");

    public static Holder.Reference<SoundEvent> RIVER = registerReference("music.overworld.river");
    public static Holder.Reference<SoundEvent> FROZEN_RIVER = registerReference("music.overworld.frozen_river");
    public static Holder.Reference<SoundEvent> WARM_OCEAN = registerReference("music.overworld.warm_ocean");
    public static Holder.Reference<SoundEvent> LUKEWARM_OCEAN = registerReference("music.overworld.lukewarm_ocean");
    public static Holder.Reference<SoundEvent> DEEP_LUKEWARM_OCEAN = registerReference("music.overworld.deep_lukewarm_ocean");
    public static Holder.Reference<SoundEvent> OCEAN = registerReference("music.overworld.ocean");
    public static Holder.Reference<SoundEvent> DEEP_OCEAN = registerReference("music.overworld.deep_ocean");
    public static Holder.Reference<SoundEvent> COLD_OCEAN = registerReference("music.overworld.cold_ocean");
    public static Holder.Reference<SoundEvent> DEEP_COLD_OCEAN = registerReference("music.overworld.deep_cold_ocean");
    public static Holder.Reference<SoundEvent> FROZEN_OCEAN = registerReference("music.overworld.frozen_ocean");
    public static Holder.Reference<SoundEvent> DEEP_FROZEN_OCEAN = registerReference("music.overworld.deep_frozen_ocean");

    public static Holder.Reference<SoundEvent> THE_END = registerReference("music.end.the_end");
    public static Holder.Reference<SoundEvent> END_HIGHLANDS = registerReference("music.end.end_highlands");
    public static Holder.Reference<SoundEvent> END_MIDLANDS = registerReference("music.end.end_midlands");
    public static Holder.Reference<SoundEvent> SMALL_END_ISLANDS = registerReference("music.end.small_end_islands");
    public static Holder.Reference<SoundEvent> END_BARRENS = registerReference("music.end.end_barrens");

    public static Holder.Reference<SoundEvent> NETHER = registerReference("music.nether");

    public static void init() {

    }

    private static Holder.Reference<SoundEvent> registerReference(final String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
