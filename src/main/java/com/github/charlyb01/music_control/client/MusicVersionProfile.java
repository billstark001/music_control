package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.api.EmptyBehavior;
import com.github.charlyb01.music_control.api.NodeOptions;
import com.github.charlyb01.music_control.api.ParentMix;
import com.google.gson.Gson;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** Version-specific identifiers and the built-in v1 music graph. */
public final class MusicVersionProfile {
    private static final String RESOURCE_PATH = "/music_control/version_profile.json";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** Semantic activation slots; their concrete node IDs live only in JSON. */
    public enum Event {
        PLAYER_FLYING("player_flying"),
        PLAYER_DRIVING("player_driving"),
        PLAYER_RIDING("player_riding"),
        TIME_NIGHT("time_night"),
        WEATHER_RAIN("weather_rain"),
        WEATHER_THUNDER("weather_thunder"),
        OVERWORLD("overworld"),
        CREATIVE("creative"),
        NETHER("nether"),
        END("end");

        private final String profileKey;

        Event(String profileKey) {
            this.profileKey = profileKey;
        }
    }

    public record Node(Identifier id, NodeOptions options, Set<Identifier> parents) {}
    public record PortableEventPolicy(boolean includeBase) {}

    private final String minecraftVersion;
    private final EnumMap<Event, Identifier> events;
    private final Map<Identifier, Node> nodes;
    private final Map<Identifier, Identifier> biomeBindings;
    private final Map<Identifier, Identifier> dimensionBindings;
    private final Map<Identifier, Set<Identifier>> biomesByEvent;
    private final Set<Identifier> syntheticEvents;
    private final Map<Identifier, PortableEventPolicy> portableEvents;
    private final Map<Identifier, Set<Identifier>> portableMembers;
    private final Set<Identifier> hiddenEvents;

    private MusicVersionProfile(RawProfile raw) {
        if (raw == null) throw new IllegalStateException("The music version profile is empty");
        if (raw.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported music version profile schema: " + raw.schemaVersion);
        }
        if (raw.minecraftVersion == null || raw.minecraftVersion.isBlank()) {
            throw new IllegalStateException("The music version profile has no Minecraft version");
        }

        this.minecraftVersion = raw.minecraftVersion;
        this.events = parseEvents(raw.events);
        this.nodes = Collections.unmodifiableMap(parseNodes(raw.nodes));
        this.biomeBindings = Collections.unmodifiableMap(parseBindings(raw.biomes));
        this.dimensionBindings = Collections.unmodifiableMap(parseBindings(raw.dimensions, "dimensions"));
        Set<Identifier> synthetic = parseIdentifierSet(raw.syntheticEvents, "syntheticEvents");
        synthetic.addAll(this.biomeBindings.values());
        this.syntheticEvents = Collections.unmodifiableSet(synthetic);
        this.portableEvents = Collections.unmodifiableMap(parsePortableEvents(raw.portableEvents));
        this.hiddenEvents = Collections.unmodifiableSet(parseIdentifierSet(raw.hiddenEvents, "hiddenEvents"));

        Map<Identifier, Set<Identifier>> reverseBiomes = new LinkedHashMap<>();
        this.biomeBindings.forEach((biome, event) ->
                reverseBiomes.computeIfAbsent(event, ignored -> new LinkedHashSet<>()).add(biome));
        reverseBiomes.replaceAll((event, biomes) -> Collections.unmodifiableSet(biomes));
        this.biomesByEvent = Collections.unmodifiableMap(reverseBiomes);

        Map<Identifier, Set<Identifier>> reversePortable = new LinkedHashMap<>();
        for (Node node : this.nodes.values()) {
            for (Identifier parent : node.parents()) {
                if (this.portableEvents.containsKey(parent)) {
                    reversePortable.computeIfAbsent(parent, ignored -> new LinkedHashSet<>()).add(node.id());
                }
            }
        }
        reversePortable.replaceAll((event, members) -> Collections.unmodifiableSet(members));
        this.portableMembers = Collections.unmodifiableMap(reversePortable);
        validate();
    }

