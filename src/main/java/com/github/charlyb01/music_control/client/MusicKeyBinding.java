package com.github.charlyb01.music_control.client;

import com.github.charlyb01.music_control.Utils;
import com.github.charlyb01.music_control.imixin.GameOptionsAccess;
import com.github.charlyb01.music_control.imixin.MusicTrackerAccess;
import com.mojang.blaze3d.platform.InputConstants;
import com.github.charlyb01.music_control.config.ModConfig;
import com.github.charlyb01.music_control.gui.MusicControlGUI;
import com.github.charlyb01.music_control.gui.MusicControlScreen;
import com.github.charlyb01.music_control.platform.ClientCompat;
import com.github.charlyb01.music_control.platform.KeyMappingCompat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;

public class MusicKeyBinding {
    private static final KeyMapping.Category mainCategory = KeyMapping.Category.register(MusicControlClient.id("main"));

    private static KeyMapping previousMusic;
    private static KeyMapping nextMusic;
    private static KeyMapping pauseResume;
    private static KeyMapping loopMusic;
    private static KeyMapping previousCategory;
    private static KeyMapping nextCategory;
    private static KeyMapping printMusic;
    private static KeyMapping volumeUp;
    private static KeyMapping volumeDown;
    private static KeyMapping openMenu;

    public static void register() {
        registerKeys();
        registerEvents();
    }

    private static void registerKeys() {
        previousMusic = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.previousMusic",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT,
                mainCategory
        ));

        nextMusic = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.nextMusic",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT,
                mainCategory
        ));

        pauseResume = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.pause",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                mainCategory
        ));

        loopMusic = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.loop",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                mainCategory
        ));

        previousCategory = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.previousCategory",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_UP,
                mainCategory
        ));

        nextCategory = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.nextCategory",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_DOWN,
                mainCategory
        ));

        printMusic = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.print",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_CONTROL,
                mainCategory
        ));

        volumeUp = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.volumeUp",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UP,
                mainCategory
        ));

        volumeDown = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.volumeDown",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_DOWN,
                mainCategory
        ));

        openMenu = KeyMappingCompat.register(new KeyMapping(
                "key.music_control.openMenu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                mainCategory
        ));
    }

    private static void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (previousMusic.consumeClick()) {
                MusicControlClient.previousMusic = true;
            }

            while (nextMusic.consumeClick()) {
                MusicControlClient.nextMusic = true;
            }

            while (pauseResume.consumeClick()) {
                MusicControlClient.pauseResume = true;
            }

            while (loopMusic.consumeClick()) {
                MusicControlClient.loopMusic = !MusicControlClient.loopMusic;
                if (MusicControlClient.loopMusic) {
                    Utils.print(client, Component.translatable("music.loop.on"));
                } else {
                    Utils.print(client, Component.translatable("music.loop.off"));
                }
            }

            while (previousCategory.consumeClick()) {
                MusicControlClient.previousCategory = true;
            }

            while (nextCategory.consumeClick()) {
                MusicControlClient.nextCategory = true;
            }

            while (printMusic.consumeClick()) {
                MusicControlClient.printMusic = true;
            }

            while (volumeUp.consumeClick()) {
                adjustVolume(client, ModConfig.get().general.misc.volumeIncrement);
            }

            while (volumeDown.consumeClick()) {
                adjustVolume(client, -ModConfig.get().general.misc.volumeIncrement);
            }

            while (openMenu.consumeClick()) {
                ClientCompat.setScreen(client, new MusicControlScreen(new MusicControlGUI(client)));
            }

            ((MusicTrackerAccess) client.getMusicManager()).music_control$handlePendingKeyInputs();
        });
    }

    private static void adjustVolume(Minecraft client, int incrementPercent) {
        double volume = client.options.getSoundSourceVolume(SoundSource.MUSIC);
        volume = MusicVolumeAdjustment.adjust(volume, incrementPercent);
        ((GameOptionsAccess) client.options).music_control$setSoundCategoryVolume(SoundSource.MUSIC, volume);
        client.options.save();
        Utils.print(client, Component.translatable("music.volume", Math.round(volume * 100.0)));
    }
}
