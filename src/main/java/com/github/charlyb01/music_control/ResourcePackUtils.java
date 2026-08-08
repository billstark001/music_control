package com.github.charlyb01.music_control;

import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.client.MusicGraphManager;
import com.github.charlyb01.music_control.client.MusicGraphSnapshot;
import com.github.charlyb01.music_control.client.MusicVersionProfile;
import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.NonNull;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.charlyb01.music_control.categories.Music.EVENTS_OF_EVENT;
import static com.github.charlyb01.music_control.categories.Music.MUSIC_BY_EVENT;
import static com.github.charlyb01.music_control.categories.MusicCategories.NAMESPACES;

public class ResourcePackUtils {
    private ResourcePackUtils() {}

    protected static final String RESOURCEPACK_PROFILE_NAME = "file/" + MusicControlClient.MOD_ID;
    protected static Path RESOURCEPACK_PATH = null;
    protected static Path ASSETS_PATH = null;
    protected static boolean WAS_CREATED = false;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean exists() {
        return Minecraft.getInstance().getResourcePackRepository().getAvailableIds().stream().anyMatch(
                name -> name.startsWith(RESOURCEPACK_PROFILE_NAME));
    }

    public static boolean wasCreatedOrIsEnabled() {
        return WAS_CREATED || Minecraft.getInstance().getResourcePackRepository().getSelectedIds().stream().anyMatch(
                name -> name.startsWith(RESOURCEPACK_PROFILE_NAME));
    }

    /**
     * Returns true if the loaded resource pack has a music_control_meta.json
     * whose minecraft_version differs from the current game version.
     */
    public static boolean needsMigration() {
        String storedVersion = readMetadataVersion();
        if (storedVersion == null) return false;
        return !storedVersion.equals(SharedConstants.getCurrentVersion().name());
    }

