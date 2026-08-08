package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.NodeOptions;
import com.github.charlyb01.music_control.api.ParentMix;
import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.categories.MusicIdentifier;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, validated graph used by one resource-reload generation. */
public final class MusicGraphSnapshot {
    public enum ResolutionKind { VANILLA, OVERRIDE, SILENT }
    public record Node(Identifier id, NodeOptions options, Set<Identifier> parents) {}

    public record PoolSelection(boolean silent, boolean vanilla, HashSet<Music> pool) {
        static PoolSelection vanilla(HashSet<Music> pool) {
            return new PoolSelection(false, true, pool);
        }

        static PoolSelection silentSelection() {
            return new PoolSelection(true, false, new HashSet<>());
        }

        static PoolSelection graph(HashSet<Music> pool) {
            return new PoolSelection(false, false, pool);
        }
    }

    private final Map<Identifier, Node> nodes;
    private final Map<Identifier, Identifier> biomeBindings;
    private final Map<Identifier, Identifier> dimensionBindings;
    private final Set<Identifier> hiddenEvents;

    MusicGraphSnapshot(
            Map<Identifier, MutableNode> sourceNodes,
            Map<Identifier, Identifier> biomeBindings,
            Map<Identifier, Identifier> dimensionBindings,
            Set<Identifier> hiddenEvents) {
        Map<Identifier, Node> immutableNodes = new LinkedHashMap<>();
        sourceNodes.forEach((id, node) -> immutableNodes.put(id,
                new Node(id, node.options, Collections.unmodifiableSet(new LinkedHashSet<>(node.parents)))));
        this.nodes = Collections.unmodifiableMap(immutableNodes);
        this.biomeBindings = Collections.unmodifiableMap(new LinkedHashMap<>(biomeBindings));
        this.dimensionBindings = Collections.unmodifiableMap(new LinkedHashMap<>(dimensionBindings));
        this.hiddenEvents = Collections.unmodifiableSet(new HashSet<>(hiddenEvents));
        validate();
    }

    public static MusicGraphSnapshot empty() {
        return new MusicGraphSnapshot(Map.of(), Map.of(), Map.of(), Set.of());
    }

    public Map<Identifier, Node> nodes() {
        return nodes;
    }

    public Identifier nodeForBiome(Identifier biome) {
        return biomeBindings.get(biome);
    }

    public Map<Identifier, Identifier> biomeBindings() {
        return biomeBindings;
    }

    public Identifier nodeForDimension(Identifier dimension) {
        return dimensionBindings.get(dimension);
    }

    public Map<Identifier, Identifier> dimensionBindings() {
        return dimensionBindings;
    }

    public Set<Identifier> hiddenEvents() {
        return hiddenEvents;
    }

    public boolean isHidden(Identifier event) {
        return hiddenEvents.contains(event);
    }

    /** Determines whether a node should replace the actual vanilla selection. */
    public ResolutionKind probe(Identifier nodeId) {
        return probe(nodeId, new HashSet<>());
    }

    private ResolutionKind probe(Identifier nodeId, Set<Identifier> visited) {
        Node node = nodes.get(nodeId);
        if (node == null || !visited.add(nodeId)) return ResolutionKind.VANILLA;
        boolean direct = !MusicIdentifier.getListFromEvent(node.id()).isEmpty();
        if (direct && node.options().parentMix() != ParentMix.PARENTS_ONLY) return ResolutionKind.OVERRIDE;
        if (direct && node.options().parentMix() == ParentMix.PARENTS_ONLY) {
            return probeParents(node, visited);
        }
        return switch (node.options().whenEmpty()) {
            case VANILLA -> ResolutionKind.VANILLA;
            case SILENT -> ResolutionKind.SILENT;
            case PARENTS -> probeParents(node, visited);
        };
    }

    private ResolutionKind probeParents(Node node, Set<Identifier> visited) {
        boolean silent = false;
        for (Identifier parent : node.parents()) {
            ResolutionKind kind = probe(parent, new HashSet<>(visited));
            if (kind == ResolutionKind.OVERRIDE) return ResolutionKind.OVERRIDE;
            silent |= kind == ResolutionKind.SILENT;
        }
        return silent ? ResolutionKind.SILENT : ResolutionKind.VANILLA;
    }

