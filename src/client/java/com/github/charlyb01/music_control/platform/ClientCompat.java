package com.github.charlyb01.music_control.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClientCompat {
    private ClientCompat() {}

    public static Screen getScreen(Minecraft client) {
#if MC_VERSION < 260200
        return client.screen;
#else
        return client.gui.screen();
#endif
    }

    public static void setScreen(Minecraft client, Screen screen) {
#if MC_VERSION < 260200
        client.setScreen(screen);
#else
        client.gui.setScreen(screen);
#endif
    }

    public static void setOverlayMessage(Minecraft client, Component message, boolean animateColor) {
#if MC_VERSION < 260200
        client.gui.setOverlayMessage(message, animateColor);
#else
        client.gui.hud.setOverlayMessage(message, animateColor);
#endif
    }

    public static void sendSystemMessage(Minecraft client, Component message) {
#if MC_VERSION < 260102
        if (client.player != null) client.player.displayClientMessage(message, false);
#else
        if (client.player != null) client.player.sendSystemMessage(message);
#endif
    }

    public static void showNowPlayingToast(Minecraft client) {
#if MC_VERSION < 260200
        client.getToastManager().showNowPlayingToast();
#else
        client.gui.toastManager().showNowPlayingToast();
#endif
    }

    public static void hideNowPlayingToast(Minecraft client) {
#if MC_VERSION < 260200
        client.getToastManager().hideNowPlayingToast();
#else
        client.gui.toastManager().hideNowPlayingToast();
#endif
    }
}
