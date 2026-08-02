package com.github.charlyb01.music_control.platform;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class KeyMappingCompat {
    private KeyMappingCompat() {}

    public static KeyMapping register(KeyMapping mapping) {
        return KeyMappingHelper.registerKeyMapping(mapping);
    }
}
