package engine.soundEngine;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import engine.Core;

/**
 * Central entry point for all audio in the game.
 *
 * <p>This is the only class call sites ever talk to. It owns the audio
 * backend, the volumes and the state of the background music, so playing
 * music and effects stays consistent no matter where it is triggered:</p>
 *
 * <pre>
 * SoundManager.getInstance().playSfx(Sound.SHOOT);
 * SoundManager.getInstance().playBgm(Sound.BGM_GAME);
 * </pre>
 *
 * <p>The manager is a singleton because the underlying audio lines are a
 * process wide resource. Two independent audio managers would compete for
 * the same mixer and produce crackling or dropped sounds.</p>
 *
 * @author Invaders Sound Team
 */
public final class SoundManager {

	/** Singleton instance of the class. */
	private static SoundManager instance;
	/** Application logger. */
	private static Logger logger;
	/** Default volume of sound effects. */
	private static final float DEFAULT_SFX_VOLUME = 0.7f;
	/** Default volume of background music. */
	private static final float DEFAULT_BGM_VOLUME = 0.5f;

	/** Backend that does the actual playback. */
	private final AudioBackend backend;
	/** Serializes slow SFX operations away from the game update thread. */
	private final ThreadPoolExecutor sfxWorker;
	/** Protects enqueue and shutdown without waiting for backend operations. */
	private final Object sfxLifecycleLock = new Object();
	/** Volume of sound effects. */
	private volatile float sfxVolume;
	/** Volume of background music. */
	private float bgmVolume;
	/** Whether all audio is muted. */
	private volatile boolean muted;
	/** Stops accepting requests after disposal. */
	private volatile boolean disposed;
	/** Music that should be playing, even while muted. */
	private Sound currentBgm;

	/**
	 * Private constructor.
	 */
	private SoundManager() {
		this(createBackend());
	}

