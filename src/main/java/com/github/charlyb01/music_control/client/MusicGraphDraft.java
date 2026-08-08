package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.NodeOptions;
import com.github.charlyb01.music_control.api.ParentMix;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Mutable, UI-scoped graph copy. Nothing is published until Save. */
public final class MusicGraphDraft {
    private static final class Node {
        private NodeOptions options;
        private final Set<Identifier> parents;

        private Node(NodeOptions options, Set<Identifier> parents) {
            this.options = options;
            this.parents = new LinkedHashSet<>(parents);
        }
    }

    private final Map<Identifier, Node> nodes = new LinkedHashMap<>();
    private final Map<Identifier, Identifier> biomeBindings = new LinkedHashMap<>();
    private final Map<Identifier, Identifier> dimensionBindings = new LinkedHashMap<>();
    private final Set<Identifier> hiddenEvents = new HashSet<>();

    public MusicGraphDraft(MusicGraphSnapshot source) {
        source.nodes().forEach((id, node) ->
                nodes.put(id, new Node(node.options(), node.parents())));
        biomeBindings.putAll(source.biomeBindings());
        dimensionBindings.putAll(source.dimensionBindings());
        hiddenEvents.addAll(source.hiddenEvents());
    }

    public void ensureNode(Identifier node) {
        nodes.computeIfAbsent(node, ignored -> new Node(NodeOptions.defaults(), Set.of()));
    }

    public Set<Identifier> nodes() {
        return Set.copyOf(nodes.keySet());
    }

    public ParentMix parentMix(Identifier node) {
        ensureNode(node);
        return nodes.get(node).options.parentMix();
    }

    public void setParentMix(Identifier node, ParentMix value) {
        ensureNode(node);
        Node current = nodes.get(node);
        current.options = new NodeOptions(value, current.options.whenEmpty());
    }

    public EmptyBehavior whenEmpty(Identifier node) {
        ensureNode(node);
        return nodes.get(node).options.whenEmpty();
    }

    public void setWhenEmpty(Identifier node, EmptyBehavior value) {
        ensureNode(node);
        Node current = nodes.get(node);
        current.options = new NodeOptions(current.options.parentMix(), value);
    }

    public Set<Identifier> parents(Identifier node) {
        ensureNode(node);
        return Set.copyOf(nodes.get(node).parents);
    }

    public boolean canAddParent(Identifier child, Identifier parent) {
        if (child.equals(parent)) return false;
        ensureNode(child);
        return !reaches(parent, child, new HashSet<>());
    }

    public boolean addParent(Identifier child, Identifier parent) {
        if (!canAddParent(child, parent)) return false;
        ensureNode(parent);
        return nodes.get(child).parents.add(parent);
    }

    public void removeParent(Identifier child, Identifier parent) {
        ensureNode(child);
        nodes.get(child).parents.remove(parent);
    }

    public Set<Identifier> biomesForNode(Identifier node) {
        Set<Identifier> result = new HashSet<>();
        biomeBindings.forEach((biome, target) -> {
            if (target.equals(node)) result.add(biome);
        });
        return result;
    }

    public void bindBiome(Identifier biome, Identifier node) {
        ensureNode(node);
        biomeBindings.put(biome, node);
    }

    public void unbindBiome(Identifier biome, Identifier node) {
        biomeBindings.remove(biome, node);
    }

    public Set<Identifier> dimensions() {
        return Set.copyOf(dimensionBindings.keySet());
    }

    public Set<Identifier> dimensionsForNode(Identifier node) {
        Set<Identifier> result = new HashSet<>();
        dimensionBindings.forEach((dimension, target) -> {
            if (target.equals(node)) result.add(dimension);
        });
        return result;
    }

    public void bindDimension(Identifier dimension, Identifier node) {
        ensureNode(node);
        dimensionBindings.put(dimension, node);
    }

    public void unbindDimension(Identifier dimension, Identifier node) {
        dimensionBindings.remove(dimension, node);
    }

    public MusicGraphSnapshot snapshot() {
        MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
        nodes.forEach((id, node) -> builder.putNode(id, node.options, node.parents));
        biomeBindings.forEach(builder::bindBiome);
        dimensionBindings.forEach(builder::bindDimension);
        hiddenEvents.forEach(builder::hide);
        return builder.build();
    }

    private boolean reaches(Identifier from, Identifier target, Set<Identifier> visited) {
        if (from.equals(target)) return true;
        if (!visited.add(from)) return false;
        Node node = nodes.get(from);
        if (node == null) return false;
        for (Identifier parent : node.parents) {
            if (reaches(parent, target, visited)) return true;
        }
        return false;
    }
}
