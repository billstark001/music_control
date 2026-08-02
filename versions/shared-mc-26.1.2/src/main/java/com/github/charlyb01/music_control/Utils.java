package com.github.charlyb01.music_control;

import com.github.charlyb01.music_control.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class Utils {
    private Utils() {}

    public static void print(final Minecraft client, final Component text) {
        switch (ModConfig.get().cosmetics.display.type) {
            case JUKEBOX -> client.gui.setOverlayMessage(text, true);
            case ACTION_BAR -> client.gui.setOverlayMessage(text, false);
            case CHAT -> {
                if (client.player != null) {
                    client.player.sendSystemMessage(text);
                }
            }
        }
    }

    public static boolean isNight(final Level world) {
        long time = world.getDefaultClockTime() % 24000L;
        return time > 13000L && time < 23000L;
    }
}
