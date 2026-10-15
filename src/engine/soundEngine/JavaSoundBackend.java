package engine.soundEngine;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;

import engine.Core;
import engine.soundEngine.Sound.Category;

/**
 * Audio backend based on the Java Sound API.
 *
 * <p>Sound data is loaded once and effects use a pool of preopened clips.
 * Missing or broken files are logged and
 * then ignored, never thrown, because a game without a single sound effect
 * must still be playable.</p>
 *
 * <p>Repeated effects overlap without interrupting active playback. When
 * all voices are busy, the new request is skipped. Background music still
 * uses a single clip per track.</p>
 *
 * @author Invaders Sound Team
 */
public final class JavaSoundBackend implements AudioBackend {

	/** Application logger. */
	private static Logger logger;
	/** Lowest gain the mixer accepts, used for silence. */
	private static final float MIN_GAIN_DB = -80.0f;
	/** Maximum simultaneous voices for each sound effect. */
	private static final int SFX_VOICES = 8;
	/** Loaded background music clips by sound. */
	private final Map<Sound, Clip> clips;
	/** Preopened effect voices; loading never occurs on a subsequent shot. */
	private final Map<Sound, List<Clip>> sfxClips;
	/** Supplies real device clips, or test clips without an audio device. */
	private final ClipFactory clipFactory;
	/** Sounds that failed to load, so they are not retried on every play. */
	private final Set<Sound> failed;
	/** Clip that currently carries the background music. */
	private Clip bgmClip;

	/**
	 * Constructor.
	 */
	public JavaSoundBackend() {
		this(new ClipFactory() {
			@Override
			public Clip create() throws LineUnavailableException {
				return AudioSystem.getClip();
			}
		});
	}

	JavaSoundBackend(final ClipFactory factory) {
		logger = Core.getLogger();
		clips = new EnumMap<Sound, Clip>(Sound.class);
		sfxClips = new EnumMap<Sound, List<Clip>>(Sound.class);
		clipFactory = factory;
		failed = EnumSet.noneOf(Sound.class);
	}

	/** Test seam for observing overlapping voices without device access. */
	interface ClipFactory {
		Clip create() throws LineUnavailableException;
	}