    /**
     * Reads the minecraft_version field from the resource pack's metadata file.
     * Returns null if the file does not exist or cannot be read.
     */
    public static String readMetadataVersion() {
        Path existingPath = getExistingMetadataPath();
        if (existingPath == null) return null;
        try (FileReader reader = new FileReader(existingPath.toFile())) {
            JsonObject meta = GSON.fromJson(reader, JsonObject.class);
            if (meta == null || !meta.has("minecraft_version")) return null;
            return meta.get("minecraft_version").getAsString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Migrates the user's music configuration from the stored metadata to the
     * current Minecraft version.
     *
     * @param inPlace         when {@code true} the existing resource pack is overwritten;
     *                        when {@code false} a brand-new resource pack is created.
     * @param applyImmediately when {@code false} and {@code inPlace} is false, the new
     *                        pack is created but NOT enabled (the old one stays active).
     * @param storedVersion   the version string stored in the metadata (used to name the
     *                        new resource pack folder when {@code inPlace} is false).
     */
    public static void migrateConfig(boolean inPlace, boolean applyImmediately, String storedVersion) {
        // Remember the source pack path so we can read metadata from it
        Path sourcePack = RESOURCEPACK_PATH;
        if (sourcePack == null) {
            setPaths();
            sourcePack = RESOURCEPACK_PATH;
        }
        if (sourcePack == null) return;

        // Read the stored changes from metadata
        Path metaFile = sourcePack.resolve("music_control_meta.json");
        if (!Files.exists(metaFile)) return;

        JsonObject storedMeta;
        try (FileReader reader = new FileReader(metaFile.toFile())) {
            storedMeta = GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (storedMeta == null || !storedMeta.has("changes")) return;
        JsonObject changesObj = storedMeta.getAsJsonObject("changes");

        // Build a map of the user's changes: eventId -> {remove, add}
        TreeMap<String, TreeSet<String>> toRemove = new TreeMap<>();
        TreeMap<String, TreeSet<String>> toAdd = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : changesObj.entrySet()) {
            Identifier parsedEvent = Identifier.tryParse(entry.getKey());
            String eventId = parsedEvent == null
                    ? entry.getKey()
                    : parsedEvent.toString();
            JsonObject change = entry.getValue().getAsJsonObject();
            TreeSet<String> removeSet = toRemove.computeIfAbsent(eventId, ignored -> new TreeSet<>());
            TreeSet<String> addSet = toAdd.computeIfAbsent(eventId, ignored -> new TreeSet<>());
            if (change.has("remove")) {
                for (JsonElement el : change.getAsJsonArray("remove")) {
                    removeSet.add(el.getAsString());
                }
            }
            if (change.has("add")) {
                for (JsonElement el : change.getAsJsonArray("add")) {
                    addSet.add(el.getAsString());
                }
            }
        }

        TreeSet<String> silentEvents = new TreeSet<>();
        if (storedMeta.has("silent_events")) {
            for (JsonElement event : storedMeta.getAsJsonArray("silent_events")) {
                Identifier parsed = Identifier.tryParse(event.getAsString());
                if (parsed != null) silentEvents.add(parsed.toString());
            }
        }
        TreeMap<String, TreeSet<Identifier>> eventLinks = new TreeMap<>();
        if (storedMeta.has("event_links")) {
            for (Map.Entry<String, JsonElement> entry
                    : storedMeta.getAsJsonObject("event_links").entrySet()) {
                Identifier event = Identifier.tryParse(entry.getKey());
                if (event == null) continue;
                TreeSet<Identifier> links = eventLinks.computeIfAbsent(
                        event.toString(), ignored -> new TreeSet<>());
                for (JsonElement linkedValue : entry.getValue().getAsJsonArray()) {
                    Identifier linked = Identifier.tryParse(linkedValue.getAsString());
                    if (linked != null) links.add(linked);
                }
            }
        }

        // If creating a new pack, set up a fresh resource pack first
        if (!inPlace) {
            createMigrationResourcePack(storedVersion, applyImmediately);
            // createMigrationResourcePack sets RESOURCEPACK_PATH / ASSETS_PATH and WAS_CREATED
        }

        Map<Identifier, EventDefinition> definitions = new HashMap<>();
        for (String namespace : NAMESPACES) {
            TreeMap<String, TreeSet<String>> vanillaSounds = loadVanillaSounds(namespace);
            TreeSet<String> allEventIds = new TreeSet<>(vanillaSounds.keySet());
            toRemove.keySet().stream().filter(id -> id.startsWith(namespace + ":")).forEach(allEventIds::add);
            toAdd.keySet().stream().filter(id -> id.startsWith(namespace + ":")).forEach(allEventIds::add);
            silentEvents.stream().filter(id -> id.startsWith(namespace + ":")).forEach(allEventIds::add);
            eventLinks.keySet().stream().filter(id -> id.startsWith(namespace + ":")).forEach(allEventIds::add);

            for (String eventId : allEventIds) {
                Identifier id = Identifier.tryParse(eventId);
                if (id == null) continue;
                EventDefinition definition = new EventDefinition();
                definition.sounds.addAll(vanillaSounds.getOrDefault(eventId, new TreeSet<>()));
                definition.sounds.removeAll(toRemove.getOrDefault(eventId, new TreeSet<>()));
                definition.sounds.addAll(toAdd.getOrDefault(eventId, new TreeSet<>()));
                definition.events.addAll(eventLinks.getOrDefault(eventId, new TreeSet<>()));
                definition.explicitSilence = silentEvents.contains(eventId);
                definitions.put(id, definition);
            }
        }
        applyPortableProjection(definitions, MusicGraphManager.current());
        writeDefinitions(definitions);

        // Update metadata with new version and same changes (recomputed via writeMetadata)
        // We need MUSIC_BY_EVENT to be consistent, but since this is a migration outside the
        // normal edit flow we simply rewrite metadata with the new game version + same changes.
        writeMigratedMetadata(buildChangesObject(toRemove, toAdd), silentEvents, eventLinks);
        writeGraphConfig(MusicGraphManager.current());
    }

    private static JsonObject buildChangesObject(
            Map<String, TreeSet<String>> toRemove,
            Map<String, TreeSet<String>> toAdd) {
        TreeSet<String> eventIds = new TreeSet<>(toRemove.keySet());
        eventIds.addAll(toAdd.keySet());

        JsonObject changes = new JsonObject();
        for (String eventId : eventIds) {
            JsonObject change = new JsonObject();
            TreeSet<String> removed = toRemove.getOrDefault(eventId, new TreeSet<>());
            TreeSet<String> added = toAdd.getOrDefault(eventId, new TreeSet<>());
            if (!removed.isEmpty()) {
                JsonArray removeArray = new JsonArray();
                removed.forEach(removeArray::add);
                change.add("remove", removeArray);
            }
            if (!added.isEmpty()) {
                JsonArray addArray = new JsonArray();
                added.forEach(addArray::add);
                change.add("add", addArray);
            }
            if (change.size() > 0) changes.add(eventId, change);
        }
        return changes;
    }

    /**
     * Writes a metadata file keeping the same user changes but updating the stored
     * minecraft_version to the current game version.
     */
    private static void writeMigratedMetadata(
            JsonObject changesObj,
            TreeSet<String> silentEvents,
            Map<String, TreeSet<Identifier>> eventLinks) {
        JsonObject meta = new JsonObject();
        meta.addProperty("format_version", 1);
        meta.addProperty("minecraft_version", SharedConstants.getCurrentVersion().name());
        meta.add("changes", changesObj);
        meta.add("silent_events", toJsonArray(silentEvents));
        meta.add("event_links", eventLinksToJson(eventLinks));

        Path metaPath = getMetadataPath();
        if (metaPath == null) return;
        try (PrintWriter out = new PrintWriter(new FileWriter(metaPath.toFile()))) {
            out.write(GSON.toJson(meta));
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the path to an existing music_control_meta.json in the current
     * resource pack, or null if it does not exist.
     */
    private static Path getExistingMetadataPath() {
        Path rp = RESOURCEPACK_PATH;
        if (rp == null) {
            // Try to resolve from enabled packs without side effects
            Optional<String> selected = Minecraft.getInstance()
                    .getResourcePackRepository().getSelectedIds().stream()
                    .filter(n -> n.startsWith(RESOURCEPACK_PROFILE_NAME)).findFirst();
            if (selected.isEmpty()) return null;
            rp = Minecraft.getInstance().getResourcePackDirectory()
                    .resolve(selected.get().substring(5));
        }
        Path filePath = rp.resolve("music_control_meta.json");
        return Files.exists(filePath) ? filePath : null;
    }

    public static void writeConfig(MusicGraphSnapshot graph) {
        if (WAS_CREATED) {
            WAS_CREATED = false;
        } else if (RESOURCEPACK_PATH == null) {
            setPaths();
        }

        Map<Identifier, EventDefinition> definitions = new HashMap<>();
        MUSIC_BY_EVENT.forEach((eventId, musics) -> {
            EventDefinition definition = new EventDefinition();
            for (Music music : musics) definition.sounds.add(music.getIdentifier().toString());
            for (Identifier linked : EVENTS_OF_EVENT.getOrDefault(eventId, new HashSet<>())) {
                definition.events.add(linked);
            }
            definition.explicitSilence = SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.contains(eventId);

            boolean unconfiguredSynthetic = MusicVersionProfile.current().isSyntheticEvent(eventId)
                    && definition.sounds.isEmpty()
                    && definition.events.isEmpty()
                    && !definition.explicitSilence;
            if (!unconfiguredSynthetic) definitions.put(eventId, definition);
        });
        applyPortableProjection(definitions, graph);

        // Build sorted sound event map: namespace -> sorted event path -> JsonObject
        Map<String, TreeMap<String, JsonObject>> sortedByNamespace = new HashMap<>();
        for (String namespace : NAMESPACES) {
            sortedByNamespace.put(namespace, new TreeMap<>());
        }

        definitions.forEach((eventId, definition) -> {
            JsonArray sounds = new JsonArray();
            for (String name : definition.sounds) {
                JsonObject fileSound = new JsonObject();
                fileSound.addProperty("name", name);
                fileSound.addProperty("stream", true);
                sounds.add(fileSound);
            }

            for (Identifier linked : definition.events) {
                JsonObject eventSound = new JsonObject();
                eventSound.addProperty("name", linked.getNamespace().equals(eventId.getNamespace())
                        ? linked.getPath() : linked.toString());
                eventSound.addProperty("type", "event");
                sounds.add(eventSound);
            }

            JsonObject soundEvent = new JsonObject();
            soundEvent.addProperty("category", "music");
            soundEvent.addProperty("replace", true);
            soundEvent.add("sounds", sounds);

            TreeMap<String, JsonObject> nsMap = sortedByNamespace.get(eventId.getNamespace());
            if (nsMap != null) {
                nsMap.put(eventId.getPath(), soundEvent);
            }
        });

        // Write sounds.json for each namespace (keys sorted via TreeMap)
        for (String namespace : NAMESPACES) {
            TreeMap<String, JsonObject> nsMap = sortedByNamespace.get(namespace);
            if (nsMap == null) continue;

            JsonObject root = new JsonObject();
            for (Map.Entry<String, JsonObject> entry : nsMap.entrySet()) {
                root.add(entry.getKey(), entry.getValue());
            }

            Path soundPath = getSoundPath(namespace);
            if (soundPath == null) continue;
            try (PrintWriter out = new PrintWriter(new FileWriter(soundPath.toFile()))) {
                out.write(GSON.toJson(root));
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        writeMetadata();
        writeGraphConfig(graph);
    }

    private static void writeGraphConfig(MusicGraphSnapshot graph) {
        JsonObject document = new JsonObject();
        document.addProperty("schemaVersion", MusicGraphManager.SCHEMA_VERSION);
        JsonObject nodes = new JsonObject();
        graph.nodes().forEach((id, node) -> {
            JsonObject value = new JsonObject();
            if (node.options().parentMix() != com.github.charlyb01.music_control.api.ParentMix.EXCLUSIVE) {
                value.addProperty("parentMix", node.options().parentMix().name().toLowerCase(Locale.ROOT));
            }
            if (node.options().whenEmpty() != com.github.charlyb01.music_control.api.EmptyBehavior.VANILLA) {
                value.addProperty("whenEmpty", node.options().whenEmpty().name().toLowerCase(Locale.ROOT));
            }
            if (!node.parents().isEmpty()) {
                JsonArray parents = new JsonArray();
                node.parents().stream().sorted().forEach(parent -> parents.add(parent.toString()));
                value.add("parents", parents);
            }
            nodes.add(id.toString(), value);
        });
        document.add("nodes", nodes);

        JsonObject biomes = new JsonObject();
        graph.biomeBindings().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> biomes.addProperty(entry.getKey().toString(), entry.getValue().toString()));
        document.add("biomes", biomes);
        JsonObject dimensions = new JsonObject();
        graph.dimensionBindings().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> dimensions.addProperty(
                        entry.getKey().toString(), entry.getValue().toString()));
        document.add("dimensions", dimensions);
        JsonArray hidden = new JsonArray();
        graph.hiddenEvents().stream().sorted()
                .forEach(event -> hidden.add(event.toString()));
        document.add("hiddenEvents", hidden);

        if (ASSETS_PATH == null) return;
        Path graphPath = ASSETS_PATH.resolve("music_control")
                .resolve("music_control/music_graph/user.json");
        try {
            Files.createDirectories(graphPath.getParent());
            try (PrintWriter out = new PrintWriter(new FileWriter(graphPath.toFile()))) {
                out.write(GSON.toJson(document));
                out.flush();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Restores the logical (pre-projection) contents of portable parent events
     * after the generated resource pack has been loaded. Without this step, a
     * later save would treat the standalone compatibility union as a direct
     * parent edit and removed child tracks could become sticky.
     */
    public static void restoreLogicalPortableEvents() {
        Path metadataPath = getExistingMetadataPath();
        if (metadataPath == null) return;
        JsonObject metadata;
        try (FileReader reader = new FileReader(metadataPath.toFile())) {
            metadata = GSON.fromJson(reader, JsonObject.class);
        } catch (Exception exception) {
            exception.printStackTrace();
            return;
        }
        if (metadata == null || !metadata.has("changes")) return;

        JsonObject changes = metadata.getAsJsonObject("changes");
        JsonObject links = metadata.has("event_links")
                ? metadata.getAsJsonObject("event_links") : new JsonObject();
        HashSet<Identifier> silent = new HashSet<>();
        if (metadata.has("silent_events")) {
            for (JsonElement value : metadata.getAsJsonArray("silent_events")) {
                Identifier id = Identifier.tryParse(value.getAsString());
                if (id != null) silent.add(id);
            }
        }

        Map<String, TreeMap<String, TreeSet<String>>> vanillaByNamespace = new HashMap<>();
        for (Identifier parent : MusicVersionProfile.current().portableEvents().keySet()) {
            TreeMap<String, TreeSet<String>> vanilla = vanillaByNamespace.computeIfAbsent(
                    parent.getNamespace(), ResourcePackUtils::loadVanillaSounds);
            TreeSet<String> logicalSounds = new TreeSet<>(
                    vanilla.getOrDefault(parent.toString(), new TreeSet<>()));
            JsonObject change = changes.has(parent.toString())
                    ? changes.getAsJsonObject(parent.toString()) : null;
            if (change != null && change.has("remove")) {
                for (JsonElement value : change.getAsJsonArray("remove")) {
                    logicalSounds.remove(value.getAsString());
                }
            }
            if (change != null && change.has("add")) {
                for (JsonElement value : change.getAsJsonArray("add")) {
                    logicalSounds.add(value.getAsString());
                }
            }

            HashSet<Music> pool = MUSIC_BY_EVENT.computeIfAbsent(parent, ignored -> new HashSet<>());
            for (Music music : new HashSet<>(pool)) music.getEvents().remove(parent);
            pool.clear();
            HashSet<Music> allMusic = Music.MUSIC_BY_NAMESPACE.get(Music.ALL_MUSICS);
            if (allMusic != null) {
                for (Music music : allMusic) {
                    if (logicalSounds.contains(music.getIdentifier().toString())) music.addEvent(parent);
                }
            }

            if (links.has(parent.toString())) {
                HashSet<Identifier> parentLinks = new HashSet<>();
                for (JsonElement value : links.getAsJsonArray(parent.toString())) {
                    Identifier linked = Identifier.tryParse(value.getAsString());
                    if (linked != null) parentLinks.add(linked);
                }
                if (parentLinks.isEmpty()) EVENTS_OF_EVENT.remove(parent);
                else EVENTS_OF_EVENT.put(parent, parentLinks);
            } else {
                EVENTS_OF_EVENT.remove(parent);
            }

            if (silent.contains(parent)) SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.add(parent);
            else SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.remove(parent);
        }
    }

    /**
     * Projects fine-grained synthetic biome events onto their native parent
     * events. The generated pack therefore remains useful without the mod,
     * while the synthetic entries remain available for exact routing with it.
     */
    private static void applyPortableProjection(
            Map<Identifier, EventDefinition> definitions,
            MusicGraphSnapshot graph) {
        MusicVersionProfile profile = MusicVersionProfile.current();
        for (Map.Entry<Identifier, MusicVersionProfile.PortableEventPolicy> portable
                : profile.portableEvents().entrySet()) {
            Identifier parent = portable.getKey();
            EventDefinition existing = definitions.get(parent);
            if (existing != null && existing.explicitSilence) continue;

            EventDefinition projected = new EventDefinition();
            if (portable.getValue().includeBase()) {
                projected.sounds.addAll(resolveDefinitionSounds(parent, definitions, new HashSet<>()));
            }
            TreeSet<Identifier> members = new TreeSet<>();
            graph.nodes().forEach((nodeId, node) -> {
                if (node.parents().contains(parent)) members.add(nodeId);
            });
            for (Identifier member : members) {
                EventDefinition child = definitions.get(member);
                if (child == null || child.explicitSilence) continue;
                projected.sounds.addAll(resolveDefinitionSounds(member, definitions, new HashSet<>()));
            }
            definitions.put(parent, projected);
        }
    }

    private static TreeSet<String> resolveDefinitionSounds(
            Identifier event,
            Map<Identifier, EventDefinition> definitions,
            HashSet<Identifier> visited) {
        TreeSet<String> result = new TreeSet<>();
        if (!visited.add(event)) return result;
        EventDefinition definition = definitions.get(event);
        if (definition == null || definition.explicitSilence) return result;
        result.addAll(definition.sounds);
        for (Identifier linked : definition.events) {
            result.addAll(resolveDefinitionSounds(linked, definitions, visited));
        }
        return result;
    }

    private static final class EventDefinition {
        private final TreeSet<String> sounds = new TreeSet<>();
        private final TreeSet<Identifier> events = new TreeSet<>();
        private boolean explicitSilence;
    }

    private static void writeDefinitions(Map<Identifier, EventDefinition> definitions) {
        Map<String, TreeMap<String, JsonObject>> byNamespace = new HashMap<>();
        for (String namespace : NAMESPACES) byNamespace.put(namespace, new TreeMap<>());
        for (Map.Entry<Identifier, EventDefinition> entry : definitions.entrySet()) {
            Identifier eventId = entry.getKey();
            TreeMap<String, JsonObject> namespaceEvents = byNamespace.get(eventId.getNamespace());
            if (namespaceEvents == null) continue;
            JsonArray sounds = new JsonArray();
            for (String name : entry.getValue().sounds) {
                JsonObject sound = new JsonObject();
                sound.addProperty("name", name);
                sound.addProperty("stream", true);
                sounds.add(sound);
            }
            for (Identifier linked : entry.getValue().events) {
                JsonObject sound = new JsonObject();
                sound.addProperty("name", linked.getNamespace().equals(eventId.getNamespace())
                        ? linked.getPath() : linked.toString());
                sound.addProperty("type", "event");
                sounds.add(sound);
            }
            JsonObject event = new JsonObject();
            event.addProperty("category", "music");
            event.addProperty("replace", true);
            event.add("sounds", sounds);
            namespaceEvents.put(eventId.getPath(), event);
        }
        for (Map.Entry<String, TreeMap<String, JsonObject>> namespace : byNamespace.entrySet()) {
            Path soundPath = getSoundPath(namespace.getKey());
            if (soundPath == null) continue;
            JsonObject root = new JsonObject();
            namespace.getValue().forEach(root::add);
            try (PrintWriter out = new PrintWriter(new FileWriter(soundPath.toFile()))) {
                out.write(GSON.toJson(root));
                out.flush();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    /**
     * Writes incremental metadata compared to the vanilla Minecraft sound list.
     * The metadata records only the events that differ from vanilla, listing
     * which sounds were added and which were removed.
     * See META_FORMAT.md for the full format specification.
     */
    private static void writeMetadata() {
        // Build current state: full event identifier -> sorted set of sound names
        TreeMap<String, TreeSet<String>> currentState = new TreeMap<>();
        MUSIC_BY_EVENT.forEach((Identifier eventId, HashSet<Music> musics) -> {
            TreeSet<String> sounds = new TreeSet<>();
            for (Music music : musics) {
                sounds.add(music.getIdentifier().toString());
            }
            currentState.put(eventId.toString(), sounds);
        });

        // Load vanilla sounds for each namespace to compute the diff
        TreeMap<String, TreeSet<String>> vanillaState = new TreeMap<>();
        for (String namespace : NAMESPACES) {
            vanillaState.putAll(loadVanillaSounds(namespace));
        }

        // Compute changes: only include events that differ from vanilla
        TreeMap<String, JsonObject> changes = new TreeMap<>();

        for (Map.Entry<String, TreeSet<String>> entry : currentState.entrySet()) {
            String eventId = entry.getKey();
            TreeSet<String> current = entry.getValue();
            TreeSet<String> vanilla = vanillaState.getOrDefault(eventId, new TreeSet<>());

            TreeSet<String> added = new TreeSet<>(current);
            added.removeAll(vanilla);

            TreeSet<String> removed = new TreeSet<>(vanilla);
            removed.removeAll(current);

            if (!added.isEmpty() || !removed.isEmpty()) {
                JsonObject change = new JsonObject();
                if (!removed.isEmpty()) {
                    JsonArray removeArray = new JsonArray();
                    removed.forEach(removeArray::add);
                    change.add("remove", removeArray);
                }
                if (!added.isEmpty()) {
                    JsonArray addArray = new JsonArray();
                    added.forEach(addArray::add);
                    change.add("add", addArray);
                }
                changes.put(eventId, change);
            }
        }

        // Record events entirely removed by the user (present in vanilla but absent in current)
        for (Map.Entry<String, TreeSet<String>> entry : vanillaState.entrySet()) {
            String eventId = entry.getKey();
            if (!currentState.containsKey(eventId)) {
                TreeSet<String> vanilla = entry.getValue();
                if (!vanilla.isEmpty()) {
                    JsonObject change = new JsonObject();
                    JsonArray removeArray = new JsonArray();
                    vanilla.forEach(removeArray::add);
                    change.add("remove", removeArray);
                    changes.put(eventId, change);
                }
            }
        }

        // Build metadata document
        JsonObject meta = new JsonObject();
        meta.addProperty("format_version", 1);
        meta.addProperty("minecraft_version", SharedConstants.getCurrentVersion().name());
        JsonObject changesObj = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : changes.entrySet()) {
            changesObj.add(entry.getKey(), entry.getValue());
        }
        meta.add("changes", changesObj);

        TreeSet<String> silentEvents = new TreeSet<>();
        SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.forEach(event -> silentEvents.add(event.toString()));
        meta.add("silent_events", toJsonArray(silentEvents));

        TreeMap<String, TreeSet<Identifier>> eventLinks = new TreeMap<>();
        EVENTS_OF_EVENT.forEach((event, links) -> {
            if (!links.isEmpty()) eventLinks.put(event.toString(), new TreeSet<>(links));
        });
        meta.add("event_links", eventLinksToJson(eventLinks));

        Path metaPath = getMetadataPath();
        if (metaPath == null) return;
        try (PrintWriter out = new PrintWriter(new FileWriter(metaPath.toFile()))) {
            out.write(GSON.toJson(meta));
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JsonArray toJsonArray(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonObject eventLinksToJson(Map<String, TreeSet<Identifier>> eventLinks) {
        JsonObject object = new JsonObject();
        eventLinks.forEach((event, links) -> {
            JsonArray values = new JsonArray();
            links.forEach(link -> values.add(link.toString()));
            object.add(event, values);
        });
        return object;
    }

    /**
     * Loads the vanilla (lowest-priority) sounds.json for a given namespace
     * from the resource manager. The lowest-priority resource is vanilla Minecraft.
     * Returns a map: full event identifier string -> sorted set of sound names.
     */
    private static TreeMap<String, TreeSet<String>> loadVanillaSounds(String namespace) {
        TreeMap<String, TreeSet<String>> result = new TreeMap<>();
        try {
            Identifier soundsId = Identifier.fromNamespaceAndPath(namespace, "sounds.json");
            List<Resource> resources = Minecraft.getInstance()
                    .getResourceManager().getResourceStack(soundsId);
            if (resources.isEmpty()) return result;

            // getAllResources returns from lowest to highest priority;
            // the first entry is vanilla (base Minecraft pack).
            // Skip our own pack if it appears first.
            String ourPackName = RESOURCEPACK_PATH != null
                    ? RESOURCEPACK_PATH.getFileName().toString() : null;
            Resource vanillaResource = null;
            for (Resource resource : resources) {
                if (ourPackName == null || !resource.source().packId().contains(ourPackName)) {
                    vanillaResource = resource;
                    break;
                }
            }
            if (vanillaResource == null) return result;

            try (InputStreamReader reader = new InputStreamReader(vanillaResource.open())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) return result;
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (!entry.getKey().contains("music")) continue;
                    String eventId = namespace + ":" + entry.getKey();
                    TreeSet<String> sounds = getSoundStrings(namespace, entry);
                    result.put(eventId, sounds);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static @NonNull TreeSet<String> getSoundStrings(String namespace, Map.Entry<String, JsonElement> entry) {
        JsonObject eventObj = entry.getValue().getAsJsonObject();
        TreeSet<String> sounds = new TreeSet<>();
        if (eventObj.has("sounds")) {
            for (JsonElement sound : eventObj.getAsJsonArray("sounds")) {
                if (sound.isJsonObject()) {
                    JsonObject soundObj = sound.getAsJsonObject();
                    if (soundObj.has("name")) {
                        String name = soundObj.get("name").getAsString();
                        // vanilla sounds.json names are paths without namespace prefix
                        sounds.add(name.contains(":") ? name : namespace + ":" + name);
                    }
                } else if (sound.isJsonPrimitive()) {
                    String name = sound.getAsString();
                    sounds.add(name.contains(":") ? name : namespace + ":" + name);
                }
            }
        }
        return sounds;
    }

    public static void createResourcePack() {
        String resourcePackProfileName = findNextAvailablePath();

        try {
            Files.createDirectories(RESOURCEPACK_PATH);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        createMetaFile();
        createIcon();

        try {
            Files.createDirectories(ASSETS_PATH);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (String namespace : NAMESPACES) {
            final Path namespacePath = ASSETS_PATH.resolve(namespace);
            try {
                Files.createDirectories(namespacePath);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        WAS_CREATED = true;
        Minecraft.getInstance().getResourcePackRepository().reload();
        Minecraft.getInstance().getResourcePackRepository().addPack(resourcePackProfileName);
    }

    /**
     * Creates a new resource pack named after the migration versions
     * (e.g. {@code music_control_1.21.10_to_1.21.11}).
     *
     * @param fromVersion      the stored (old) Minecraft version.
     * @param applyImmediately if {@code true} the new pack is enabled immediately;
     *                         otherwise it is only created on disk.
     */
    private static void createMigrationResourcePack(String fromVersion, boolean applyImmediately) {
        String toVersion = SharedConstants.getCurrentVersion().name();
        String resourcePackProfileName = findNextAvailableMigrationPath(fromVersion, toVersion);

        try {
            Files.createDirectories(RESOURCEPACK_PATH);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        createMetaFile();
        createIcon();

        try {
            Files.createDirectories(ASSETS_PATH);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (String namespace : NAMESPACES) {
            final Path namespacePath = ASSETS_PATH.resolve(namespace);
            try {
                Files.createDirectories(namespacePath);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        Minecraft.getInstance().getResourcePackRepository().reload();
        if (applyImmediately) {
            WAS_CREATED = true;
            Minecraft.getInstance().getResourcePackRepository().addPack(resourcePackProfileName);
        }
    }

    /**
     * Finds the next available folder name for a migration pack,
     * e.g. {@code music_control_1.21.10_to_1.21.11} or
     *      {@code music_control_1.21.10_to_1.21.11_2}.
     */
    private static String findNextAvailableMigrationPath(String fromVersion, String toVersion) {
        // Replace dots with underscores so the folder name is filesystem-safe on all platforms
        String safe_from = fromVersion.replace('.', '_');
        String safe_to   = toVersion.replace('.', '_');
        String base = MusicControlClient.MOD_ID + "_" + safe_from + "_to_" + safe_to;

        final Path resourcePackDir = Minecraft.getInstance().getResourcePackDirectory();
        RESOURCEPACK_PATH = resourcePackDir.resolve(base);
        String resourcePackName = base;
        int i = 1;
        while (Files.exists(RESOURCEPACK_PATH)) {
            resourcePackName = base + "_" + (++i);
            RESOURCEPACK_PATH = resourcePackDir.resolve(resourcePackName);
        }

        ASSETS_PATH = RESOURCEPACK_PATH.resolve("assets");
        return "file/" + resourcePackName;
    }

    private static String findNextAvailablePath() {
        String resourcePackName = MusicControlClient.MOD_ID;
        int i = 0;

        final Path resourcePackDir = Minecraft.getInstance().getResourcePackDirectory();
        RESOURCEPACK_PATH = resourcePackDir.resolve(resourcePackName);

        while (Files.exists(RESOURCEPACK_PATH)) {
            resourcePackName = MusicControlClient.MOD_ID + "_" + ++i;
            RESOURCEPACK_PATH = resourcePackDir.resolve(resourcePackName);
        }

        ASSETS_PATH = RESOURCEPACK_PATH.resolve("assets");
        return "file/" + resourcePackName;
    }

    private static void setPaths() {
        Optional<String> selectedResourcePack = Minecraft.getInstance().getResourcePackRepository().getSelectedIds().stream()
                .filter(name -> name.startsWith(RESOURCEPACK_PROFILE_NAME)).findFirst();
        selectedResourcePack.ifPresent(name -> {
            RESOURCEPACK_PATH = Minecraft.getInstance().getResourcePackDirectory().resolve(name.substring(5));
            ASSETS_PATH = RESOURCEPACK_PATH.resolve("assets");
        });
    }

    private static void createMetaFile() {
        Path path = RESOURCEPACK_PATH.resolve("pack.mcmeta");
        if (!Files.exists(path)) {
            try {
                Files.createFile(path);

                JsonObject data = new JsonObject();
                JsonObject pack = new JsonObject();
                int format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
                pack.addProperty("min_format", format);
                pack.addProperty("max_format", format + 1);
                pack.addProperty("description", Component.translatable("music_control.metadata.description").getString());
                data.add("pack", pack);

                try (PrintWriter out = new PrintWriter(new FileWriter(path.toFile()))) {
                    out.write(GSON.toJson(data));
                    out.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void createIcon() {
        Path targetPath = RESOURCEPACK_PATH.resolve("pack.png");
        if (Files.exists(targetPath)) {
            return;
        }

        Optional<Path> sourcePath;
        Optional<ModContainer> modContainer = FabricLoader.getInstance().getModContainer(MusicControlClient.MOD_ID);
        if (modContainer.isPresent()) {
            Optional<String> iconPath = modContainer.get().getMetadata().getIconPath(400);
            if (iconPath.isPresent()) {
                sourcePath = modContainer.get().findPath(iconPath.get());
            } else {
                return;
            }
        } else {
            return;
        }

        if (sourcePath.isEmpty()) {
            return;
        }

        try {
            Files.copy(sourcePath.get(), targetPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path getSoundPath(final String namespace) {
        Path dirPath = ASSETS_PATH.resolve(namespace);
        Path filePath = dirPath.resolve("sounds.json");
        if (Files.exists(filePath)) {
            return filePath;
        }

        try {
            Files.createDirectories(dirPath);
            return Files.createFile(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static Path getMetadataPath() {
        if (RESOURCEPACK_PATH == null) return null;
        Path filePath = RESOURCEPACK_PATH.resolve("music_control_meta.json");
        if (Files.exists(filePath)) {
            return filePath;
        }
        try {
            return Files.createFile(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
