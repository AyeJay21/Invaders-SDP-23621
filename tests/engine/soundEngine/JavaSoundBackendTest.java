package engine.soundEngine;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/** Checks real voice selection using simulated clips and the actual WAV. */
public final class JavaSoundBackendTest {
    public static void main(final String[] args) {
        repeatedShotsOverlapAndReuseFinishedVoice();
        busyPoolDoesNotInterruptPlayback();
        partialPoolRemainsUsable();
        failedOpenClosesClipAndIsNotRetried();
        disposalClosesEveryVoice();
        invalidRequestsDoNotOpenClips();
        System.out.println("PASS: 6 overlapping SFX regression checks");
    }

    private static void repeatedShotsOverlapAndReuseFinishedVoice() {
        FakeFactory factory = new FakeFactory();
        JavaSoundBackend backend = new JavaSoundBackend(factory);
        try {
            backend.preload(Sound.SHOOT);
            check(factory.voices.size() == 8, "voices are preopened");
            FakeClip first = factory.voices.get(0);
            check(first.format.getSampleRate() == 44100,
                    "PCM sample rate preserved");
            check(first.bytes.length / first.format.getFrameSize() == 44100,
                    "whole one-second sound loaded");
            for (FakeClip voice : factory.voices)
                check(voice.bytes == first.bytes, "PCM data decoded only once");

            backend.playSfx(Sound.SHOOT, 0.7f);
            backend.playSfx(Sound.SHOOT, 0.7f);
            FakeClip second = factory.voices.get(1);
            check(first.running && second.running, "successive shots overlap");
            check(first.stops == 0 && first.seeks == 1,
                    "second shot does not stop or rewind first");
            first.running = false; // Simulate natural completion.
            backend.playSfx(Sound.SHOOT, 0.7f);
            check(first.starts == 2 && second.starts == 1,
                    "completed voice is reused while other voice continues");
            check(factory.attempts == 8, "firing opens no new device clips");
        } finally {
            backend.dispose();
        }
    }

    private static void busyPoolDoesNotInterruptPlayback() {
        FakeFactory factory = new FakeFactory();
        JavaSoundBackend backend = new JavaSoundBackend(factory);
        try {
            for (int i = 0; i < 12; i++)
                backend.playSfx(Sound.SHOOT, 0.7f);
            check(factory.voices.size() == 8, "voice count is bounded");
            for (FakeClip voice : factory.voices)
                check(voice.running && voice.starts == 1 && voice.stops == 0
                        && voice.seeks == 1, "busy voices finish uninterrupted");
        } finally {
            backend.dispose();
        }
    }

    private static void partialPoolRemainsUsable() {
        FakeFactory factory = new FakeFactory();
        factory.capacity = 2;
        JavaSoundBackend backend = new JavaSoundBackend(factory);
        try {
            backend.preload(Sound.SHOOT);
            backend.playSfx(Sound.SHOOT, 0.7f);
            backend.playSfx(Sound.SHOOT, 0.7f);
            backend.playSfx(Sound.SHOOT, 0.7f);
            check(factory.voices.size() == 2 && factory.attempts == 3,
                    "limited device capacity is not retried on every shot");
            for (FakeClip voice : factory.voices)
                check(voice.starts == 1 && voice.stops == 0,
                        "available voices remain usable");
        } finally {
            backend.dispose();
        }
    }

    private static void failedOpenClosesClipAndIsNotRetried() {
        FakeFactory factory = new FakeFactory();
        factory.failOpen = true;
        JavaSoundBackend backend = new JavaSoundBackend(factory);
        try {
            backend.playSfx(Sound.SHOOT, 0.7f);
            backend.playSfx(Sound.SHOOT, 0.7f);
            check(factory.attempts == 1 && factory.voices.get(0).closes == 1,
                    "failed clip is closed and failed sound is cached");
        } finally {
            backend.dispose();
        }
    }

    private static void disposalClosesEveryVoice() {
        FakeFactory factory = new FakeFactory();
        JavaSoundBackend backend = new JavaSoundBackend(factory);
        backend.preload(Sound.SHOOT);
        backend.playSfx(Sound.SHOOT, 0.7f);
        backend.dispose();
        backend.dispose();
        for (FakeClip voice : factory.voices)
            check(voice.closes == 1 && voice.stops == 1 && !voice.running,
                    "all voices are stopped and closed exactly once");
    }

    private static void invalidRequestsDoNotOpenClips() {
        FakeFactory factory = new FakeFactory();
        JavaSoundBackend backend = new JavaSoundBackend(factory);
        try {
            backend.preload(null);
            backend.preload(Sound.BGM_GAME);
            backend.playSfx(null, 0.7f);
            backend.playSfx(Sound.BGM_GAME, 0.7f);
            backend.playBgm(Sound.SHOOT, 0.7f);
            check(factory.attempts == 0, "invalid requests do no device work");
        } finally {
            backend.dispose();
        }
    }

    private static void check(final boolean condition, final String label) {
        if (!condition)
            throw new AssertionError(label);
    }

    private static final class FakeFactory implements JavaSoundBackend.ClipFactory {
        final List<FakeClip> voices = new ArrayList<FakeClip>();
        int capacity = 8;
        int attempts;
        boolean failOpen;

        public Clip create() throws LineUnavailableException {
            attempts++;
            if (voices.size() == capacity)
                throw new LineUnavailableException("simulated capacity limit");
            FakeClip voice = new FakeClip(failOpen);
            voices.add(voice);
            return (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(),
                    new Class<?>[] { Clip.class }, voice);
        }
    }

    private static final class FakeClip implements InvocationHandler {
        final boolean failOpen;
        boolean running;
        int starts, stops, seeks, closes;
        AudioFormat format;
        byte[] bytes;

        FakeClip(final boolean failure) {
            failOpen = failure;
        }

        public Object invoke(final Object proxy, final Method method,
                final Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("open")) {
                if (failOpen)
                    throw new LineUnavailableException("simulated open failure");
                format = (AudioFormat) args[0];
                bytes = (byte[]) args[1];
            } else if (name.equals("isRunning")) {
                return running;
            } else if (name.equals("isControlSupported")) {
                return false;
            } else if (name.equals("setFramePosition")) {
                seeks++;
            } else if (name.equals("start")) {
                starts++;
                running = true;
            } else if (name.equals("stop")) {
                stops++;
                running = false;
            } else if (name.equals("close")) {
                closes++;
            } else if (!name.equals("flush")) {
                throw new AssertionError("Unexpected clip operation: " + name);
            }
            return null;
        }
    }
}
