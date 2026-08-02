package com.github.charlyb01.music_control.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClientCompat {
    private ClientCompat() {}

    public static Screen getScreen(Minecraft client) {
        return client.gui.screen();
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }

    public static void setOverlayMessage(Minecraft client, Component message, boolean animateColor) {
        client.gui.hud.setOverlayMessage(message, animateColor);
    }

    public static void sendSystemMessage(Minecraft client, Component message) {
        if (client.player != null) client.player.sendSystemMessage(message);
    }

    public static void showNowPlayingToast(Minecraft client) {
        client.gui.toastManager().showNowPlayingToast();
    }

    public static void hideNowPlayingToast(Minecraft client) {
        client.gui.toastManager().hideNowPlayingToast();
    }
}
