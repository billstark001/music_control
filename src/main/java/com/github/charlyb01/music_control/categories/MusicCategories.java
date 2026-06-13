package com.github.charlyb01.music_control.categories;

import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.mixin.SoundSetAccessor;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

import static com.github.charlyb01.music_control.categories.Music.*;


public class MusicCategories {
    public final static ArrayList<String> CATEGORIES = new ArrayList<>(Arrays.asList(ALL_MUSICS, DEFAULT_MUSICS, ALL_MUSIC_DISCS));
    public final static ArrayList<String> NAMESPACES = new ArrayList<>(List.of("minecraft"));
    public final static LinkedList<Identifier> PLAYED_MUSICS = new LinkedList<>();

    private MusicCategories() {}

    public static void init(final Minecraft client) {
        if (MusicControlClient.init) {
            PLAYED_MUSICS.clear();
            MUSIC_BY_NAMESPACE.clear();
            MUSIC_BY_EVENT.clear();
            EVENTS.clear();

            SoundEventRegistry.BIOME_MUSIC_MAP.clear();
        } else {
            MusicControlClient.init = true;
        }

        RandomSource random = RandomSource.createThreadLocalInstance();
        HashSet<Music> musics = new HashSet<>();
        HashSet<Music> discs = new HashSet<>();
        MUSIC_BY_NAMESPACE.put(ALL_MUSICS, musics);
        MUSIC_BY_NAMESPACE.put(ALL_MUSIC_DISCS, discs);

        for (SoundEvent soundEvent : BuiltInRegistries.SOUND_EVENT) {
            Identifier event = soundEvent.location();
            if (event.getPath().contains("music")) {
                if (!EVENTS.contains(event) && !BLACK_LISTED_EVENTS.contains(event)) {
                    EVENTS.add(event);
                    MUSIC_BY_EVENT.put(event, new HashSet<>());
                }

                String[] split = event.getPath().split("\\.");
                ResourceKey<Biome> biomeRegistryKey;
                if (split.length > 0
                        && (biomeRegistryKey = SoundEventRegistry.NAME_BIOME_MAP.get(
                                Identifier.fromNamespaceAndPath(event.getNamespace(), split[split.length-1]))) != null) {
                    SoundEventRegistry.BIOME_MUSIC_MAP.put(biomeRegistryKey, soundEvent);
                }
            }
        }

        for (Identifier eventIdentifier : client.getSoundManager().getAvailableSounds()) {
            if (client.getSoundManager().getSoundEvent(eventIdentifier) != null) {
                List<Weighted<Sound>> sounds = ((SoundSetAccessor) Objects.requireNonNull(client.getSoundManager().getSoundEvent(eventIdentifier))).getList();
                String namespace = eventIdentifier.getNamespace();
                String path = eventIdentifier.getPath();

                if (!path.contains("music")) continue;

                for (Weighted<Sound> soundContainer : sounds) {
                    if (!(soundContainer instanceof Sound)) continue;

                    Identifier musicIdentifier = soundContainer.getSound(random).getLocation();
                    Music music = new Music(musicIdentifier);
                    Optional<Music> optionalMusic = musics.stream()
                            .filter(music1 -> music1.getIdentifier().equals(musicIdentifier)).findAny();

                    if (optionalMusic.isPresent()) {
                        music = optionalMusic.get();
                        music.addEvent(eventIdentifier);
                        continue;
                    }
                    if (path.contains("music_disc")) {
                        discs.add(music);
                    }

                    musics.add(music);
                    music.addEvent(eventIdentifier);

                    if (!namespace.equals("minecraft")) {
                        HashSet<Music> customMusics = MUSIC_BY_NAMESPACE.computeIfAbsent(namespace, k -> new HashSet<>());
                        customMusics.add(music);

                        if (!CATEGORIES.contains(namespace)) {
                            CATEGORIES.add(namespace);
                            NAMESPACES.add(namespace);
                        }
                    }
                }
            }
        }

        if (!CATEGORIES.contains(MusicControlClient.currentCategory)) {
            MusicControlClient.currentCategory = DEFAULT_MUSICS;
        }
    }

    public static void changeCategory(final boolean nextCategory) {
        int index = nextCategory
                ? (CATEGORIES.indexOf(MusicControlClient.currentCategory) + 1) % CATEGORIES.size()
                : (CATEGORIES.indexOf(MusicControlClient.currentCategory) - 1);
        if (index < 0) {
            index =  CATEGORIES.size() - 1;
        }

        MusicControlClient.currentCategory = CATEGORIES.get(index);
    }
}
