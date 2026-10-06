/**
 * Sound effects for player ship damage and destruction.
 *
 * - Damage sound: short, sharp impact to signal a hit without fully
 *   interrupting gameplay flow.
 * - Death sound: longer, more dramatic sound (explosion + descending tone)
 *   to clearly communicate loss of a life or game failure.
 *
 * Branch: sfx/player-damage-death
 */
public class PlayerDamageDeathSFX {

    private static final String DAMAGE_KEY = "player_damage";
    private static final String DEATH_KEY = "player_death";

    private static final String DAMAGE_FILE = "assets/audio/sfx/player_damage.wav";
    private static final String DEATH_FILE = "assets/audio/sfx/player_death.wav";

    private static final float DAMAGE_VOLUME = 0.7f;
    private static final float DEATH_VOLUME = 1.0f; // death should be clearly audible above other sounds

    private final AudioManager audioManager;

    public PlayerDamageDeathSFX(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    /**
     * Loads both sounds into memory. Call this once during level/asset
     * initialization, not on every hit or death.
     */
    public void loadSounds() {
        audioManager.loadSound(DAMAGE_KEY, DAMAGE_FILE);
        audioManager.loadSound(DEATH_KEY, DEATH_FILE);
    }

    /**
     * Call this whenever the player's ship takes damage but survives.
     */
    public void playDamage() {
        audioManager.play(DAMAGE_KEY, DAMAGE_VOLUME);
    }

    /**
     * Call this whenever the player's ship is destroyed (loses a life
     * or triggers game over). Should be clearly distinguishable from
     * the regular damage sound.
     */
    public void playDeath() {
        audioManager.play(DEATH_KEY, DEATH_VOLUME);
    }

    /**
     * Releases the sounds from memory. Call when the player object
     * or the level is being unloaded.
     */
    public void unload() {
        audioManager.stop(DAMAGE_KEY);
        audioManager.stop(DEATH_KEY);
    }
}