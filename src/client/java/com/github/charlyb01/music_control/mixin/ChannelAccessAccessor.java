package com.github.charlyb01.music_control.mixin;

import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.concurrent.Executor;

@Mixin(ChannelAccess.class)
public interface ChannelAccessAccessor {
    @Accessor("channels")
    Set<ChannelAccess.ChannelHandle> music_control$getChannels();

    @Accessor("executor")
    Executor music_control$getExecutor();
}
