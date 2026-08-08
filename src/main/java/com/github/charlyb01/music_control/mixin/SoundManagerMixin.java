package com.github.charlyb01.music_control.mixin;

import com.github.charlyb01.music_control.categories.Music;
import com.github.charlyb01.music_control.client.MusicControlClient;
import com.github.charlyb01.music_control.client.MusicGraphManager;
import com.github.charlyb01.music_control.client.SoundEventRegistry;
import com.github.charlyb01.music_control.imixin.PauseResumeIMixin;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SoundManager.class)
public class SoundManagerMixin implements PauseResumeIMixin {
    @Shadow @Final private SoundEngine soundEngine;

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/client/sounds/SoundManager$Preparations;", at = @At("HEAD"))
    private void resetEventsOfEvent(ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<SoundManager.Preparations> cir) {
        Music.EVENTS_OF_EVENT.clear();
        SoundEventRegistry.EXPLICITLY_EMPTY_EVENTS.clear();
        MusicGraphManager.reload(resourceManager);
    }

    @Inject(method = "resume", at = @At("TAIL"))
    private void dontResumeIfPaused(CallbackInfo ci) {
        if (MusicControlClient.isPaused) {
            this.music_control$pauseMusic();
        }
    }

    @Override
    public void music_control$pauseMusic() {
        ((PauseResumeIMixin) this.soundEngine).music_control$pauseMusic();
    }

    @Override
    public void music_control$resumeMusic() {
        ((PauseResumeIMixin) this.soundEngine).music_control$resumeMusic();
    }
}