    public PoolSelection resolvePool(
            Identifier nodeId,
            Identifier vanillaEvent,
            RandomSource random) {
        HashSet<Music> vanillaPool = vanillaEvent == null
                ? new HashSet<>() : MusicIdentifier.getListFromEvent(vanillaEvent);
        return resolvePool(nodeId, vanillaPool, random, new HashSet<>());
    }

    /** Resolves a node with an already-composed runtime context as virtual vanilla. */
    public PoolSelection resolvePool(
            Identifier nodeId,
            Set<Music> vanillaPool,
            RandomSource random) {
        return resolvePool(nodeId, new HashSet<>(vanillaPool), random, new HashSet<>());
    }

    private PoolSelection resolvePool(
            Identifier nodeId,
            HashSet<Music> vanillaPool,
            RandomSource random,
            Set<Identifier> visited) {
        Node node = nodes.get(nodeId);
        if (node == null || !visited.add(nodeId)) return PoolSelection.vanilla(new HashSet<>(vanillaPool));

        HashSet<Music> direct = MusicIdentifier.getListFromEvent(node.id());
        if (direct.isEmpty()) {
            return switch (node.options().whenEmpty()) {
                case VANILLA -> PoolSelection.vanilla(new HashSet<>(vanillaPool));
                case SILENT -> PoolSelection.silentSelection();
                case PARENTS -> resolveParents(node, vanillaPool, random, visited);
            };
        }

        PoolSelection parentSelection = resolveParents(node, vanillaPool, random, new HashSet<>(visited));
        HashSet<Music> parents = parentSelection.silent() ? new HashSet<>() : parentSelection.pool();
        return switch (node.options().parentMix()) {
            case EXCLUSIVE -> PoolSelection.graph(direct);
            case PARENTS_ONLY -> parents.isEmpty()
                    ? PoolSelection.vanilla(new HashSet<>(vanillaPool)) : PoolSelection.graph(parents);
            case HALF -> {
                if (parents.isEmpty()) yield PoolSelection.graph(direct);
                yield PoolSelection.graph(random.nextBoolean() ? direct : parents);
            }
            case PROPORTIONAL -> {
                direct.addAll(parents);
                yield PoolSelection.graph(direct);
            }
        };
    }

    private PoolSelection resolveParents(
            Node node,
            HashSet<Music> vanillaPool,
            RandomSource random,
            Set<Identifier> visited) {
        HashSet<Music> combined = new HashSet<>();
        boolean vanilla = false;
        boolean silent = false;
        for (Identifier parent : node.parents()) {
            PoolSelection selected = resolvePool(parent, vanillaPool, random, new HashSet<>(visited));
            if (selected.silent()) {
                silent = true;
                continue;
            }
            vanilla |= selected.vanilla();
            combined.addAll(selected.pool());
        }
        if (node.parents().isEmpty()) return PoolSelection.vanilla(new HashSet<>(vanillaPool));
        if (combined.isEmpty() && silent) return PoolSelection.silentSelection();
        return vanilla ? PoolSelection.vanilla(combined) : PoolSelection.graph(combined);
    }

    private void validate() {
        for (Node node : nodes.values()) {
            for (Identifier parent : node.parents()) {
                if (!nodes.containsKey(parent)) {
                    throw new IllegalStateException("Unknown parent " + parent + " for music node " + node.id());
                }
            }
        }
        Set<Identifier> complete = new HashSet<>();
        for (Identifier node : nodes.keySet()) validateAcyclic(node, complete, new LinkedHashSet<>());
        for (Map.Entry<Identifier, Identifier> binding : biomeBindings.entrySet()) {
            if (!nodes.containsKey(binding.getValue())) {
                throw new IllegalStateException(
                        "Biome " + binding.getKey() + " references unknown music node " + binding.getValue());
            }
        }
        for (Map.Entry<Identifier, Identifier> binding : dimensionBindings.entrySet()) {
            if (!nodes.containsKey(binding.getValue())) {
                throw new IllegalStateException(
                        "Dimension " + binding.getKey() + " references unknown music node " + binding.getValue());
            }
        }
    }

