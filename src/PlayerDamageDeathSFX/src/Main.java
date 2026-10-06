/**
 * Entry point used to test the player damage/death sound effects.
 * Run this to verify your .wav files play correctly.
 *
 * Branch: sfx/player-damage-death-sound
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        AudioManager audioManager = new AudioManager();
        PlayerDamageDeathSFX playerSFX = new PlayerDamageDeathSFX(audioManager);

        playerSFX.loadSounds();

        System.out.println("Playing damage sound...");
        playerSFX.playDamage();
        Thread.sleep(1500);

        System.out.println("Playing death sound...");
        playerSFX.playDeath();
        Thread.sleep(2500);

        playerSFX.unload();
        audioManager.unloadAll();
    }
}