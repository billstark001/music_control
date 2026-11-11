package com.github.charlyb01.music_control.categories;

import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.ResourcePackUtils;
import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.client.MusicGraphManager;
import com.github.charlyb01.music_control.mixin.SoundSetAccessor;
import com.github.charlyb01.music_control.platform.WorldCompat;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

import static com.github.charlyb01.music_control.categories.Music.*;


public class MusicCategories {
    public final static ArrayList<String> CATEGORIES = new ArrayList<>(Arrays.asList(ALL_MUSICS, DEFAULT_MUSICS, ALL_MUSIC_DISCS));
    public final static ArrayList<String> NAMESPACES = new ArrayList<>(List.of("minecraft"));
    /** Session playback history exposed by the History screen. */
    public final static LinkedList<Identifier> PLAYED_MUSICS = new LinkedList<>();
    /** Bounded no-repeat window; deliberately independent from visible history. */
    public final static LinkedList<Identifier> RECENT_MUSICS = new LinkedList<>();

    private MusicCategories() {}

    public static void init(final Minecraft client) {
        if (MusicControlClient.init) {
            RECENT_MUSICS.clear();
            MUSIC_BY_NAMESPACE.clear();
            MUSIC_BY_EVENT.clear();
            EVENTS.clear();

        } else {
            MusicControlClient.init = true;
        }

        RandomSource random = WorldCompat.createThreadLocalRandom();
        HashSet<Music> musics = new HashSet<>();
        HashSet<Music> discs = new HashSet<>();
        MUSIC_BY_NAMESPACE.put(ALL_MUSICS, musics);
        MUSIC_BY_NAMESPACE.put(ALL_MUSIC_DISCS, discs);

        for (SoundEvent soundEvent : BuiltInRegistries.SOUND_EVENT) {
            Identifier event = soundEvent.location();
            if (event.getPath().contains("music") && SoundEventRegistry.isEventAvailable(event)) {
                MUSIC_BY_EVENT.computeIfAbsent(event, ignored -> new HashSet<>());
                if (!EVENTS.contains(event) && !MusicGraphManager.current().isHidden(event)) {
                    EVENTS.add(event);
                }
            }
        }
        MusicGraphManager.current().nodes().forEach((nodeId, node) -> {
            MUSIC_BY_EVENT.computeIfAbsent(nodeId, ignored -> new HashSet<>());
            if (!EVENTS.contains(nodeId) && !MusicGraphManager.current().isHidden(nodeId)) EVENTS.add(nodeId);
        });
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

        ResourcePackUtils.restoreLogicalPortableEvents();

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

    /** Records one visible history entry, moving repeats to the newest position. */
    public static void recordHistory(Identifier music) {
        PLAYED_MUSICS.remove(music);
        PLAYED_MUSICS.add(music);
    }
}