	/** Creates a manager with an injectable backend for concurrency tests. */
	SoundManager(final AudioBackend audioBackend) {
		logger = Core.getLogger();
		sfxVolume = DEFAULT_SFX_VOLUME;
		bgmVolume = DEFAULT_BGM_VOLUME;
		backend = audioBackend;
		sfxWorker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<Runnable>(8), new ThreadFactory() {
					@Override
					public Thread newThread(final Runnable task) {
						Thread thread = new Thread(task, "invaders-sfx");
						thread.setDaemon(true);
						return thread;
					}
				}, new ThreadPoolExecutor.DiscardOldestPolicy());
	}

	/** Selects the real backend or a silent fallback. */
	private static AudioBackend createBackend() {
		try {
			return JavaSoundBackend.isAvailable()
					? new JavaSoundBackend()
					: new SilentBackend();
		} catch (RuntimeException e) {
			Core.getLogger().warning("Audio initialization failed: "
					+ e.getMessage());
			return new SilentBackend();
		}
	}

	/**
	 * Returns the shared instance of SoundManager.
	 *
	 * @return Shared instance of SoundManager.
	 */
	public static synchronized SoundManager getInstance() {
		if (instance == null)
			instance = new SoundManager();
		return instance;
	}

	/**
	 * Queues loading of all sound effects before subsequent SFX requests.
	 * Loading and playback share one worker so neither stalls game updates.
	 */
	public void preloadSfx() {
		enqueueSfx(new Runnable() {
			@Override
			public void run() {
				for (Sound sound : Sound.values()) {
					if (disposed)
						return;
					backend.preload(sound);
				}
			}
		});
	}

	/**
	 * Queues loading of a single effect, without requiring other SFX assets.
	 *
	 * @param sound Effect to prepare before gameplay starts.
	 */
	public void preloadSfx(final Sound sound) {
		if (sound == null || sound.getCategory() != Sound.Category.SFX)
			return;
		enqueueSfx(new Runnable() {
			@Override
			public void run() {
				backend.preload(sound);
			}
		});
	}

	/**
	 * Queues a short effect without waiting for the audio device. Ignored
	 * while muted or when the sound is not an effect. A bounded queue drops
	 * the oldest pending request if playback cannot keep up.
	 *
	 * @param sound
	 *            Effect to play.
	 */
	public void playSfx(final Sound sound) {
		if (muted || sound == null)
			return;
		if (sound.getCategory() != Sound.Category.SFX)
			return;
		enqueueSfx(new Runnable() {
			@Override
			public void run() {
				if (!muted)
					backend.playSfx(sound, sfxVolume);
			}
		});
	}

	/** Enqueues device work without holding the manager's state lock. */
	private void enqueueSfx(final Runnable task) {
		synchronized (sfxLifecycleLock) {
			if (disposed)
				return;
			sfxWorker.execute(new Runnable() {
				@Override
				public void run() {
					if (disposed)
						return;
					try {
						task.run();
					} catch (RuntimeException e) {
						logger.warning("Sound effect failed: " + e.getMessage());
					}
				}
			});
		}
	}

	/**
	 * Switches the background music. Does nothing if the requested track is
	 * already playing, so screens can call this every frame safely.
	 *
	 * @param sound
	 *            Music to play.
	 */
	public synchronized void playBgm(final Sound sound) {
		if (disposed || sound == null || sound.getCategory() != Sound.Category.BGM)
			return;
		if (sound == currentBgm && backend.isBgmPlaying())
			return;

		currentBgm = sound;
		if (muted)
			return;
		backend.playBgm(sound, bgmVolume);
	}

	/**
	 * Stops the background music and forgets the current track.
	 */
	public synchronized void stopBgm() {
		currentBgm = null;
		backend.stopBgm();
	}

	/**
	 * Returns the track that should currently be playing.
	 *
	 * @return Current background music, or null if none was requested.
	 */
	public synchronized Sound getCurrentBgm() {
		return currentBgm;
	}

	/**
	 * Mutes or unmutes all audio. Music keeps its position, so unmuting
	 * resumes the current track.
	 *
	 * @param mute
	 *            True to silence everything.
	 */
	public synchronized void setMuted(final boolean mute) {
		if (muted == mute)
			return;

		muted = mute;
		if (muted) {
			backend.stopBgm();
		} else if (currentBgm != null) {
			backend.playBgm(currentBgm, bgmVolume);
		}
	}

	/**
	 * Tells whether audio is muted.
	 *
	 * @return True when all audio is silenced.
	 */
	public synchronized boolean isMuted() {
		return muted;
	}

	/**
	 * Sets the volume of one category.
	 *
	 * @param category
	 *            Category to adjust.
	 * @param volume
	 *            Volume between 0.0 and 1.0, clamped.
	 */
	public synchronized void setVolume(final Sound.Category category,
			final float volume) {
		float clamped = Math.max(0.0f, Math.min(1.0f, volume));
		if (category == Sound.Category.BGM) {
			bgmVolume = clamped;
			backend.setBgmVolume(muted ? 0.0f : clamped);
		} else {
			sfxVolume = clamped;
		}
	}

	/**
	 * Returns the volume of one category.
	 *
	 * @param category
	 *            Category to query.
	 * @return Volume between 0.0 and 1.0.
	 */
	public synchronized float getVolume(final Sound.Category category) {
		return category == Sound.Category.BGM ? bgmVolume : sfxVolume;
	}

	/**
	 * Discards pending effects and queues resource cleanup after any active
	 * SFX operation. Called when the game exits; does not wait for the device.
	 */
	public synchronized void dispose() {
		synchronized (sfxLifecycleLock) {
			if (disposed)
				return;
			disposed = true;
			currentBgm = null;
			sfxWorker.getQueue().clear();
			sfxWorker.execute(new Runnable() {
				@Override
				public void run() {
					backend.dispose();
				}
			});
			sfxWorker.shutdown();
		}
	}
}
