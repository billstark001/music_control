package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.Utils;
import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.categories.MusicCategories;
import com.github.charlyb01.music_control.categories.MusicIdentifier;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.config.ModConfig;
import com.github.charlyb01.music_control.imixin.PauseResumeIMixin;
import com.github.charlyb01.music_control.imixin.MusicTrackerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;

import static com.github.charlyb01.music_control.categories.Music.EMPTY_MUSIC;

/**
 * Core mixin for {@link MusicManager}.
 *
 * <h2>Vanilla engine analysis (1.21.x)</h2>
 * <p>{@code MusicTracker.tick()} drives all background music through three stages:</p>
 * <ol>
 *   <li><b>Volume fade</b> – if the user music-volume slider differs from the tracked
 *       {@code volume} field, {@code canFadeTowardsVolume()} gradually adjusts it.
 *       While it is still converging the method returns early, so stages 2–3 are
 *       skipped that tick.  When volume reaches {@code ≤ 1e-4}, {@code stop()} is
 *       called internally and the method returns {@code false}.</li>
 *   <li><b>Track-end detection</b> – if {@code current} is no longer playing in
 *       {@code SoundManager}, vanilla nulls it and sets a fresh delay drawn from
 *       {@code MusicFrequency.getDelayBeforePlaying()}.  It also calls
 *       {@code shouldReplace()} on every tick while a track is playing: if the new
 *       {@code MusicSound} has {@code replaceCurrentMusic == true} and a different
 *       sound-event id, the current track is force-stopped immediately.</li>
 *   <li><b>Countdown + play</b> – {@code timeUntilNextSong} is decremented each tick;
 *       when it hits 0, {@code play(MusicSound)} is called.  {@code play()} assigns
 *       {@code current}, notifies the toast manager, and resets the counter to
 *       {@code Integer.MAX_VALUE}.</li>
 * </ol>
 *
 * <h2>Injection strategy</h2>
 * <p>We cancel {@code tick()} entirely from HEAD and reproduce only the parts we need,
 * routing all playback through our {@link #onPlay} hook.  This avoids fighting with
 * vanilla's {@code shouldReplace} and volume-fade paths, which would otherwise fire
 * behind our back and play a second concurrent track.</p>
 * <p>{@code play(MusicSound)} is also cancelled from HEAD so that <em>every</em>
 * invocation — whether from vanilla's countdown, our tick loop, or key handlers —
 * passes through {@link #onPlay} as the single authoritative gate for track selection
 * and playback start.</p>
 * <p>The mod-owned {@link #safeStop()} replaces all direct calls to
 * {@code SoundManager.stop()} + {@code current = null} to guarantee the pair is always
 * kept in sync and the toast is always dismissed.</p>
 *
 * <h2>Version-update checklist</h2>
 * <ul>
 *   <li>Verify {@code MusicTracker} field names ({@code current}, {@code volume},
 *       {@code timeUntilNextSong}, {@code shownToast}) still match after remapping.</li>
 *   <li>Check whether {@code play(MusicSound)} still unconditionally sets
 *       {@code timeUntilNextSong = Integer.MAX_VALUE} — our onPlay relies on this.</li>
 *   <li>Check whether {@code stop()} now also touches {@code timeUntilNextSong} (as of
 *       1.21.x it adds 100; we never call the shadowed {@code stop()} ourselves).</li>
 *   <li>Re-examine {@code canFadeTowardsVolume()} if Mojang changes the fade curve;
 *       our {@code SoundCategory.MUSIC} volume writes bypass this path entirely.</li>
 * </ul>
 */
@Mixin(MusicManager.class)
public abstract class MusicTrackerMixin implements MusicTrackerAccess {

    // ── Shadowed vanilla fields / methods ────────────────────────────────────

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RandomSource random;
    @Shadow private int nextSongDelay;
    @Shadow private SoundInstance currentMusic;
    /** Vanilla's per-tick volume scalar; we write this directly when fading. */
    @Shadow private float currentGain;
    /**
     * Vanilla tracks whether a "now playing" toast has been shown for the
     * current track.  We mirror its state so {@link MusicManager#showNowPlayingToastIfNeeded()}
     * (called by the HUD) stays correct.
     */
    @Shadow private boolean toastShown;

    @Shadow public abstract void startPlaying(net.minecraft.sounds.Music music);

    // ── Mod-owned state ───────────────────────────────────────────────────────

    /** {@code true} after the user has explicitly asked to display the current track. */
    @Unique private boolean displayPrompted = false;

