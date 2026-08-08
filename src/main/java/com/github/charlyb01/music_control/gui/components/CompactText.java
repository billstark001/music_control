package com.github.charlyb01.music_control.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Pixel-width text fitting shared by compact rows and selects. */
final class CompactText {
    private static final String ELLIPSIS = "…";

    private CompactText() {}

    static Component fit(Component text, int maxWidth) {
        String value = text.getString();
        var font = Minecraft.getInstance().font;
        if (maxWidth <= 0) return Component.empty();
        if (font.width(value) <= maxWidth) return Component.literal(value);

        int ellipsisWidth = font.width(ELLIPSIS);
        if (ellipsisWidth > maxWidth) return Component.empty();
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end);
            if (font.width(candidate) + ellipsisWidth <= maxWidth) {
                return Component.literal(candidate + ELLIPSIS);
            }
        }
        return Component.literal(ELLIPSIS);
    }
}
