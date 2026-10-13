package engine;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

public class SoundManager {

    /** Reusable pool of thread to avoid creating new Thread() every sounds */
    private static final ExecutorService soundPool = Executors.newCachedThreadPool();

    /**
     * Play a .waw sound file from the resources folder. The sound is played in a separate thread to avoid blocking the main game loop.
     */
    public static void playSound(String fileName) {
        soundPool.execute(() -> {
            try {
                InputStream audioSrc = SoundManager.class.getClassLoader().getResourceAsStream(fileName);
                if (audioSrc == null) {
                    audioSrc = new java.io.FileInputStream("res/" + fileName);
                }
                InputStream bufferedInput = new BufferedInputStream(audioSrc);

                try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedInput)) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioStream);

                    // Free ressrources when the sound is finished playing
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) {
                            clip.close();
                        }
                    });

                    clip.start();
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de la lecture du son : " + fileName);
                e.printStackTrace();
            }
        });
    }
}