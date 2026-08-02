package com.github.charlyb01.music_control;

import com.github.charlyb01.music_control.config.ModConfig;
import com.github.charlyb01.music_control.platform.ClientCompat;
import com.github.charlyb01.music_control.platform.WorldCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class Utils {
    private Utils() {}

    public static void print(final Minecraft client, final Component text) {
        switch (ModConfig.get().cosmetics.display.type) {
            case JUKEBOX -> ClientCompat.setOverlayMessage(client, text, true);
            case ACTION_BAR -> ClientCompat.setOverlayMessage(client, text, false);
            case CHAT -> ClientCompat.sendSystemMessage(client, text);
        }
    }

    public static boolean isNight(final Level world) {
        long time = WorldCompat.getDayTime(world) % 24000L;
        return time > 13000L && time < 23000L;
    }
}
