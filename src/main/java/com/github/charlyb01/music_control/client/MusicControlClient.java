package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.config.ModConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class MusicControlClient implements ClientModInitializer {
    public static final String MOD_ID = "music_control";

    public enum State {
        MINECRAFT,
        CUSTOM,
        FADE_IN,
        FADE_OUT,
    }

    public static boolean init = false;

    /**
     * The playback state machine.
     *
     * <ul>
     *   <li>{@code MINECRAFT} – the mod defers track selection and biome-music changes
     *       to Minecraft's event system; no extra interception beyond routing through
     *       {@code MusicTrackerMixin.onPlay}.</li>
     *   <li>{@code CUSTOM} – the user has explicitly picked a track (e.g. via the
     *       previous-track key or the GUI); biome changes are suppressed so the chosen
     *       song plays to completion undisturbed.  Persists across track boundaries
     *       until the world is left.</li>
     *   <li>{@code FADE_IN} – the new track is playing but {@code SoundCategory.MUSIC}
     *       volume is ramping 0 → 1 over {@code fadeInDuration} seconds.</li>
     *   <li>{@code FADE_OUT} – the current track's volume is ramping 1 → 0 over
     *       {@code fadeOutDuration} seconds; the next track starts once volume hits 0.</li>
     * </ul>
     *
     * <p>Valid transitions:</p>
     * <pre>
     *   MINECRAFT ──(biome switch, fade-out configured)──▶ FADE_OUT
     *   MINECRAFT ──(biome switch, fade-in only)────────▶ FADE_IN
     *   FADE_OUT  ──(volume == 0, fade-in configured)───▶ FADE_IN
     *   FADE_OUT  ──(volume == 0, no fade-in)───────────▶ MINECRAFT
     *   FADE_IN   ──(volume == 1)────────────────────────▶ MINECRAFT
     *   any       ──(world unloaded / unexpected stop)──▶ MINECRAFT
     * </pre>
     *
     * <p>{@code CUSTOM} is entered by the previous-track key and exits only when
     * the world is left.</p>
     */
    public static State currentState = State.MINECRAFT;

    /**
     * Whether the user has paused music playback.
     *
     * <p>While {@code true} the entire state machine is frozen:
     * fade ticks do not advance, the next-track countdown is held in place, the
     * next-track trigger is suppressed, and any visible toast has been dismissed.
     * Set and cleared exclusively by the pause/resume key handler.</p>
     */
    public static boolean isPaused = false;
    public static boolean shouldPlay = true;
    public static boolean isCurrentEventEmpty = false;
    public static boolean categoryChanged = false;
    public static Identifier musicSelected;

    public static Identifier currentMusic;
    public static Identifier currentEvent;
    public static String currentCategory;

    public static boolean previousMusic = false;
    public static boolean nextMusic = false;
    public static boolean pauseResume = false;
    public static boolean loopMusic = false;
    public static boolean previousCategory = false;
    public static boolean nextCategory = false;
    public static boolean printMusic = false;

    @Override
    public void onInitializeClient() {
        SoundEventRegistry.init();
        AutoConfig.register(ModConfig.class, PartitioningSerializer.wrap(GsonConfigSerializer::new));
        MusicKeyBinding.register();

        currentCategory = ModConfig.get().general.misc.musicCategoryStart;
    }

    public static Identifier id(final String path) {
        return Identifier.of(MOD_ID, path);
    }
}