    public static MusicVersionProfile current() {
        return CurrentProfile.INSTANCE;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public Identifier event(Event event) {
        return events.get(event);
    }

    public Map<Identifier, Node> nodes() {
        return nodes;
    }

    public Map<Identifier, Identifier> biomeBindings() {
        return biomeBindings;
    }

    public Map<Identifier, Identifier> dimensionBindings() {
        return dimensionBindings;
    }

    public Set<Identifier> biomesForEvent(Identifier event) {
        return biomesByEvent.getOrDefault(event, Set.of());
    }

    public boolean isSyntheticEvent(Identifier event) {
        return syntheticEvents.contains(event);
    }

    public boolean isHidden(Identifier event) {
        return hiddenEvents.contains(event);
    }

    public Set<Identifier> hiddenEvents() {
        return hiddenEvents;
    }

    public Map<Identifier, PortableEventPolicy> portableEvents() {
        return portableEvents;
    }

    public Set<Identifier> portableMembers(Identifier parent) {
        return portableMembers.getOrDefault(parent, Set.of());
    }

    private static MusicVersionProfile load() {
        try (InputStream stream = MusicVersionProfile.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) throw new IllegalStateException("Missing version resource " + RESOURCE_PATH);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new MusicVersionProfile(new Gson().fromJson(reader, RawProfile.class));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + RESOURCE_PATH, exception);
        }
    }

    private static EnumMap<Event, Identifier> parseEvents(Map<String, String> rawEvents) {
        if (rawEvents == null) throw new IllegalStateException("The profile has no events object");
        EnumMap<Event, Identifier> parsed = new EnumMap<>(Event.class);
        for (Event event : Event.values()) {
            parsed.put(event, parseIdentifier(rawEvents.get(event.profileKey), "events." + event.profileKey));
        }
        return parsed;
    }

    private static Map<Identifier, Node> parseNodes(Map<String, RawNode> values) {
        if (values == null) throw new IllegalStateException("The profile has no nodes object");
        Map<Identifier, Node> parsed = new LinkedHashMap<>();
        values.forEach((idValue, value) -> {
            if (value == null) throw new IllegalStateException("Empty nodes." + idValue);
            Identifier id = parseIdentifier(idValue, "nodes key");
            ParentMix parentMix = parseEnum(
                    ParentMix.class, value.parentMix, ParentMix.EXCLUSIVE, "nodes." + idValue + ".parentMix");
            EmptyBehavior whenEmpty = parseEnum(
                    EmptyBehavior.class, value.whenEmpty, EmptyBehavior.VANILLA, "nodes." + idValue + ".whenEmpty");
            Set<Identifier> parents = Collections.unmodifiableSet(
                    parseIdentifierSet(value.parents, "nodes." + idValue + ".parents"));
            parsed.put(id, new Node(id, new NodeOptions(parentMix, whenEmpty), parents));
        });
        return parsed;
    }

    private static Map<Identifier, Identifier> parseBindings(Map<String, String> values) {
        return parseBindings(values, "biomes");
    }

    private static Map<Identifier, Identifier> parseBindings(Map<String, String> values, String field) {
        if (values == null) throw new IllegalStateException("The profile has no " + field + " object");
        Map<Identifier, Identifier> parsed = new LinkedHashMap<>();
        values.forEach((biome, node) -> parsed.put(
                parseIdentifier(biome, field + " key"), parseIdentifier(node, field + "." + biome)));
        return parsed;
    }

    private static Map<Identifier, PortableEventPolicy> parsePortableEvents(Map<String, RawPortableEvent> values) {
        if (values == null) throw new IllegalStateException("The profile has no portableEvents object");
        Map<Identifier, PortableEventPolicy> parsed = new LinkedHashMap<>();
        values.forEach((id, value) -> {
            if (value == null) throw new IllegalStateException("Empty portableEvents." + id);
            parsed.put(parseIdentifier(id, "portableEvents key"), new PortableEventPolicy(value.includeBase));
        });
        return parsed;
    }

    private static Set<Identifier> parseIdentifierSet(List<String> values, String field) {
        if (values == null) return new LinkedHashSet<>();
        Set<Identifier> parsed = new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            parsed.add(parseIdentifier(values.get(index), field + "[" + index + "]"));
        }
        return parsed;
    }

    private static Identifier parseIdentifier(String value, String field) {
        Identifier identifier = value == null ? null : Identifier.tryParse(value);
        if (identifier == null) throw new IllegalStateException("Invalid identifier in " + field + ": " + value);
        return identifier;
    }

    private static <T extends Enum<T>> T parseEnum(
            Class<T> type, String value, T defaultValue, String field) {
        if (value == null) return defaultValue;
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid " + field + ": " + value, exception);
        }
    }

    private void validate() {
        for (Identifier node : biomeBindings.values()) {
            if (!nodes.containsKey(node)) {
                throw new IllegalStateException("Biome binding references an undefined node: " + node);
            }
        }
        for (Identifier node : dimensionBindings.values()) {
            if (!nodes.containsKey(node)) {
                throw new IllegalStateException("Dimension binding references an undefined node: " + node);
            }
        }
        for (Node node : nodes.values()) {
            for (Identifier parent : node.parents()) {
                if (!nodes.containsKey(parent)) {
                    throw new IllegalStateException("Node " + node.id() + " references an undefined parent: " + parent);
                }
            }
        }
        for (Identifier portable : portableEvents.keySet()) {
            if (!portableMembers.containsKey(portable)) {
                throw new IllegalStateException("Portable event has no graph members: " + portable);
            }
        }
    }

    private static final class CurrentProfile {
        private static final MusicVersionProfile INSTANCE = load();
    }

    @SuppressWarnings("unused")
    private static final class RawProfile {
        private int schemaVersion;
        private String minecraftVersion;
        private Map<String, String> events;
        private List<String> syntheticEvents = new ArrayList<>();
        private Map<String, RawNode> nodes;
        private Map<String, String> biomes;
        private Map<String, String> dimensions;
        private Map<String, RawPortableEvent> portableEvents;
        private List<String> hiddenEvents = new ArrayList<>();
    }

    @SuppressWarnings("unused")
    private static final class RawNode {
        private String parentMix;
        private String whenEmpty;
        private List<String> parents = new ArrayList<>();
    }

    @SuppressWarnings("unused")
    private static final class RawPortableEvent {
        private boolean includeBase = true;
    }
}
