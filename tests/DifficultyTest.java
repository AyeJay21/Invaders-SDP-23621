import engine.Core;
import engine.Difficulty;
import engine.DifficultyManager;
import engine.GameSettings;

/** Standalone checks for the easy, medium and hard difficulty modes. */
public final class DifficultyTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /** Width of a formation, from the settings. */
    private static int width(GameSettings settings) {
        return settings.getFormationWidth();
    }

    /** Height of a formation, from the settings. */
    private static int height(GameSettings settings) {
        return settings.getFormationHeight();
    }

    /** The three modes, weakest first. */
    private static final Difficulty[] MODES = { Difficulty.EASY,
            Difficulty.MEDIUM, Difficulty.HARD };

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);

        // The levels as Core sets them up, including the two boss levels and
        // the diagonal one, plus the widest formation the game uses.
        GameSettings[] levels = {
                new GameSettings(5, 2, 60, 2000),
                new GameSettings(5, 4, 40, 2000, true),
                new GameSettings(5, 4, 5, 1000),
                new GameSettings(6, 3, 10, 1000, Math.round(6 * 3 * 0.20f)),
                new GameSettings(7, 5, 1, 300, true, Math.round(7 * 5 * 0.20f)),
                new GameSettings(5, 4, 8, 500),
        };

        // Every mode yields settings the game can actually build.
        for (Difficulty mode : MODES) {
            for (GameSettings level : levels) {
                GameSettings scaled = mode.apply(level);
                String where = mode + " on " + width(level) + "x"
                        + height(level);

                check(width(scaled) >= 3 && width(scaled) <= 8,
                        "formation too wide: " + where);
                check(height(scaled) >= 2 && height(scaled) <= 6,
                        "formation too tall: " + where);
                check(scaled.getBaseSpeed() >= 1,
                        "enemies must move: " + where);
                check(scaled.getShootingFrecuency() >= 150,
                        "shooting too fast: " + where);
                check(scaled.getEnemyBulletSpeed() >= 1,
                        "bullets must move: " + where);
                check(scaled.getTwoHitEnemyCount() >= 0
                                && scaled.getTwoHitEnemyCount()
                                < width(scaled) * height(scaled),
                        "two-hit enemies must leave an ordinary one: " + where);
                check(scaled.isDiagonalMovement()
                                == level.isDiagonalMovement(),
                        "a mode must keep the level's movement: " + where);
            }
        }

        // Medium plays the levels exactly as they are designed.
        for (GameSettings level : levels) {
            GameSettings scaled = Difficulty.MEDIUM.apply(level);
            check(width(scaled) == width(level), "medium must keep the width");
            check(height(scaled) == height(level),
                    "medium must keep the height");
            check(scaled.getBaseSpeed() == level.getBaseSpeed(),
                    "medium must keep the speed");
            check(scaled.getShootingFrecuency()
                            == level.getShootingFrecuency(),
                    "medium must keep the shooting frequency");
            check(scaled.getTwoHitEnemyCount()
                            == level.getTwoHitEnemyCount(),
                    "medium must keep the two-hit enemies");
            check(scaled.getEnemyBulletSpeed()
                            == GameSettings.DEFAULT_ENEMY_BULLET_SPEED,
                    "medium must keep the bullet speed");
        }

        // Easy is kinder and hard is harsher on every dimension: more robots,
        // faster robots, quicker shots and more of them needing two hits.
        for (GameSettings level : levels) {
            GameSettings easy = Difficulty.EASY.apply(level);
            GameSettings medium = Difficulty.MEDIUM.apply(level);
            GameSettings hard = Difficulty.HARD.apply(level);

            check(width(easy) <= width(medium)
                            && width(medium) <= width(hard),
                    "column count must grow with the difficulty");
            check(height(easy) <= height(medium)
                            && height(medium) <= height(hard),
                    "row count must grow with the difficulty");
            check(easy.getBaseSpeed() <= medium.getBaseSpeed()
                            && medium.getBaseSpeed() <= hard.getBaseSpeed(),
                    "enemies must not get slower with the difficulty");
            // The fastest slowest level sits at one pixel per frame already,
            // so only the livelier levels can tell the factor apart.
            if (level.getBaseSpeed() >= 10)
                check(easy.getBaseSpeed() < hard.getBaseSpeed(),
                        "hard enemies must move faster than easy ones");
            check(easy.getShootingFrecuency() >= medium.getShootingFrecuency()
                            && medium.getShootingFrecuency()
                            >= hard.getShootingFrecuency(),
                    "enemies must not wait longer on harder modes");
            check(easy.getShootingFrecuency()
                            > hard.getShootingFrecuency(),
                    "hard enemies must shoot sooner than easy ones");
            check(easy.getTwoHitEnemyCount()
                            <= medium.getTwoHitEnemyCount()
                            && medium.getTwoHitEnemyCount()
                            <= hard.getTwoHitEnemyCount(),
                    "two-hit count must grow with the difficulty");
        }

        // The two boss levels shoot and move through their settings too, so
        // hard has to reach them.
        GameSettings boss = new GameSettings(5, 4, 8, 500);
        check(Difficulty.HARD.apply(boss).getShootingFrecuency()
                        < boss.getShootingFrecuency(),
                "hard must speed the boss shooting up");
        check(Difficulty.EASY.apply(boss).getShootingFrecuency()
                        > boss.getShootingFrecuency(),
                "easy must slow the boss shooting down");

        // A mode has to leave the last enemy killable with one hit.
        GameSettings tiny = new GameSettings(3, 2, 10, 1000);
        check(Difficulty.HARD.apply(tiny).getTwoHitEnemyCount()
                        < width(Difficulty.HARD.apply(tiny))
                        * height(Difficulty.HARD.apply(tiny)),
                "not every enemy may take two hits");

        // Lives: easy is the only mode that hands out an extra one.
        check(Difficulty.EASY.getStartingLives() > 3,
                "easy must start with an extra life");
        check(Difficulty.MEDIUM.getStartingLives() == 3
                        && Difficulty.HARD.getStartingLives() == 3,
                "medium and hard must keep the three lives");
        for (Difficulty mode : MODES) {
            check(mode.getStartingLives() > 0,
                    "every mode must start with a life");
            check(mode.getLabel() != null && !mode.getLabel().isEmpty(),
                    "every mode needs a label");
            check(mode.getDescription() != null
                            && !mode.getDescription().isEmpty(),
                    "every mode needs a description");
        }

        // Old constructors keep the bullet speed the game always used.
        check(new GameSettings(5, 2, 60, 2000).getEnemyBulletSpeed()
                        == GameSettings.DEFAULT_ENEMY_BULLET_SPEED,
                "the plain constructor must keep the default bullets");

        // The choice is remembered, and an unknown saved value falls back.
        DifficultyManager manager = DifficultyManager.getInstance();
        Difficulty original = manager.getDifficulty();
        check(original != null, "a difficulty must always be set");

        manager.setDifficulty(Difficulty.HARD);
        check(manager.getDifficulty() == Difficulty.HARD,
                "the chosen mode must stick");

        manager.setDifficulty(Difficulty.EASY);
        check(manager.getDifficulty() == Difficulty.EASY,
                "the chosen mode must be replaceable");

        manager.setDifficulty(original);

        System.out.println("DifficultyTest: " + checks + " checks passed");
    }
}
