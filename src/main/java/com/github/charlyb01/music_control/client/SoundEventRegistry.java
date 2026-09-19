package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.categories.MusicIdentifier;
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
import net.minecraft.util.RandomSource;

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
    private static MusicIdentifier.EventMusicPlan selectedPlan;
    private static MusicGraphSnapshot selectedPlanGraph;
    private static RandomSource selectionRandom = RandomSource.create();

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

    /** Supplies MusicManager's RNG so HALF is decided once for the pending selection. */
    public static void useSelectionRandom(RandomSource random) {
        if (random != null) selectionRandom = random;
    }

    /** Returns the cached full-resolution plan only when it describes this lookup. */
    public static MusicIdentifier.EventMusicPlan selectionPlan(
            Identifier vanillaEvent,
            List<Identifier> contextNodes) {
        return cachedSelectionPlan(
                MusicGraphManager.current(), vanillaEvent, contextNodes);
    }

    private static MusicIdentifier.EventMusicPlan cachedSelectionPlan(
            MusicGraphSnapshot graph,
            Identifier vanillaEvent,
            List<Identifier> contextNodes) {
        return selectedPlan != null
                && selectedPlanGraph == graph
                && selectedPlan.matches(vanillaEvent, contextNodes)
                ? selectedPlan : null;
    }

    /** The next completed track selection must receive a fresh HALF decision. */
    public static void consumeSelectionPlan() {
        selectedPlan = null;
        selectedPlanGraph = null;
    }

    /** Resource reloads replace both graph structure and the sound pools it reads. */
    public static void invalidateSelectionPlan() {
        consumeSelectionPlan();
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

    /** Records a final non-environmental return such as screen or End boss music. */
    public static void captureVanillaMusic(Music vanillaSelection) {
        currentSelectionMode = SelectionMode.VANILLA;
        selectedGraphNode = null;
        selectedVanillaMusic = vanillaSelection;
        selectedVanillaEvent = vanillaSelection == null
                ? null : vanillaSelection.sound().value().location();
        selectedContextNodes = List.of();
        selectPlan(MusicGraphManager.current());
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
        selectedGraphNode = null;
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
        MusicIdentifier.EventMusicPlan plan = selectPlan(graph);

        if (!plan.override() && !plan.silent()) return vanillaSelection;
        if (plan.silent()) {
            currentSelectionMode = SelectionMode.SILENT;
            return Optional.empty();
        }
        currentSelectionMode = SelectionMode.OVERRIDE;
        SoundEvent event = SoundEvent.createVariableRangeEvent(plan.envelopeNode());
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

    private static MusicIdentifier.EventMusicPlan selectPlan(MusicGraphSnapshot graph) {
        return selectionPlanOrCreate(
                graph, selectedVanillaEvent, selectedContextNodes, selectionRandom);
    }

    static MusicIdentifier.EventMusicPlan selectionPlanOrCreate(
            MusicGraphSnapshot graph,
            Identifier vanillaEvent,
            List<Identifier> contextNodes,
            RandomSource random) {
        MusicIdentifier.EventMusicPlan existing = cachedSelectionPlan(
                graph, vanillaEvent, contextNodes);
        if (existing != null) return existing;
        selectedPlan = MusicIdentifier.createEventMusicPlan(
                graph, vanillaEvent, contextNodes, random);
        selectedPlanGraph = graph;
        return selectedPlan;
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
