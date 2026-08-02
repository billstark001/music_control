package com.github.charlyb01.music_control.platform;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public final class KeyMappingCompat {
    private KeyMappingCompat() {}

    public static KeyMapping register(KeyMapping mapping) {
        return KeyBindingHelper.registerKeyBinding(mapping);
    }
}
