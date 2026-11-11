package com.github.charlyb01.music_control.platform;

#if MC_VERSION < 260102
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
#else
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
#endif
import net.minecraft.client.KeyMapping;

public final class KeyMappingCompat {
    private KeyMappingCompat() {}

    public static KeyMapping register(KeyMapping mapping) {
#if MC_VERSION < 260102
        return KeyBindingHelper.registerKeyBinding(mapping);
#else
        return KeyMappingHelper.registerKeyMapping(mapping);
#endif
    }
}
