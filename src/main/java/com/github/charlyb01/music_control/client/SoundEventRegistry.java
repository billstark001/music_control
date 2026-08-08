package com.github.charlyb01.music_control.client;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SoundEventRegistry {
    public static final HashMap<Identifier, ResourceKey<Biome>> NAME_BIOME_MAP = new HashMap<>();
    public static final Set<Identifier> EXPLICITLY_EMPTY_EVENTS = new HashSet<>();

    public enum SelectionMode {
        VANILLA,
        OVERRIDE,
        SILENT
    }

    private static SelectionMode currentSelectionMode = SelectionMode.VANILLA;
    private static Identifier selectedGraphNode;
    private static Identifier selectedVanillaEvent;
    private static Music selectedVanillaMusic;
    private static List<Identifier> selectedContextNodes = List.of();

    private SoundEventRegistry() {}

    /** Graph sound events are resolved dynamically and do not require registry mutation. */
    public static void init() {
    }

    /**
     * Returns whether an event is meaningful for the active runtime. Non-biome
     * events are always available; biome events require at least one matching
     * biome key captured from the running Minecraft version.
     */
    public static boolean isEventAvailable(Identifier event) {
        var configuredBiomes = MusicGraphManager.current().biomeBindings().entrySet().stream()
                .filter(entry -> entry.getValue().equals(event))
                .map(Map.Entry::getKey)
                .toList();
        return configuredBiomes.isEmpty()
                || configuredBiomes.stream().anyMatch(NAME_BIOME_MAP::containsKey);
    }

    public static SelectionMode currentSelectionMode() {
        return currentSelectionMode;
    }

    public static Identifier selectedGraphNode() {
        return selectedGraphNode;
    }

    public static Identifier selectedVanillaEvent() {
        return selectedVanillaEvent;
    }

    public static Music selectedVanillaMusic() {
        return selectedVanillaMusic;
    }

    public static List<Identifier> selectedContextNodes() {
        return selectedContextNodes;
    }

    /** Stable identity of the vanilla choice plus every effective graph layer. */
    public static List<Identifier> selectedContextSignature() {
        ArrayList<Identifier> signature = new ArrayList<>();
        if (selectedVanillaEvent != null) signature.add(selectedVanillaEvent);
        for (Identifier node : selectedContextNodes) {
            if (!signature.contains(node)) signature.add(node);
        }
        return List.copyOf(signature);
    }

    /** Clears state before each complete Minecraft situational-music lookup. */
    public static void beginSituationalSelection() {
        currentSelectionMode = SelectionMode.VANILLA;
        selectedGraphNode = null;
        selectedVanillaEvent = null;
        selectedVanillaMusic = null;
        selectedContextNodes = List.of();
    }

    /**
     * Builds the active graph context around the music selected by vanilla and
     * data-pack biome attributes. The selected vanilla event is retained as the
     * runtime fallback; a playable override receives the original timing envelope.
     */
    public static Optional<Music> resolveBiomeMusic(
            ResourceKey<Biome> biome,
            Player player,
            Level world,
            Optional<Music> vanillaSelection) {
        currentSelectionMode = SelectionMode.VANILLA;
        selectedVanillaMusic = vanillaSelection.orElse(null);
        selectedVanillaEvent = vanillaSelection.map(music -> music.sound().value().location()).orElse(null);
        MusicGraphSnapshot graph = MusicGraphManager.current();
        if (biome != null) {
            selectedGraphNode = graph.nodeForBiome(biome.identifier());
        }
        Identifier dimensionNode = world == null
                ? null : graph.nodeForDimension(world.dimension().identifier());
        ArrayList<Identifier> context = initialContext(
                graph, selectedVanillaEvent, dimensionNode, selectedGraphNode);
        if (world != null && world.dimension() == Level.OVERWORLD) {
            if (com.github.charlyb01.music_control.Utils.isNight(world)) {
                addIfPresent(context, graph,
                        MusicVersionProfile.current().event(MusicVersionProfile.Event.TIME_NIGHT));
            }
            if (world.isRaining()) {
                addIfPresent(context, graph,
                        MusicVersionProfile.current().event(MusicVersionProfile.Event.WEATHER_RAIN));
            }
            if (world.isThundering()) {
                addIfPresent(context, graph,
                        MusicVersionProfile.current().event(MusicVersionProfile.Event.WEATHER_THUNDER));
            }
        }
        if (player != null && player.isPassenger()) {
            addIfPresent(context, graph, MusicVersionProfile.current().event(
                    player.getVehicle() instanceof LivingEntity
                            ? MusicVersionProfile.Event.PLAYER_RIDING
                            : MusicVersionProfile.Event.PLAYER_DRIVING));
        }
        if (player != null && player.isFallFlying()) {
            addIfPresent(context, graph,
                    MusicVersionProfile.current().event(MusicVersionProfile.Event.PLAYER_FLYING));
        }
        selectedContextNodes = List.copyOf(context);

        MusicGraphSnapshot.ResolutionKind resolution = MusicGraphSnapshot.ResolutionKind.VANILLA;
        Identifier envelopeNode = null;
        for (Identifier node : context) {
            MusicGraphSnapshot.ResolutionKind next = graph.probe(node);
            if (next == MusicGraphSnapshot.ResolutionKind.VANILLA) continue;
            resolution = next;
            if (next == MusicGraphSnapshot.ResolutionKind.OVERRIDE) envelopeNode = node;
        }

        if (resolution == MusicGraphSnapshot.ResolutionKind.VANILLA) return vanillaSelection;
        if (resolution == MusicGraphSnapshot.ResolutionKind.SILENT) {
            currentSelectionMode = SelectionMode.SILENT;
            return Optional.empty();
        }
        currentSelectionMode = SelectionMode.OVERRIDE;
        SoundEvent event = SoundEvent.createVariableRangeEvent(envelopeNode);
        if (vanillaSelection.isPresent()) {
            Music vanilla = vanillaSelection.get();
            return Optional.of(new Music(
                    Holder.direct(event),
                    vanilla.minDelay(),
                    vanilla.maxDelay(),
                    vanilla.replaceCurrentMusic()));
        }
        return Optional.of(Musics.createGameMusic(Holder.direct(event)));
    }

    static ArrayList<Identifier> initialContext(
            MusicGraphSnapshot graph,
            Identifier vanillaEvent,
            Identifier dimensionNode,
            Identifier biomeNode) {
        ArrayList<Identifier> context = new ArrayList<>();
        addIfPresent(context, graph, vanillaEvent);
        addIfPresent(context, graph, dimensionNode);
        addIfPresent(context, graph, biomeNode);
        return context;
    }

    private static void addIfPresent(
            List<Identifier> context,
            MusicGraphSnapshot graph,
            Identifier node) {
        if (node != null && graph.nodes().containsKey(node) && !context.contains(node)) {
            context.add(node);
        }
    }

}
