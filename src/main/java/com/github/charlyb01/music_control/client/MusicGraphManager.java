package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.GraphContributor;
import com.github.charlyb01.music_control.api.MusicControlApi;
import com.github.charlyb01.music_control.api.ParentMix;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds and atomically publishes graph snapshots during sound-resource reloads. */
public final class MusicGraphManager {
    public static final int SCHEMA_VERSION = 1;
    public static final String RESOURCE_PREFIX = "music_control/music_graph";

    private static final Logger LOGGER = LoggerFactory.getLogger("music_control/graph");
    private static final Gson GSON = new Gson();
    private static volatile MusicGraphSnapshot current = MusicGraphSnapshot.empty();

    private MusicGraphManager() {}

    public static MusicGraphSnapshot current() {
        return current;
    }

    public static void reload(ResourceManager resources) {
        try {
            MusicGraphSnapshot.Builder builder = new MusicGraphSnapshot.Builder();
            addBuiltInProfile(builder);

            for (Map.Entry<Identifier, GraphContributor> contribution
                    : MusicControlApi.contributors().entrySet()) {
                try {
                    contribution.getValue().contribute(builder);
                } catch (RuntimeException exception) {
                    throw new IllegalStateException(
                            "Music graph contributor failed: " + contribution.getKey(), exception);
                }
            }

            Map<Identifier, List<Resource>> documents = resources.listResourceStacks(
                    RESOURCE_PREFIX,
                    id -> id.getPath().endsWith(".json"));
            Map<String, Integer> packPriority = new HashMap<>();
            int[] priority = {0};
            resources.listPacks().forEach(pack -> packPriority.put(pack.packId(), priority[0]++));
            List<GraphResource> ordered = new ArrayList<>();
            documents.forEach((id, stack) ->
                    stack.forEach(resource -> ordered.add(new GraphResource(id, resource))));
            ordered.sort(Comparator
                    .comparingInt((GraphResource item) ->
                            packPriority.getOrDefault(item.resource().source().packId(), -1))
                    .thenComparing(GraphResource::id));
            ordered.forEach(item -> applyResource(builder, item.id(), item.resource()));

            current = builder.build();
            LOGGER.info("Loaded music graph with {} nodes", current.nodes().size());
        } catch (RuntimeException exception) {
            LOGGER.error("Rejecting invalid music graph reload; keeping the previous snapshot", exception);
        }
    }

    private static void addBuiltInProfile(MusicGraphSnapshot.Builder builder) {
        MusicVersionProfile profile = MusicVersionProfile.current();
        profile.nodes().forEach((id, node) ->
                builder.putNode(id, node.options(), node.parents()));
        profile.biomeBindings().forEach(builder::bindBiome);
        profile.dimensionBindings().forEach(builder::bindDimension);
        profile.hiddenEvents().forEach(builder::hide);
    }

    private static void applyResource(
            MusicGraphSnapshot.Builder builder,
            Identifier documentId,
            Resource resource) {
        try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonObject document = GSON.fromJson(reader, JsonObject.class);
            if (document == null) return;
            int schema = document.has("schemaVersion")
                    ? document.get("schemaVersion").getAsInt() : SCHEMA_VERSION;
            if (schema != SCHEMA_VERSION) {
                throw new IllegalStateException("Unsupported graph schema " + schema + " in " + documentId);
            }
            if (document.has("nodes")) {
                for (Map.Entry<String, JsonElement> entry
                        : document.getAsJsonObject("nodes").entrySet()) {
                    Identifier nodeId = parseId(entry.getKey(), documentId);
                    JsonObject value = entry.getValue().getAsJsonObject();
                    if (value.has("disabled") && value.get("disabled").getAsBoolean()) {
                        builder.removeNode(nodeId);
                        continue;
                    }
                    ParentMix mix = value.has("parentMix")
                            ? ParentMix.valueOf(value.get("parentMix").getAsString().toUpperCase(Locale.ROOT))
                            : null;
                    EmptyBehavior empty = value.has("whenEmpty")
                            ? EmptyBehavior.valueOf(value.get("whenEmpty").getAsString().toUpperCase(Locale.ROOT))
                            : null;
                    Set<Identifier> parents = value.has("parents") ? new HashSet<>() : null;
                    if (value.has("parents")) {
                        for (JsonElement parent : value.getAsJsonArray("parents")) {
                            parents.add(parseId(parent.getAsString(), documentId));
                        }
                    }
                    builder.patchNode(nodeId, mix, empty, parents);
                }
            }
            if (document.has("biomes")) {
                for (Map.Entry<String, JsonElement> entry
                        : document.getAsJsonObject("biomes").entrySet()) {
                    Identifier biome = parseId(entry.getKey(), documentId);
                    if (entry.getValue().isJsonNull()) builder.unbindBiome(biome);
                    else builder.bindBiome(biome, parseId(entry.getValue().getAsString(), documentId));
                }
            }
            if (document.has("dimensions")) {
                for (Map.Entry<String, JsonElement> entry
                        : document.getAsJsonObject("dimensions").entrySet()) {
                    Identifier dimension = parseId(entry.getKey(), documentId);
                    if (entry.getValue().isJsonNull()) builder.unbindDimension(dimension);
                    else builder.bindDimension(dimension, parseId(entry.getValue().getAsString(), documentId));
                }
            }
            if (document.has("hiddenEvents")) {
                for (JsonElement event : document.getAsJsonArray("hiddenEvents")) {
                    builder.hide(parseId(event.getAsString(), documentId));
                }
            }
            if (document.has("visibleEvents")) {
                for (JsonElement event : document.getAsJsonArray("visibleEvents")) {
                    builder.show(parseId(event.getAsString(), documentId));
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load music graph resource " + documentId, exception);
        }
    }

    private static Identifier parseId(String value, Identifier documentId) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) throw new IllegalStateException("Invalid identifier " + value + " in " + documentId);
        return id;
    }

    private record GraphResource(Identifier id, Resource resource) {}
}