    // =========================================================================
    // Injection points
    // =========================================================================

    /**
     * Replaces vanilla {@code play(MusicSound)}.
     *
     * <p>Vanilla {@code play()} is called in two situations we care about:
     * <ol>
     *   <li>from {@code tick()} when the countdown expires (our tick loop mirrors this),</li>
     *   <li>from our own {@link #tickFadeOut} when the fade completes.</li>
     * </ol>
     * Cancelling it here and re-implementing track selection lets us intercept
     * both cases with a single code path.</p>
     *
     * <p>The method is <em>not</em> cancelled when the mod is uninitialised or
     * there is no world, so vanilla playback works normally on the main menu.</p>
     */
    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void onPlay(net.minecraft.sounds.Music instance, CallbackInfo ci) {
        // Capture the event id before any cancellation so it is always up to date.
        MusicControlClient.currentEvent = (instance != null && instance.sound() != null)
                ? instance.sound().value().location() : null;

        if (!MusicControlClient.init || this.minecraft.level == null) return;

        // shouldPlay == false means the caller explicitly wanted silence this cycle.
        if (!MusicControlClient.shouldPlay) {
            MusicControlClient.shouldPlay = true;
            this.nextSongDelay = Integer.MAX_VALUE;
            ci.cancel();
            return;
        }

        boolean wasPlaying = this.minecraft.getSoundManager().isActive(this.currentMusic);
        safeStop();
        resolveNextMusic(instance, wasPlaying);
        SoundEngine.PlayResult result = startPlayback(instance);
        onTrackStart(result);

        this.nextSongDelay = Integer.MAX_VALUE;
        ci.cancel();
    }

    /**
     * Replaces vanilla {@code tick()}.
     *
     * <p>Vanilla's tick is cancelled in full while the mod is active so that its
     * {@code shouldReplace} check and built-in volume fade cannot race with ours.
     * We reproduce the parts we need (track-end detection, countdown, play trigger)
     * and add the fade state-machine and key-input dispatch on top.</p>
     *
     * <p>When the mod is not yet initialised or there is no world (e.g. main menu),
     * we fall through to vanilla by <em>not</em> cancelling the callback — but we
     * still run key-input and state-sync housekeeping at the end.</p>
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        net.minecraft.sounds.Music musicSound = this.minecraft.getSituationalMusic();

        if (MusicControlClient.init && this.minecraft.level != null && musicSound != null) {
            tickModActive(musicSound);
            ci.cancel();
        }

        // State housekeeping runs regardless of vanilla/mod path.
        syncState();
        holdTimerIfPaused();
    }

    @Override
    public void music_control$handlePendingKeyInputs() {
        if (this.minecraft.level == null) {
            MusicControlClient.previousMusic = false;
            MusicControlClient.nextMusic = false;
            MusicControlClient.pauseResume = false;
            MusicControlClient.previousCategory = false;
            MusicControlClient.nextCategory = false;
            MusicControlClient.printMusic = false;
            return;
        }

        handleKeyInputs();
    }

    // =========================================================================
    // Tick: mod-active path
    // =========================================================================

    /**
     * Full mod-controlled tick, executed only when the mod is initialised and a
     * world is loaded.
     *
     * <p>Order of operations:
     * <ol>
     *   <li>Run the fade state-machine if either fade duration is non-zero.</li>
     *   <li>Detect natural track end; update {@code current} and {@code timeUntilNextSong}.</li>
     *   <li>Trigger next-track when the countdown expires (skipped while paused or fading out).</li>
     * </ol>
     */
    @Unique
    private void tickModActive(net.minecraft.sounds.Music musicSound) {
        boolean hasFade = ModConfig.get().general.timer.fadeOutDuration != 0
                || ModConfig.get().general.timer.fadeInDuration != 0;

        // --- Fade state-machine ---
        if (hasFade && musicSound.sound() != null) {
            switch (MusicControlClient.currentState) {
                case FADE_IN:
                    if (this.currentMusic != null) {
                        if (!MusicControlClient.isPaused) tickFadeIn();
                        return; // consume the tick; countdown / play handled after fade completes
                    }
                    break;
                case FADE_OUT:
                    if (this.currentMusic != null) {
                        if (!MusicControlClient.isPaused) tickFadeOut(musicSound);
                        return;
                    }
                    break;
                case MINECRAFT:
                    // Biome-switch fade is only triggered in MINECRAFT state (CUSTOM is immune).
                    if (this.currentMusic != null && requiresBiomeFade(musicSound)) {
                        startBiomeFade(musicSound);
                        return;
                    }
                    break;
                default: // CUSTOM — no fade trigger
                    break;
            }
        }

        // --- Natural track-end detection (mirrors vanilla stage 2) ---
        if (this.currentMusic != null && !this.minecraft.getSoundManager().isActive(this.currentMusic)) {
            this.currentMusic = null;
            this.minecraft.getToastManager().hideNowPlayingToast();
            this.nextSongDelay = Math.min(this.nextSongDelay, musicSound.minDelay());
            // Abort any mid-flight fade; CUSTOM persists across track boundaries.
            if (MusicControlClient.currentState == MusicControlClient.State.FADE_IN
                    || MusicControlClient.currentState == MusicControlClient.State.FADE_OUT) {
                resetVolume();
            }
        }

        // --- Countdown + play trigger (mirrors vanilla stage 3) ---
        if (!MusicControlClient.isPaused
                && MusicControlClient.currentState != MusicControlClient.State.FADE_OUT
                && this.currentMusic == null && this.nextSongDelay-- <= 0) {
            this.startPlaying(musicSound); // routed through onPlay()
        }
    }

