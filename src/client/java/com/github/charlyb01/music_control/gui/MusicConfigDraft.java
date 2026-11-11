package com.github.charlyb01.music_control.gui;

import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.client.MusicGraphDraft;
import com.github.charlyb01.music_control.client.MusicGraphManager;
import com.github.charlyb01.music_control.client.MusicGraphSnapshot;
import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.ParentMix;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Transactional copy of editable sound/event membership. */
public final class MusicConfigDraft {
    private final Map<Identifier, Set<Identifier>> soundsByEvent = new HashMap<>();
    private final Map<Identifier, Set<Identifier>> linkedEvents = new HashMap<>();
    private final Set<Identifier> explicitSilence = new HashSet<>();
    private final MusicGraphDraft graph = new MusicGraphDraft(MusicGraphManager.current());

    public MusicConfigDraft() {
        Music.MUSIC_BY_EVENT.forEach((event, musics) -> {
            Set<Identifier> sounds = new HashSet<>();
            musics.forEach(music -> sounds.add(music.getIdentifier()));
            soundsByEvent.put(event, sounds);
        });
        Music.EVENTS_OF_EVENT.forEach((event, links) ->
                linkedEvents.put(event, new HashSet<>(links)));
        explicitSilence.addAll(SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS);
    }

    public Set<Identifier> sounds(Identifier event) {
        return Set.copyOf(soundsByEvent.getOrDefault(event, Set.of()));
    }

    public Set<Identifier> eventsForSound(Identifier sound) {
        Set<Identifier> result = new HashSet<>();
        soundsByEvent.forEach((event, sounds) -> {
            if (sounds.contains(sound)) result.add(event);
        });
        return result;
    }

    public Set<Identifier> linkedEvents(Identifier event) {
        return Set.copyOf(linkedEvents.getOrDefault(event, Set.of()));
    }

    public void addSound(Identifier event, Identifier sound) {
        soundsByEvent.computeIfAbsent(event, ignored -> new HashSet<>()).add(sound);
        explicitSilence.remove(event);
    }

    public void removeSound(Identifier event, Identifier sound) {
        soundsByEvent.computeIfAbsent(event, ignored -> new HashSet<>()).remove(sound);
        updateSilence(event);
    }

    public void addLinkedEvent(Identifier event, Identifier linked) {
        linkedEvents.computeIfAbsent(event, ignored -> new HashSet<>()).add(linked);
        explicitSilence.remove(event);
    }

    public void removeLinkedEvent(Identifier event, Identifier linked) {
        Set<Identifier> links = linkedEvents.get(event);
        if (links != null) {
            links.remove(linked);
            if (links.isEmpty()) linkedEvents.remove(event);
        }
        updateSilence(event);
    }

    public void ensureGraphNode(Identifier event) {
        graph.ensureNode(event);
    }

    public Set<Identifier> graphNodes() {
        return graph.nodes();
    }

    public ParentMix parentMix(Identifier event) {
        return graph.parentMix(event);
    }

    public void setParentMix(Identifier event, ParentMix value) {
        graph.setParentMix(event, value);
    }

    public EmptyBehavior whenEmpty(Identifier event) {
        return graph.whenEmpty(event);
    }

    public void setWhenEmpty(Identifier event, EmptyBehavior value) {
        graph.setWhenEmpty(event, value);
    }

    public Set<Identifier> graphParents(Identifier event) {
        return graph.parents(event);
    }

    public boolean canAddGraphParent(Identifier child, Identifier parent) {
        return graph.canAddParent(child, parent);
    }

    public boolean addGraphParent(Identifier child, Identifier parent) {
        return graph.addParent(child, parent);
    }

    public void removeGraphParent(Identifier child, Identifier parent) {
        graph.removeParent(child, parent);
    }

    public Set<Identifier> biomesForNode(Identifier event) {
        return graph.biomesForNode(event);
    }

    public void bindBiome(Identifier biome, Identifier event) {
        graph.bindBiome(biome, event);
    }

    public void unbindBiome(Identifier biome, Identifier event) {
        graph.unbindBiome(biome, event);
    }

    public Set<Identifier> dimensions() {
        return graph.dimensions();
    }

    public Set<Identifier> dimensionsForNode(Identifier event) {
        return graph.dimensionsForNode(event);
    }

    public void bindDimension(Identifier dimension, Identifier event) {
        graph.bindDimension(dimension, event);
    }

    public void unbindDimension(Identifier dimension, Identifier event) {
        graph.unbindDimension(dimension, event);
    }

    public MusicGraphSnapshot graphSnapshot() {
        return graph.snapshot();
    }

    private void updateSilence(Identifier event) {
        if (soundsByEvent.getOrDefault(event, Set.of()).isEmpty()
                && linkedEvents.getOrDefault(event, Set.of()).isEmpty()) {
            explicitSilence.add(event);
        }
    }

    /** Commits the complete draft immediately before writing and reloading the pack. */
    public void apply() {
        Music.MUSIC_BY_EVENT.values().forEach(Set::clear);
        Music.MUSIC_BY_NAMESPACE.getOrDefault(Music.ALL_MUSICS, new HashSet<>())
                .forEach(music -> music.getEvents().clear());
        SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.clear();
        soundsByEvent.forEach((event, sounds) -> {
            Music.MUSIC_BY_EVENT.computeIfAbsent(event, ignored -> new HashSet<>());
            for (Identifier sound : sounds) {
                Music music = Music.getMusicFromIdentifier(sound);
                if (music != null) music.addEvent(event);
            }
        });
        Music.EVENTS_OF_EVENT.clear();
        linkedEvents.forEach((event, links) ->
                Music.EVENTS_OF_EVENT.put(event, new HashSet<>(links)));
        SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.clear();
        SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.addAll(explicitSilence);
    }
}