	/**
	 * Tells whether the machine offers a usable audio device.
	 *
	 * @return True when at least one mixer is present.
	 */
	public static boolean isAvailable() {
		try {
			return AudioSystem.getMixerInfo().length > 0;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public synchronized void preload(final Sound sound) {
		if (sound != null && sound.getCategory() == Sound.Category.SFX)
			loadSfx(sound);
	}

	@Override
	public synchronized void playSfx(final Sound sound, final float volume) {
		if (sound == null || sound.getCategory() != Sound.Category.SFX)
			return;

		List<Clip> voices = loadSfx(sound);
		if (voices == null)
			return;

		for (Clip clip : voices) {
			if (!clip.isRunning()) {
				clip.setFramePosition(0);
				applyVolume(clip, volume);
				clip.start();
				return;
			}
		}
	}

	@Override
	public synchronized void playBgm(final Sound sound, final float volume) {
		if (sound == null || sound.getCategory() != Sound.Category.BGM)
			return;

		Clip clip = load(sound);
		if (clip == null)
			return;

		if (bgmClip != null && bgmClip != clip)
			bgmClip.stop();

		bgmClip = clip;
		clip.stop();
		clip.setFramePosition(0);
		applyVolume(clip, volume);
		clip.loop(Clip.LOOP_CONTINUOUSLY);
	}

	@Override
	public synchronized void stopBgm() {
		if (bgmClip != null) {
			bgmClip.stop();
			bgmClip.setFramePosition(0);
		}
	}

	@Override
	public synchronized boolean isBgmPlaying() {
		return bgmClip != null && bgmClip.isRunning();
	}

	@Override
	public synchronized void setBgmVolume(final float volume) {
		if (bgmClip != null)
			applyVolume(bgmClip, volume);
	}

	@Override
	public synchronized void dispose() {
		for (Clip clip : clips.values()) {
			clip.stop();
			clip.flush();
			clip.close();
		}
		clips.clear();
		for (List<Clip> voices : sfxClips.values()) {
			for (Clip clip : voices) {
				clip.stop();
				clip.flush();
				clip.close();
			}
		}
		sfxClips.clear();
		failed.clear();
		bgmClip = null;
	}

	/**
	 * Loads a sound, or returns the already loaded clip.
	 *
	 * @param sound
	 *            Sound to load.
	 * @return The clip, or null if the file is missing or unreadable.
	 */
	private Clip load(final Sound sound) {
		Clip cached = clips.get(sound);
		if (cached != null)
			return cached;
		if (failed.contains(sound))
			return null;

		try {
			Clip clip = openClip(readSound(sound));
			clips.put(sound, clip);
			return clip;
		} catch (Exception e) {
			logger.warning("Could not load sound " + sound.getPath() + ": "
					+ e.getMessage());
			failed.add(sound);
			return null;
		}
	}

	/** Decodes once and opens a bounded set of independent playback voices. */
	private List<Clip> loadSfx(final Sound sound) {
		List<Clip> cached = sfxClips.get(sound);
		if (cached != null)
			return cached;
		if (failed.contains(sound))
			return null;

		List<Clip> voices = new ArrayList<Clip>();
		try {
			AudioData data = readSound(sound);
			for (int i = 0; i < SFX_VOICES; i++)
				voices.add(openClip(data));
		} catch (Exception e) {
			logger.warning("Could not prepare all voices for " + sound.getPath()
					+ " (opened " + voices.size() + "): " + e.getMessage());
			if (voices.isEmpty()) {
				failed.add(sound);
				return null;
			}
		}
		sfxClips.put(sound, voices);
		return voices;
	}

	/** Reads decoded PCM data so all voices share the same source bytes. */
	private AudioData readSound(final Sound sound) throws Exception {
		try (InputStream stream = JavaSoundBackend.class.getClassLoader()
				.getResourceAsStream(sound.getPath())) {
			if (stream == null)
				throw new IOException("Sound file not found: " + sound.getPath());
			try (AudioInputStream audio = AudioSystem.getAudioInputStream(
					new BufferedInputStream(stream))) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				byte[] buffer = new byte[8192];
				int count;
				while ((count = audio.read(buffer)) != -1)
					bytes.write(buffer, 0, count);
				return new AudioData(audio.getFormat(), bytes.toByteArray());
			}
		}
	}

	private Clip openClip(final AudioData data) throws LineUnavailableException {
		Clip clip = clipFactory.create();
		boolean opened = false;
		try {
			clip.open(data.format, data.bytes, 0, data.bytes.length);
			opened = true;
			return clip;
		} finally {
			if (!opened)
				clip.close();
		}
	}

	private static final class AudioData {
		private final AudioFormat format;
		private final byte[] bytes;

		AudioData(final AudioFormat audioFormat, final byte[] audioBytes) {
			format = audioFormat;
			bytes = audioBytes;
		}
	}

	/**
	 * Applies a linear volume to a clip through its gain control.
	 *
	 * @param clip
	 *            Clip to adjust.
	 * @param volume
	 *            Volume between 0.0 and 1.0.
	 */
	private static void applyVolume(final Clip clip, final float volume) {
		if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
			return;

		FloatControl gain = (FloatControl) clip
				.getControl(FloatControl.Type.MASTER_GAIN);
		float clamped = Math.max(0.0f, Math.min(1.0f, volume));
		float decibel = clamped <= 0.0f
				? MIN_GAIN_DB
				: (float) (Math.log10(clamped) * 20.0);
		decibel = Math.max(gain.getMinimum(),
				Math.min(gain.getMaximum(), decibel));
		gain.setValue(decibel);
	}

}
