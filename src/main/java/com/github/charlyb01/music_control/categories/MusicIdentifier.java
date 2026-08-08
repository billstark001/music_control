package com.github.charlyb01.music_control.categories;

import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.client.MusicGraphManager;
import com.github.charlyb01.music_control.client.MusicGraphSnapshot;
import com.github.charlyb01.music_control.client.MusicVersionProfile;
import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.config.BiomeSwitchBehavior;
import com.github.charlyb01.music_control.config.ModConfig;
import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import static com.github.charlyb01.music_control.categories.Music.*;

public class MusicIdentifier {
    private MusicIdentifier() {}

    public record EventMusicSelection(boolean silent, HashSet<Music> pool) {
        public static EventMusicSelection silence() {
            return new EventMusicSelection(true, new HashSet<>());
        }

        public static EventMusicSelection playable(HashSet<Music> pool) {
            return new EventMusicSelection(false, pool);
        }
    }

    public static EventMusicSelection resolveEventMusic(
            final Identifier eventId, final RandomSource random) {
        EventMusicSelection selection = previewEventMusic(eventId, random);
        MusicControlClient.isCurrentEventEmpty = !selection.silent() && selection.pool().isEmpty();
        return selection;
    }

    /** Resolves the active graph pool without mutating playback state. */
    public static EventMusicSelection previewEventMusic(
            final Identifier eventId, final RandomSource random) {
        MusicGraphSnapshot graph = MusicGraphManager.current();
        Identifier vanillaEvent = SoundEventRegistry.selectedVanillaEvent() != null
                ? SoundEventRegistry.selectedVanillaEvent() : eventId;
        HashSet<Music> pool = getListFromEvent(vanillaEvent);

        for (Identifier node : SoundEventRegistry.selectedContextNodes()) {
            EventMusicSelection selection = applyNode(graph, node, pool, random);
            if (selection.silent()) return selection;
            pool = selection.pool();
        }

        return EventMusicSelection.playable(pool);
    }

    private static EventMusicSelection applyNode(
            MusicGraphSnapshot graph,
            Identifier node,
            HashSet<Music> parentPool,
            RandomSource random) {
        if (node == null) return EventMusicSelection.playable(parentPool);
        MusicGraphSnapshot.PoolSelection selection = graph.resolvePool(node, parentPool, random);
        return selection.silent()
                ? EventMusicSelection.silence()
                : EventMusicSelection.playable(selection.pool());
    }

    public static HashSet<Music> getListFromEvent(final Identifier eventId) {
        HashSet<Music> musics = new HashSet<>();
        if (eventId == null) return musics;
        HashSet<Identifier> checkedEvents = new HashSet<>();
        ArrayList<Identifier> eventsToCheck = new ArrayList<>();
        eventsToCheck.add(eventId);
        while (!eventsToCheck.isEmpty()) {
            Identifier event = eventsToCheck.removeFirst();
            if (checkedEvents.contains(event)) continue;

            HashSet<Music> eventMusics = MUSIC_BY_EVENT.get(event);
            if (eventMusics != null) musics.addAll(eventMusics);
            checkedEvents.add(event);
            if (EVENTS_OF_EVENT.containsKey(event)) {
                eventsToCheck.addAll(EVENTS_OF_EVENT.get(event));
            }
        }

        return musics;
    }

    public static Identifier getFromList(final HashSet<Music> musics, final RandomSource random) {
        return getFromList(musics, random, ModConfig.get().general.misc.musicQueue);
    }

    static Identifier getFromList(
            final HashSet<Music> musics,
            final RandomSource random,
            final int queueLength) {
        if (musics.isEmpty()) return null;

        Identifier music;
        int size = musics.size();

        int recentLimit = Math.min(queueLength, size);
        while (MusicCategories.RECENT_MUSICS.size() >= recentLimit) {
            MusicCategories.RECENT_MUSICS.poll();
        }

        ArrayList<Music> musicList = new ArrayList<>(musics);
        do {
            music = musicList.remove(random.nextInt(size--)).getIdentifier();
        } while (MusicCategories.RECENT_MUSICS.contains(music));

        MusicCategories.RECENT_MUSICS.add(music);
        MusicCategories.recordHistory(music);
        return music;
    }

    public static Identifier getFromCategory(final RandomSource random) {
        if (MUSIC_BY_NAMESPACE.containsKey(MusicControlClient.currentCategory)) {
            HashSet<Music> musics = MUSIC_BY_NAMESPACE.get(MusicControlClient.currentCategory);
            return getFromList(musics, random);
        } else {
            return null;
        }
    }

    private static Identifier profileEvent(MusicVersionProfile.Event event) {
        return MusicVersionProfile.current().event(event);
    }

    public static boolean shouldChangeMusic(
            final BiomeSwitchBehavior behavior,
            final Identifier eventId,
            final boolean contextChanged,
            final RandomSource random) {
        if (behavior == BiomeSwitchBehavior.NEVER) return false;
        if (!contextChanged) return false;
        if (behavior == BiomeSwitchBehavior.ALWAYS) return true;

        EventMusicSelection selection = previewEventMusic(eventId, random);
        if (selection.silent()) return MusicControlClient.currentMusic != null;
        HashSet<Music> nextEventList = selection.pool();

        // If both events are empty, we want to keep the fallback/misc music
        if (nextEventList.isEmpty() && MusicControlClient.isCurrentEventEmpty)
            return false;

        // If next event contains current music, we don't want to change it
        Music currentMusic = Music.getMusicFromIdentifier(MusicControlClient.currentMusic);
        return !nextEventList.contains(currentMusic);
    }

    public static boolean isDimension(final Identifier identifier) {
        return identifier.equals(profileEvent(MusicVersionProfile.Event.OVERWORLD))
                || identifier.equals(profileEvent(MusicVersionProfile.Event.NETHER))
                || identifier.equals(profileEvent(MusicVersionProfile.Event.END));
    }

    public static boolean isBiome(final Identifier identifier) {
        String path = identifier.getPath();
        return path.startsWith("music.overworld.")
                || path.startsWith("music.nether.")
                || path.startsWith("music.end.");
    }

    public static boolean isDisc(final Identifier identifier) {
        return identifier.getPath().startsWith("music_disc");
    }

    public static boolean isMisc(final Identifier identifier) {
        return identifier.getPath().startsWith("music.misc.");
    }
}
