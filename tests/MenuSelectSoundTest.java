import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import engine.soundEngine.AudioBackend;
import engine.soundEngine.MenuSounds;
import engine.soundEngine.Sound;
import engine.soundEngine.SoundManager;

/** Standalone checks for the click heard when a menu entry is confirmed. */
public final class MenuSelectSoundTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /** Backend that only writes down what it was asked to play. */
    private static final class Recorder implements AudioBackend {
        final List<Sound> played = new ArrayList<Sound>();
        final List<Float> volumes = new ArrayList<Float>();

        @Override
        public void preload(Sound sound) {
        }

        @Override
        public void playSfx(Sound sound, float volume) {
            played.add(sound);
            volumes.add(Float.valueOf(volume));
        }

        @Override
        public void playBgm(Sound sound, float volume) {
        }

        @Override
        public void stopBgm() {
        }

        @Override
        public boolean isBgmPlaying() {
            return false;
        }

        @Override
        public void setBgmVolume(float volume) {
        }

        @Override
        public void dispose() {
        }
    }

    /** Points the manager at the recorder, whatever backend it picked. */
    private static Recorder record(SoundManager manager) throws Exception {
        Recorder recorder = new Recorder();
        Field backend = SoundManager.class.getDeclaredField("backend");
        backend.setAccessible(true);
        backend.set(manager, recorder);
        return recorder;
    }

    public static void main(String[] args) throws Exception {
        // The sound is useless without its file: the enum only stores a path.
        String path = "res/" + Sound.MENU_SELECT.getPath();
        check(new File(path).isFile(), "missing audio file: " + path);
        check(Sound.MENU_SELECT.getCategory() == Sound.Category.SFX,
                "the click has to be an effect, not music");

        SoundManager manager = SoundManager.getInstance();
        boolean muted = manager.isMuted();
        manager.setMuted(false);

        Recorder recorder = record(manager);

        MenuSounds.playSelect();
        check(recorder.played.size() == 1 && recorder.played.get(0) == Sound.MENU_SELECT,
                "confirming a menu entry must play the select sound once");
        check(recorder.volumes.get(0).floatValue() == manager
                .getVolume(Sound.Category.SFX),
                "the click must use the effect volume");

        // Every entry sounds the same, however fast the player clicks.
        MenuSounds.playSelect();
        check(recorder.played.size() == 2,
                "two clicks must play two sounds, unlike the cursor move");

        manager.setMuted(true);
        MenuSounds.playSelect();
        check(recorder.played.size() == 2, "a muted game must stay silent");
        manager.setMuted(muted);

        System.out.println("MenuSelectSoundTest: " + checks + " checks passed");
    }
}