    // =========================================================================
    // Fade helpers
    // =========================================================================

    /**
     * Returns {@code true} if the current biome/event change warrants a fade
     * transition (only honoured in {@code MINECRAFT} state).
     *
     * <p>Reads {@code changeMusicOnBiomeSwitch} from config; delegates the
     * actual "does this event differ enough?" logic to
     * {@link MusicIdentifier#shouldChangeMusic}.</p>
     */
    @Unique
    private boolean requiresBiomeFade(net.minecraft.sounds.Music instance) {
        if (!ModConfig.get().general.timer.changeMusicOnBiomeSwitch) return false;
        return MusicIdentifier.shouldChangeMusic(instance.sound().value().location());
    }

    /**
     * Initiates a biome-switch transition based on configured fade durations.
     *
     * <ul>
     *   <li>No fade at all → immediate stop + play.</li>
     *   <li>Fade-in only → mute, stop, play, then ramp up in {@link #tickFadeIn}.</li>
     *   <li>Fade-out (with optional fade-in) → let {@link #tickFadeOut} handle it.</li>
     * </ul>
     */
    @Unique
    private void startBiomeFade(net.minecraft.sounds.Music musicSound) {
        boolean noFadeOut = ModConfig.get().general.timer.fadeOutDuration == 0;
        boolean noFadeIn  = ModConfig.get().general.timer.fadeInDuration == 0;

        if (noFadeOut && noFadeIn) {
            safeStop();
            this.startPlaying(musicSound);
        } else if (noFadeOut) {
            MusicControlClient.currentState = MusicControlClient.State.FADE_IN;
            this.currentGain = 0.f;
            this.minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, 0.f);
            safeStop();
            this.startPlaying(musicSound);
        } else {
            MusicControlClient.currentState = MusicControlClient.State.FADE_OUT;
        }
    }

    /**
     * Advances a fade-in by one tick.  Transitions to {@code MINECRAFT} when
     * {@code volume} reaches 1.
     *
     * <p>We write directly to {@code SoundCategory.MUSIC} volume rather than the
     * per-instance pitch/volume because vanilla's {@code canFadeTowardsVolume()}
     * path — which acts on the same category — is bypassed entirely while our tick
     * is in control.</p>
     */
    @Unique
    private void tickFadeIn() {
        this.currentGain = Math.min(1.f, this.currentGain + 1.f / (ModConfig.get().general.timer.fadeInDuration * 20));
        this.minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, this.currentGain);
        if (this.currentGain >= 1.f) {
            MusicControlClient.currentState = MusicControlClient.State.MINECRAFT;
        }
    }

    /**
     * Advances a fade-out by one tick.  When {@code volume} reaches 0, stops the
     * current track and immediately triggers the next one via {@link #startPlaying}.
     *
     * <p>After calling {@link #safeStop()}, {@code current} is {@code null}, so the
     * subsequent {@link #startPlaying} call goes through {@link #onPlay} cleanly without a
     * double-stop.</p>
     */
    @Unique
    private void tickFadeOut(net.minecraft.sounds.Music next) {
        this.currentGain = Math.max(0.f, this.currentGain - 1.f / (ModConfig.get().general.timer.fadeOutDuration * 20));
        this.minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, this.currentGain);

        if (this.currentGain <= 0.f) {
            safeStop();
            this.startPlaying(next); // onPlay will pick the next track
            if (ModConfig.get().general.timer.fadeInDuration == 0) {
                resetVolume();
            } else {
                MusicControlClient.currentState = MusicControlClient.State.FADE_IN;
            }
        }
    }

    // =========================================================================
    // Track start / stop
    // =========================================================================

    /**
     * Stops the current track, nulls {@code current}, and dismisses the toast.
     *
     * <p>{@code SoundManager.stop()} and {@code current = null} must always be
     * called together; this method is the single place that does so to prevent
     * two tracks from playing simultaneously.</p>
     */
    @Unique
    private void safeStop() {
        if (this.currentMusic == null) return;
        this.minecraft.getSoundManager().stop(this.currentMusic);
        this.currentMusic = null;
        this.minecraft.getToastManager().hideNowPlayingToast();
    }

    /**
     * Builds the {@link SoundInstance}, submits it to {@link SoundManager}, and
     * optionally shows the "now playing" toast.
     *
     * <p>Vanilla's own {@code play()} always shows the toast; we gate it behind
     * {@code showMusicToast} in config and only fire it when the system confirmed
     * the sound actually started ({@code PlayResult.STARTED}).</p>
     */
    @Unique
    private SoundEngine.PlayResult startPlayback(net.minecraft.sounds.Music instance) {
        if (MusicControlClient.currentMusic != null || (instance != null && instance.sound() != null)) {
            this.currentMusic = SimpleSoundInstance.forMusic(
                    MusicControlClient.currentMusic == null
                            ? instance.sound().value()
                            : SoundEvent.createVariableRangeEvent(MusicControlClient.currentMusic));
        }

        if (this.currentMusic != null && this.currentMusic.getSound() != SoundManager.EMPTY_SOUND) {
            return this.minecraft.getSoundManager().play(this.currentMusic);
        }
        return SoundEngine.PlayResult.NOT_STARTED;
    }

    /** Resets the {@code SoundCategory.MUSIC} volume to 1 and clears any fade state. */
    @Unique
    private void resetVolume() {
        MusicControlClient.currentState = MusicControlClient.State.MINECRAFT;
        this.currentGain = 1.f;
        this.minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, 1.f);
    }

    // =========================================================================
    // Track selection
    // =========================================================================

    /**
     * Resolves {@link MusicControlClient#currentMusic} for the upcoming track.
     *
     * <p>Priority order:
     * <ol>
     *   <li>Explicit GUI selection ({@code musicSelected}).</li>
     *   <li>Previous-track key (navigates the {@code PLAYED_MUSICS} history).</li>
     *   <li>Loop mode (keeps the existing {@code currentMusic} unchanged).</li>
     *   <li>Event-driven selection (DEFAULT_MUSICS category + known event).</li>
     *   <li>Random pick from the active category.</li>
     * </ol>
     *
     * @param instance  the {@link net.minecraft.sounds.Music} passed to {@code play()}; used as
     *                  fallback event source in event-driven selection
     * @param wasPlaying whether a track was audibly playing before the stop
     */
    @Unique
    private void resolveNextMusic(net.minecraft.sounds.Music instance, boolean wasPlaying) {
        if (MusicControlClient.musicSelected != null) {
            MusicControlClient.currentMusic = MusicControlClient.musicSelected;
            MusicControlClient.currentState = MusicControlClient.State.CUSTOM;
            MusicControlClient.musicSelected = null;
        } else if (MusicControlClient.previousMusic) {
            MusicControlClient.previousMusic = false;
            if (wasPlaying) MusicCategories.PLAYED_MUSICS.pollLast();
            Identifier prev = MusicCategories.PLAYED_MUSICS.peekLast();
            if (prev != null) MusicControlClient.currentMusic = prev;
        } else if (MusicControlClient.loopMusic) {
            // keep currentMusic as-is
        } else if (MusicControlClient.currentEvent != null
                && MusicControlClient.currentCategory.equals(Music.DEFAULT_MUSICS)
                && Music.MUSIC_BY_EVENT.containsKey(MusicControlClient.currentEvent)) {
            resolveEventMusic();
        } else {
            MusicControlClient.currentMusic = MusicIdentifier.getFromCategory(this.random);
        }
    }

    /**
     * Picks a track from the event-driven music pool.
     *
     * <p>Falls back to {@link MusicIdentifier#getFallback} when the pool for the
     * current event is empty (e.g. no custom music registered for this biome).</p>
     */
    @Unique
    private void resolveEventMusic() {
        boolean creative = this.minecraft.player != null && this.minecraft.player.isCreative();
        HashSet<Music> pool = MusicIdentifier.getListFromEvent(
                MusicControlClient.currentEvent, this.minecraft.player, this.minecraft.level, this.random);

        MusicControlClient.currentMusic = pool.isEmpty() && this.minecraft.level != null
                ? MusicIdentifier.getFallback(this.minecraft.level.dimension(), creative, this.random)
                : MusicIdentifier.getFromList(pool, this.random);
    }

    // =========================================================================
    // Tick housekeeping (run every tick, mod-active or not)
    // =========================================================================

    /** Dispatches all pending key-press actions in a fixed priority order. */
    @Unique
    private void handleKeyInputs() {
        handlePreviousMusicKey();
        handleNextMusicKey();
        handleResumePauseKey();
        handleChangeCategoryKey();
        handleDisplayMusicKey();
    }

    /**
     * Guards state consistency after the per-tick logic runs.
     *
     * <ul>
     *   <li>{@code CUSTOM} — persists as long as a world is loaded; reset when leaving.</li>
     *   <li>{@code FADE_IN/OUT} — aborted (with volume restore) if the world disappears
     *       or the sound stopped outside our control.</li>
     *   <li>{@code MINECRAFT} — never needs correction here.</li>
     * </ul>
     */
    @Unique
    private void syncState() {
        if (MusicControlClient.currentState == MusicControlClient.State.MINECRAFT) return;
        boolean noWorld = !MusicControlClient.init || this.minecraft.level == null;

        switch (MusicControlClient.currentState) {
            case CUSTOM:
                if (noWorld) MusicControlClient.currentState = MusicControlClient.State.MINECRAFT;
                break;
            case FADE_IN:
            case FADE_OUT:
                if (noWorld || this.currentMusic == null || !this.minecraft.getSoundManager().isActive(this.currentMusic))
                    resetVolume();
                break;
            default:
                break;
        }
    }

    /**
     * Compensates for the automatic decrement of {@code timeUntilNextSong} while
     * music is paused, keeping the countdown frozen until playback resumes.
     *
     * <p>The counter is only incremented when no sound is actually playing, which
     * avoids double-counting when a live (audible) track is paused at the OS level.</p>
     */
    @Unique
    private void holdTimerIfPaused() {
        if (MusicControlClient.isPaused
                && (this.currentMusic == null || !this.minecraft.getSoundManager().isActive(this.currentMusic))) {
            this.nextSongDelay++;
        }
    }

    // =========================================================================
    // Display helpers
    // =========================================================================

    /** Shows the track name on track start if auto-display or category-change is active. */
    @Unique
    private void onTrackStart(SoundEngine.PlayResult result) {
        if (ModConfig.get().cosmetics.display.atMusicStart || MusicControlClient.categoryChanged) {
            printMusic();
        }
        showToastOnPlay(result);
        if (MusicControlClient.categoryChanged) {
            MusicControlClient.categoryChanged = false;
        }
    }

    @Unique
    private void showToastOnPlay(SoundEngine.PlayResult result) {
        if (ModConfig.get().cosmetics.display.showMusicToast && result == SoundEngine.PlayResult.STARTED) {
            this.minecraft.getToastManager().showNowPlayingToast();
            this.toastShown = true;
        }
    }

    @Unique
    private void printPaused() {
        Utils.print(this.minecraft, Component.translatable("music.paused"));
    }

    /**
     * Prints the current track name (or a "nothing playing" message) to the HUD.
     *
     * <p>When {@code displayPrompted} is {@code true} but no track is playing, the
     * remaining time is shown if the config option is enabled.</p>
     */
    @Unique
    private void printMusic() {
        if (this.minecraft.level == null) return;

        final String id = (this.currentMusic == null || this.currentMusic.getSound() == null)
                ? EMPTY_MUSIC
                : this.currentMusic.getSound().getLocation().toString();

        if (id.equals(EMPTY_MUSIC)) {
            if (!this.displayPrompted) return;
            this.displayPrompted = false;
            if (ModConfig.get().cosmetics.display.remainingSeconds) {
                Utils.print(this.minecraft, Component.translatable("music.no_playing_with_time",
                        String.valueOf(this.nextSongDelay / 20.0)));
            } else {
                Utils.print(this.minecraft, Component.translatable("music.no_playing"));
            }
        } else {
            Component category = Component.translatableWithFallback(
                    "music.category." + MusicControlClient.currentCategory,
                    MusicControlClient.currentCategory.toUpperCase().replace('_', ' '));
            Component music   = Component.translatable(id);
            Component content = MusicControlClient.categoryChanged
                    ? Component.translatable("music.format.category", category, music)
                    : music;
            Utils.print(this.minecraft, Component.translatable("record.nowPlaying", content));
        }
    }

    // =========================================================================
    // Key-press handlers
    // =========================================================================

    /**
     * Plays the previous track from history.
     * Sets state to {@code CUSTOM} so biome changes will not interrupt it.
     */
    @Unique
    private void handlePreviousMusicKey() {
        if (!MusicControlClient.previousMusic) return;
        if (MusicControlClient.isPaused) {
            MusicControlClient.previousMusic = false;
            printPaused();
        } else {
            this.displayPrompted = ModConfig.get().cosmetics.display.atMusicStart;
            MusicControlClient.currentState = MusicControlClient.State.CUSTOM;
            this.startPlaying(null);
        }
    }

    /**
     * Skips to a random next track.
     * Resets state to {@code MINECRAFT} so normal event-driven selection resumes.
     */
    @Unique
    private void handleNextMusicKey() {
        if (!MusicControlClient.nextMusic) return;
        MusicControlClient.nextMusic = false;
        MusicControlClient.loopMusic = false;
        if (MusicControlClient.isPaused) {
            printPaused();
        } else {
            this.displayPrompted = ModConfig.get().cosmetics.display.atMusicStart;
            MusicControlClient.currentState = MusicControlClient.State.MINECRAFT;
            this.startPlaying(this.minecraft.getSituationalMusic());
        }
    }

    /**
     * Toggles playback pause / resume.
     *
     * <ul>
     *   <li><b>Pause</b>: suspends audio via {@link PauseResumeIMixin}, clears the
     *       toast so it is not shown while silent.</li>
     *   <li><b>Resume</b>: restores audio; shows the toast again unless a fade-out is
     *       in progress (the song is about to change anyway).</li>
     * </ul>
     */
    @Unique
    private void handleResumePauseKey() {
        if (!MusicControlClient.pauseResume) return;
        MusicControlClient.pauseResume = false;

        if (MusicControlClient.isPaused) {
            MusicControlClient.isPaused = false;
            ((PauseResumeIMixin) this.minecraft.getSoundManager()).music_control$resumeMusic();
            if (this.minecraft.player != null) {
                Utils.print(this.minecraft, Component.translatable("music.play"));
                if (MusicControlClient.currentState != MusicControlClient.State.FADE_OUT
                        && this.currentMusic != null && this.minecraft.getSoundManager().isActive(this.currentMusic)) {
                    this.showToastOnPlay(SoundEngine.PlayResult.STARTED);
                }
            }
        } else {
            MusicControlClient.isPaused = true;
            ((PauseResumeIMixin) this.minecraft.getSoundManager()).music_control$pauseMusic();
            this.minecraft.getToastManager().hideNowPlayingToast();
            this.toastShown = false;
            if (this.minecraft.player != null) Utils.print(this.minecraft, Component.translatable("music.pause"));
        }
    }

    /**
     * Switches the active music category and starts a new track immediately.
     *
     * <p>Resets state to {@code MINECRAFT} because category selection is an
     * event-driven (not user-specific-song) action.</p>
     */
    @Unique
    private void handleChangeCategoryKey() {
        if (MusicControlClient.nextCategory == MusicControlClient.previousCategory) {
            MusicControlClient.nextCategory = MusicControlClient.previousCategory = false;
            return;
        }
        if (MusicControlClient.isPaused) {
            printPaused();
        } else {
            MusicControlClient.categoryChanged = true;
            MusicControlClient.currentState = MusicControlClient.State.MINECRAFT;
            MusicCategories.changeCategory(MusicControlClient.nextCategory);
            safeStop();
            net.minecraft.sounds.Music musicSound = this.minecraft.getSituationalMusic();
            if (musicSound != null) {
                this.startPlaying(musicSound);
            } else {
                this.nextSongDelay = Math.max(this.nextSongDelay, 100);
            }
        }
        MusicControlClient.nextCategory = MusicControlClient.previousCategory = false;
    }

    /** Prints the current track on demand and arms {@link #displayPrompted}. */
    @Unique
    private void handleDisplayMusicKey() {
        if (!MusicControlClient.printMusic) return;
        MusicControlClient.printMusic = false;
        this.displayPrompted = true;
        printMusic();
    }
}
