package engine;

import engine.soundEngine.Sound;
import engine.soundEngine.SoundManager;

public final class PlayerSounds {

    private PlayerSounds() {
    }

    public static void playDamage() {
        SoundManager.getInstance().playSfx(Sound.PLAYER_EXPLOSION);
    }

    public static void playDeath() {
        SoundManager.getInstance().playSfx(Sound.PLAYER_EXPLOSION);
    }

    public static void playHit(final int livesRemaining) {
        if (livesRemaining <= 0) {
            playDeath();
        } else {
            playDamage();
        }
    }
}