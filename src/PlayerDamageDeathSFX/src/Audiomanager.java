import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Core audio playback engine.
 * Handles loading, caching, and playing sound clips, with basic
 * volume control per clip.
 *
 * Branch: engine/audio-manager
 */
public class AudioManager {

    // Cache loaded clips so the same sound file isn't reloaded from disk every time it plays
    private final Map<String, Clip> clipCache = new HashMap<>();

    /**
     * Loads a WAV file into memory and caches it under the given key.
     * Call this once during asset loading, not every time the sound plays.
     *
     * @param key      identifier used to reference this sound later (e.g. "player_damage")
     * @param filePath path to the .wav file (e.g. "assets/audio/sfx/player_damage.wav")
     */
    public void loadSound(String key, String filePath) {
        try {
            File audioFile = new File(filePath);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clipCache.put(key, clip);
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("[AudioManager] Failed to load sound '" + key + "' from " + filePath);
            e.printStackTrace();
        }
    }

    /**
     * Plays the sound associated with the given key from the beginning.
     * Safe to call repeatedly (e.g. multiple hits in a row).
     *
     * @param key    identifier of a previously loaded sound
     * @param volume value between 0.0 (silent) and 1.0 (full volume)
     */
    public void play(String key, float volume) {
        Clip clip = clipCache.get(key);
        if (clip == null) {
            System.err.println("[AudioManager] No sound loaded for key: " + key);
            return;
        }

        // Rewind so the sound can be replayed from the start even if it was just played
        if (clip.isRunning()) {
            clip.stop();
        }
        clip.setFramePosition(0);

        setVolume(clip, volume);
        clip.start();
    }

    /**
     * Stops a currently playing sound, if any.
     */
    public void stop(String key) {
        Clip clip = clipCache.get(key);
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }

    /**
     * Releases all loaded clips. Call when shutting down or changing scenes
     * to free memory and avoid audio leaks.
     */
    public void unloadAll() {
        for (Clip clip : clipCache.values()) {
            clip.close();
        }
        clipCache.clear();
    }

    private void setVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return; // some systems/lines don't support gain control, fail silently
        }
        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

        // Convert a 0.0–1.0 linear volume into decibels, which is what MASTER_GAIN expects
        float clampedVolume = Math.max(0.0001f, Math.min(1f, volume));
        float dB = (float) (Math.log10(clampedVolume) * 20);
        dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
        gainControl.setValue(dB);
    }
}