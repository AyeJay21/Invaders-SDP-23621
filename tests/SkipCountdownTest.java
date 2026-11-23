import java.lang.reflect.Field;
import java.lang.reflect.Method;
import engine.Cooldown;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import screen.GameScreen;

/** Standalone regression checks for skipping the level start countdown. */
public final class SkipCountdownTest {
    private static int checks;

    private static Object get(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);

        // finish() ends a running cooldown at once.
        Cooldown cooldown = Core.getCooldown(6000);
        cooldown.reset();
        check(!cooldown.checkFinished(), "fresh cooldown must still be running");
        cooldown.finish();
        check(cooldown.checkFinished(), "finished cooldown must be over");

        // Skipping the countdown opens the input gate without waiting.
        Field settingsField = Core.class.getDeclaredField("SETTINGS_LEVEL_1");
        settingsField.setAccessible(true);
        GameSettings settings = (GameSettings) settingsField.get(null);
        GameScreen screen = new GameScreen(new GameState(1, 0, 3, 0, 0, 0),
                settings, false, 448, 520, 60);

        Cooldown inputDelay = (Cooldown) get(screen, "inputDelay");
        check(!inputDelay.checkFinished(), "level must start with a countdown");

        Method skip = GameScreen.class.getDeclaredMethod("skipCountdown");
        skip.setAccessible(true);
        skip.invoke(screen);
        check(inputDelay.checkFinished(), "skip must open the input gate");

        // A second skip must not throw or change anything.
        skip.invoke(screen);
        check(inputDelay.checkFinished(), "repeated skip must stay finished");

        // A skipped warp must stop hiding the ship right away.
        entity.Ship ship = new entity.Ship(10, 10);
        engine.WarpIn warpIn = new engine.WarpIn();
        warpIn.startLevel(6000);
        check(warpIn.update(ship, true), "planned warp must hide the ship");
        warpIn.skip();
        check(!warpIn.update(ship, true), "skipped warp must not hide the ship");
        check(!warpIn.takeImpact(), "skipped warp must not shake the screen");

        System.out.println("SkipCountdownTest: " + checks + " checks passed");
    }
}
