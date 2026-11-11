package com.github.charlyb01.music_control.client;

import net.minecraft.client.sounds.AudioStream;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingMusicStreamTest {
    @Test
    void streamFinishingAfterStopIsClosedWithoutStarting() throws InterruptedException {
        var pending = new PendingMusicStream();
        var stream = new TestStream();

        pending.release();

        assertFalse(pending.offer(stream));
        assertNull(pending.take());
        stream.awaitClose();
    }

    @Test
    void stoppingWhileStartIsQueuedClosesStreamExactlyOnce() throws InterruptedException {
        var pending = new PendingMusicStream();
        var stream = new TestStream();
        assertTrue(pending.offer(stream));

        pending.release();
        pending.release();

        assertNull(pending.take());
        stream.awaitClose();
    }

    @Test
    void attachedStreamIsOwnedByTheChannel() {
        var pending = new PendingMusicStream();
        var stream = new TestStream();
        assertTrue(pending.offer(stream));
        assertSame(stream, pending.take());

        pending.release();

        assertNull(pending.take());
        assertEquals(0, stream.closes.get());
    }

    @Test
    void releasingDoesNotWaitForTheDecoderToClose() throws Exception {
        var pending = new PendingMusicStream();
        var allowClose = new CountDownLatch(1);
        var stream = new TestStream() {
            @Override
            public void close() {
                try {
                    allowClose.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.close();
            }
        };
        assertTrue(pending.offer(stream));

        try {
            CompletableFuture.runAsync(pending::release).get(5, TimeUnit.SECONDS);
            assertNull(pending.take());
        } finally {
            allowClose.countDown();
        }
        stream.awaitClose();
    }

    private static class TestStream implements AudioStream {
        private final AtomicInteger closes = new AtomicInteger();
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public AudioFormat getFormat() {
            throw new AssertionError("Pending streams must not be read");
        }

        @Override
        public ByteBuffer read(int size) {
            throw new AssertionError("Pending streams must not be read");
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            closed.countDown();
        }

        void awaitClose() throws InterruptedException {
            assertTrue(closed.await(5, TimeUnit.SECONDS));
            assertEquals(1, closes.get());
        }
    }
}