    private void validateAcyclic(
            Identifier node,
            Set<Identifier> complete,
            LinkedHashSet<Identifier> path) {
        if (complete.contains(node)) return;
        if (!path.add(node)) {
            List<Identifier> cycle = new ArrayList<>(path);
            cycle.add(node);
            throw new IllegalStateException("Music graph cycle: " + cycle);
        }
        for (Identifier parent : nodes.get(node).parents()) validateAcyclic(parent, complete, path);
        path.remove(node);
        complete.add(node);
    }

    static final class MutableNode {
        private NodeOptions options;
        private final Set<Identifier> parents = new LinkedHashSet<>();

        MutableNode(NodeOptions options) {
            this.options = options;
        }
    }

    static final class Builder implements com.github.charlyb01.music_control.api.GraphRegistrar {
        private final Map<Identifier, MutableNode> nodes = new LinkedHashMap<>();
        private final Map<Identifier, Identifier> biomeBindings = new LinkedHashMap<>();
        private final Map<Identifier, Identifier> dimensionBindings = new LinkedHashMap<>();
        private final Set<Identifier> hiddenEvents = new HashSet<>();
        private boolean open = true;

        @Override
        public void addNode(Identifier nodeId) {
            addNode(nodeId, NodeOptions.defaults());
        }

        @Override
        public void addNode(Identifier nodeId, NodeOptions options) {
            checkOpen();
            if (nodes.putIfAbsent(nodeId, new MutableNode(options)) != null) {
                throw new IllegalStateException("Duplicate music node: " + nodeId);
            }
        }

        void putNode(Identifier nodeId, NodeOptions options, Set<Identifier> parents) {
            checkOpen();
            MutableNode node = new MutableNode(options);
            node.parents.addAll(parents);
            nodes.put(nodeId, node);
        }

        void patchNode(
                Identifier nodeId,
                ParentMix parentMix,
                EmptyBehavior whenEmpty,
                Set<Identifier> parents) {
            checkOpen();
            MutableNode node = nodes.computeIfAbsent(
                    nodeId, ignored -> new MutableNode(NodeOptions.defaults()));
            NodeOptions previous = node.options;
            node.options = new NodeOptions(
                    parentMix == null ? previous.parentMix() : parentMix,
                    whenEmpty == null ? previous.whenEmpty() : whenEmpty);
            if (parents != null) {
                node.parents.clear();
                node.parents.addAll(parents);
            }
        }

        void ensureNode(Identifier nodeId) {
            checkOpen();
            nodes.putIfAbsent(nodeId, new MutableNode(NodeOptions.defaults()));
        }

        void removeNode(Identifier nodeId) {
            nodes.remove(nodeId);
            biomeBindings.values().removeIf(nodeId::equals);
            dimensionBindings.values().removeIf(nodeId::equals);
        }

        @Override
        public void addParent(Identifier child, Identifier parent) {
            checkOpen();
            MutableNode node = nodes.get(child);
            if (node == null) throw new IllegalStateException("Unknown child music node: " + child);
            node.parents.add(parent);
        }

        @Override
        public void bindBiome(net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> biome,
                              Identifier nodeId) {
            bindBiome(biome.identifier(), nodeId);
        }

        void bindBiome(Identifier biome, Identifier nodeId) {
            checkOpen();
            biomeBindings.put(biome, nodeId);
        }

        void unbindBiome(Identifier biome) {
            checkOpen();
            biomeBindings.remove(biome);
        }

        @Override
        public void bindDimension(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                  Identifier nodeId) {
            bindDimension(dimension.identifier(), nodeId);
        }

        void bindDimension(Identifier dimension, Identifier nodeId) {
            checkOpen();
            dimensionBindings.put(dimension, nodeId);
        }

        void unbindDimension(Identifier dimension) {
            checkOpen();
            dimensionBindings.remove(dimension);
        }

        void hide(Identifier event) {
            hiddenEvents.add(event);
        }

        void show(Identifier event) {
            hiddenEvents.remove(event);
        }

        MusicGraphSnapshot build() {
            checkOpen();
            open = false;
            return new MusicGraphSnapshot(nodes, biomeBindings, dimensionBindings, hiddenEvents);
        }

        private void checkOpen() {
            if (!open) throw new IllegalStateException("This graph registrar is no longer active");
        }
    }
}
