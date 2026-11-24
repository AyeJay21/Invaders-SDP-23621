package engine.soundEngine;

public final class PlayerSounds {

    private PlayerSounds() {
    }

    public static void playDamage() {
        SoundManager.getInstance().playSfx(Sound.PLAYER_DAMAGE); // Fichier player_damage.wav
    }

    public static void playDeath() {
        SoundManager.getInstance().playSfx(Sound.PLAYER_EXPLOSION);   // Fichier player_death.wav
    }

    public static void playHit(final int livesRemaining) {
        if (livesRemaining <= 0) {
            playDeath();
        } else {
            playDamage();
        }
    }
}