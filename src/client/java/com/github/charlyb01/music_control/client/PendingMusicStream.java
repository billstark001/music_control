package com.github.charlyb01.music_control.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.util.Util;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

/** Owns a loaded stream until the audio thread attaches it or the channel is released. */
public final class PendingMusicStream {
    private static final Logger LOGGER = LogUtils.getLogger();

    private boolean released;
    private AudioStream stream;

    public boolean offer(AudioStream stream) {
        synchronized (this) {
            if (!released) {
                this.stream = stream;
                return true;
            }
        }
        closeAsync(stream);
        return false;
    }

    public synchronized AudioStream take() {
        AudioStream pending = stream;
        stream = null;
        return pending;
    }

    public void release() {
        AudioStream pending;
        synchronized (this) {
            released = true;
            pending = take();
        }
        if (pending != null) closeAsync(pending);
    }

    public synchronized boolean isReleased() {
        return released;
    }

    public static void closeAsync(AudioStream stream) {
        // A decoder may block in close(). Free the native channel independently.
        Runnable close = () -> {
            try {
                stream.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close a music stream", e);
            }
        };
        try {
            Util.nonCriticalIoPool().execute(close);
        } catch (RejectedExecutionException e) {
            close.run();
        }
    }
}
