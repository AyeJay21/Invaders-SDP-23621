package engine.soundEngine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression checks for slow audio devices; no speakers or GUI required. */
public final class SoundManagerTest {
    public static void main(final String[] args) throws Exception {
        blockedDeviceDoesNotBlockCaller();
        blockedPlaybackDoesNotBlockNextShot();
        queuedEffectsRespectMute();
        saturatedQueueStaysBounded();
        disposalDropsPendingEffects();
        playbackFailureDoesNotStopWorker();
        System.out.println("PASS: 6 SFX concurrency regression checks");
    }

    private static void blockedDeviceDoesNotBlockCaller() throws Exception {
        final FakeBackend backend = new FakeBackend(1);
        final SoundManager manager = new SoundManager(backend);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            manager.preloadSfx(Sound.SHOOT);
            await(backend.loading, "preload started");
            Future<?> request = caller.submit(new Runnable() {
                public void run() {
                    manager.playSfx(Sound.SHOOT);
                    manager.setVolume(Sound.Category.SFX, 0.25f);
                }
            });
            request.get(1, TimeUnit.SECONDS);
            check(backend.plays.get() == 0, "playback waits for preloading");
            backend.release.countDown();
            await(backend.played, "queued shot played");
            check(backend.volume == 0.25f, "queued shot uses current volume");
            check(backend.workerThread == backend.playThread,
                    "loading and playback share the audio worker");
        } finally {
            backend.release.countDown();
            manager.dispose();
            caller.shutdownNow();
            await(backend.closed, "backend disposed");
        }
    }

    private static void blockedPlaybackDoesNotBlockNextShot() throws Exception {
        final FakeBackend backend = new FakeBackend(2);
        backend.blockFirstPlayback = true;
        final SoundManager manager = new SoundManager(backend);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            manager.playSfx(Sound.SHOOT);
            await(backend.playing, "playback started");
            Future<?> request = caller.submit(new Runnable() {
                public void run() {
                    manager.playSfx(Sound.SHOOT);
                    manager.setVolume(Sound.Category.SFX, 0.25f);
                }
            });
            request.get(1, TimeUnit.SECONDS);
            check(backend.plays.get() == 1, "second shot waits on audio worker");
            backend.release.countDown();
            await(backend.played, "both shots played");
        } finally {
            backend.release.countDown();
            manager.dispose();
            caller.shutdownNow();
            await(backend.closed, "backend disposed");
        }
    }

    private static void queuedEffectsRespectMute() throws Exception {
        FakeBackend backend = new FakeBackend(0);
        SoundManager manager = new SoundManager(backend);
        try {
            manager.preloadSfx(Sound.SHOOT);
            await(backend.loading, "preload started");
            manager.playSfx(Sound.SHOOT);
            manager.setMuted(true);
            manager.playSfx(Sound.SHOOT);
            backend.release.countDown();
            manager.preloadSfx(Sound.SHOOT);
            await(backend.secondLoad, "queued work drained");
            check(backend.plays.get() == 0, "muting skips pending effects");
        } finally {
            backend.release.countDown();
            manager.dispose();
            await(backend.closed, "backend disposed");
        }
    }

    private static void saturatedQueueStaysBounded() throws Exception {
        FakeBackend backend = new FakeBackend(8);
        SoundManager manager = new SoundManager(backend);
        try {
            manager.preloadSfx(Sound.SHOOT);
            await(backend.loading, "preload started");
            for (int i = 0; i < 30; i++)
                manager.playSfx(Sound.SHOOT);
            backend.release.countDown();
            await(backend.played, "bounded backlog played");
            manager.preloadSfx(Sound.SHOOT);
            await(backend.secondLoad, "queued work drained");
            check(backend.plays.get() == 8, "at most eight shots remain queued");
        } finally {
            backend.release.countDown();
            manager.dispose();
            await(backend.closed, "backend disposed");
        }
    }

    private static void disposalDropsPendingEffects() throws Exception {
        FakeBackend backend = new FakeBackend(0);
        SoundManager manager = new SoundManager(backend);
        try {
            manager.preloadSfx(Sound.SHOOT);
            await(backend.loading, "preload started");
            manager.playSfx(Sound.SHOOT);
            manager.dispose();
            manager.dispose();
            manager.playSfx(Sound.SHOOT);
            manager.preloadSfx();
        } finally {
            backend.release.countDown();
            manager.dispose();
            await(backend.closed, "backend disposed");
        }
        check(backend.plays.get() == 0, "shutdown discards pending effects");
        check(backend.disposals.get() == 1, "backend closes once");
    }

    private static void playbackFailureDoesNotStopWorker() throws Exception {
        FakeBackend backend = new FakeBackend(2);
        backend.failFirstPlayback = true;
        SoundManager manager = new SoundManager(backend);
        try {
            manager.playSfx(Sound.SHOOT);
            manager.playSfx(Sound.SHOOT);
            await(backend.played, "worker continued after failure");
            check(backend.plays.get() == 2, "both requests reached backend");
        } finally {
            manager.dispose();
            await(backend.closed, "backend disposed");
        }
    }

    private static void await(final CountDownLatch latch, final String label)
            throws InterruptedException {
        check(latch.await(2, TimeUnit.SECONDS), label);
    }

    private static void check(final boolean condition, final String label) {
        if (!condition)
            throw new AssertionError(label);
    }

    private static final class FakeBackend implements AudioBackend {
        final CountDownLatch loading = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch secondLoad = new CountDownLatch(1);
        final CountDownLatch playing = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final CountDownLatch played;
        final AtomicInteger loads = new AtomicInteger();
        final AtomicInteger plays = new AtomicInteger();
        final AtomicInteger disposals = new AtomicInteger();
        volatile float volume;
        volatile Thread workerThread;
        volatile Thread playThread;
        boolean failFirstPlayback;
        boolean blockFirstPlayback;

        FakeBackend(final int expectedPlays) {
            played = new CountDownLatch(expectedPlays);
        }

        public void preload(final Sound sound) {
            workerThread = Thread.currentThread();
            if (loads.incrementAndGet() == 1) {
                loading.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                secondLoad.countDown();
            }
        }

        public void playSfx(final Sound sound, final float level) {
            volume = level;
            playThread = Thread.currentThread();
            int count = plays.incrementAndGet();
            playing.countDown();
            if (blockFirstPlayback && count == 1) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            played.countDown();
            if (failFirstPlayback && count == 1)
                throw new IllegalStateException("simulated device failure");
        }

        public void playBgm(final Sound sound, final float level) { }
        public void stopBgm() { }
        public boolean isBgmPlaying() { return false; }
        public void setBgmVolume(final float level) { }
        public void dispose() {
            disposals.incrementAndGet();
            closed.countDown();
        }
    }
}
