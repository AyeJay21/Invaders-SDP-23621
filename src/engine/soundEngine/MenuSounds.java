package engine.soundEngine;

/**
 * Sounds played by the menus. Screens call this instead of talking to
 * the SoundManager, so the sound can be changed in one place.
 */
public final class MenuSounds {

    /** Minimum time between two move sounds, in milliseconds. */
    private static final long MIN_INTERVAL_MS = 60;
    /** When the move sound last played. */
    private static long lastPlayed;

    private MenuSounds() {
    }

    /** Plays the short sound heard when the menu cursor moves. */
    public static void playMove() {
        final long now = System.currentTimeMillis();
        if (now - lastPlayed < MIN_INTERVAL_MS)
            return;
        lastPlayed = now;
        SoundManager.getInstance().playSfx(Sound.MENU_MOVE);
    }

    /**
     * Plays the sound heard when a menu entry is confirmed. Every way of
     * confirming - a key or a mouse click - goes through the same call, so it
     * plays once per confirmation. No throttle is needed here, because the
     * screens only confirm on a fresh key press or click, never while a key
     * is held.
     */
    public static void playSelect() {
        SoundManager.getInstance().playSfx(Sound.MENU_SELECT);
    }
}
