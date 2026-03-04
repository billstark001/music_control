package com.github.charlyb01.music_control;

import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
        return MinecraftClient.getInstance().getResourcePackManager().getIds().stream().anyMatch(
                name -> name.startsWith(RESOURCEPACK_PROFILE_NAME));
    }

    public static boolean wasCreatedOrIsEnabled() {
        return WAS_CREATED || MinecraftClient.getInstance().getResourcePackManager().getEnabledIds().stream().anyMatch(
                name -> name.startsWith(RESOURCEPACK_PROFILE_NAME));
    }

    public static void writeConfig() {
        if (WAS_CREATED) {
            WAS_CREATED = false;
        } else if (RESOURCEPACK_PATH == null) {
            setPaths();
        }

        // Build sorted sound event map: namespace -> sorted event path -> JsonObject
        Map<String, TreeMap<String, JsonObject>> sortedByNamespace = new HashMap<>();
        for (String namespace : NAMESPACES) {
            sortedByNamespace.put(namespace, new TreeMap<>());
        }

        MUSIC_BY_EVENT.forEach((Identifier eventId, HashSet<Music> musics) -> {
            // Sort sound names for deterministic output
            List<String> soundNames = new ArrayList<>();
            for (Music music : musics) {
                soundNames.add(music.getIdentifier().toString());
            }
            soundNames.sort(String::compareTo);

            JsonArray sounds = new JsonArray();
            for (String name : soundNames) {
                JsonObject fileSound = new JsonObject();
                fileSound.addProperty("name", name);
                fileSound.addProperty("stream", true);
                sounds.add(fileSound);
            }

            HashSet<Identifier> events = EVENTS_OF_EVENT.get(eventId);
            if (events != null) {
                List<String> eventNames = new ArrayList<>();
                for (Identifier otherId : events) {
                    eventNames.add(otherId.getPath());
                }
                eventNames.sort(String::compareTo);
                for (String name : eventNames) {
                    JsonObject eventSound = new JsonObject();
                    eventSound.addProperty("name", name);
                    eventSound.addProperty("type", "event");
                    sounds.add(eventSound);
                }
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
        meta.addProperty("minecraft_version", SharedConstants.getGameVersion().getName());
        JsonObject changesObj = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : changes.entrySet()) {
            changesObj.add(entry.getKey(), entry.getValue());
        }
        meta.add("changes", changesObj);

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
     * Loads the vanilla (lowest-priority) sounds.json for a given namespace
     * from the resource manager. The lowest-priority resource is vanilla Minecraft.
     * Returns a map: full event identifier string -> sorted set of sound names.
     */
    private static TreeMap<String, TreeSet<String>> loadVanillaSounds(String namespace) {
        TreeMap<String, TreeSet<String>> result = new TreeMap<>();
        try {
            Identifier soundsId = Identifier.of(namespace, "sounds.json");
            List<Resource> resources = MinecraftClient.getInstance()
                    .getResourceManager().getAllResources(soundsId);
            if (resources.isEmpty()) return result;

            // getAllResources returns from lowest to highest priority;
            // the first entry is vanilla (base Minecraft pack).
            // Skip our own pack if it appears first.
            String ourPackName = RESOURCEPACK_PATH != null
                    ? RESOURCEPACK_PATH.getFileName().toString() : null;
            Resource vanillaResource = null;
            for (Resource resource : resources) {
                if (ourPackName == null || !resource.getResourcePackName().contains(ourPackName)) {
                    vanillaResource = resource;
                    break;
                }
            }
            if (vanillaResource == null) return result;

            try (InputStreamReader reader = new InputStreamReader(vanillaResource.getInputStream())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) return result;
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    String eventId = namespace + ":" + entry.getKey();
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
                    result.put(eventId, sounds);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
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
        MinecraftClient.getInstance().getResourcePackManager().scanPacks();
        MinecraftClient.getInstance().getResourcePackManager().enable(resourcePackProfileName);
    }

    private static String findNextAvailablePath() {
        String resourcePackName = MusicControlClient.MOD_ID;
        int i = 0;

        final Path resourcePackDir = MinecraftClient.getInstance().getResourcePackDir();
        RESOURCEPACK_PATH = resourcePackDir.resolve(resourcePackName);

        while (Files.exists(RESOURCEPACK_PATH)) {
            resourcePackName = MusicControlClient.MOD_ID + "_" + ++i;
            RESOURCEPACK_PATH = resourcePackDir.resolve(resourcePackName);
        }

        ASSETS_PATH = RESOURCEPACK_PATH.resolve("assets");
        return "file/" + resourcePackName;
    }

    private static void setPaths() {
        Optional<String> selectedResourcePack = MinecraftClient.getInstance().getResourcePackManager().getEnabledIds().stream()
                .filter(name -> name.startsWith(RESOURCEPACK_PROFILE_NAME)).findFirst();
        selectedResourcePack.ifPresent(name -> {
            RESOURCEPACK_PATH = MinecraftClient.getInstance().getResourcePackDir().resolve(name.substring(5));
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
                int format = SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES).major();
                pack.addProperty("min_format", format);
                pack.addProperty("max_format", format + 1);
                pack.addProperty("description", Text.translatable("music_control.metadata.description").getString());
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
