package com.github.charlyb01.music_control.categories;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class Music implements Comparable<Music> {
    public final static String ALL_MUSICS = "all";
    public final static String ALL_MUSIC_DISCS = "disc";
    public final static String DEFAULT_MUSICS = "default";

    public final static Identifier EMPTY_MUSIC_ID = SoundManager.EMPTY_SOUND.getLocation();
    public final static String EMPTY_MUSIC = EMPTY_MUSIC_ID.toString();

    public final static HashMap<String, HashSet<Music>> MUSIC_BY_NAMESPACE = new HashMap<>();
    public final static HashSet<Identifier> EVENTS = new HashSet<>();
    public final static HashSet<Identifier> BLACK_LISTED_EVENTS = new HashSet<>(List.of(Identifier.withDefaultNamespace("music.overworld.old_growth_taiga")));
    public final static HashMap<Identifier, HashSet<Music>> MUSIC_BY_EVENT = new HashMap<>();
    public final static HashMap<Identifier, HashSet<Identifier>> EVENTS_OF_EVENT = new HashMap<>();
    public final static Comparator<Identifier> TRANSLATED_ORDER = (Identifier a, Identifier b) ->
            String.CASE_INSENSITIVE_ORDER.compare(getTranslatedText(a).getString(), getTranslatedText(b).getString());

    private final static HashMap<Identifier, Component> TRANSLATION_CACHE = new HashMap<>();
    private static Language LAST_LANG_INSTANCE = Language.getInstance();

    private final Identifier identifier;
    private final HashSet<Identifier> events;

    public Music(final Identifier identifier) {
        this.identifier = identifier;
        this.events = new HashSet<>();
    }

    public static Music getMusicFromIdentifier(final Identifier identifier) {
        Optional<Music> music = MUSIC_BY_NAMESPACE.get(ALL_MUSICS).stream()
                .filter(music1 -> music1.getIdentifier().equals(identifier)).findAny();
        return music.orElse(null);
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public HashSet<Identifier> getEvents() {
        return events;
    }

    public void addEvent(final Identifier event) {
        if (MUSIC_BY_EVENT.containsKey(event)) {
            MUSIC_BY_EVENT.get(event).add(this);
            this.events.add(event);
        }
    }

    public void removeEvent(final Identifier event) {
        if (MUSIC_BY_EVENT.containsKey(event)) {
            MUSIC_BY_EVENT.get(event).remove(this);
            this.events.remove(event);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Music && this.identifier.equals(((Music) obj).identifier);
    }

    @Override
    public int compareTo(@NotNull Music music) {
        return this.identifier.compareTo(music.identifier);
    }

    public static Component getTranslatedText(Identifier identifier) {
        if (LAST_LANG_INSTANCE != Language.getInstance()) {
            TRANSLATION_CACHE.clear();
            LAST_LANG_INSTANCE = Language.getInstance();
        }

        if (TRANSLATION_CACHE.containsKey(identifier)) {
            return TRANSLATION_CACHE.get(identifier);
        }

        final String idString = identifier.toString();
        final String path = identifier.getPath();
        if (LAST_LANG_INSTANCE.has(idString)) {
            TRANSLATION_CACHE.put(identifier, Component.translatable(idString));

        // Get official biome translation for biomes' music
        } else if (MusicIdentifier.isBiome(identifier)) {
            TRANSLATION_CACHE.put(identifier, Component.translatable(
                    "music.format.biome", Component.translatable(
                            "biome." + identifier.getNamespace() + "." + path.split("\\.", 3)[2])));
        } else if (MusicIdentifier.isDimension(identifier)) {
            TRANSLATION_CACHE.put(identifier, Component.translatable(
                    "music.format.dimension", Component.translatable(path)));
        } else if (MusicIdentifier.isDisc(identifier)) {
            TRANSLATION_CACHE.put(identifier, Component.translatable(
                "music.format.disc", Component.translatable(path)));
        } else if (MusicIdentifier.isMisc(identifier)) {
            TRANSLATION_CACHE.put(identifier, Component.translatable(
                    "music.format.misc", Component.translatable(path)));
        }

        return TRANSLATION_CACHE.getOrDefault(identifier, Component.translatable(idString));
    }
}
