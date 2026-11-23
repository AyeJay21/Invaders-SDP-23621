package engine;

/**
 * The three game modes chosen on the Difficulty screen.
 *
 * A mode does not carry its own levels: it scales the ten level settings of
 * Core, so a mode stays balanced against the level design. The scaling is
 * only about how hard the game plays - rewards and prices are the same in
 * every mode.
 *
 * The numbers are read as: how many lives a run starts with, how many
 * columns and rows the enemy formation gains, how fast the enemies move, how
 * quickly they shoot, how many of them take two hits, and how fast their
 * bullets fly.
 */
public enum Difficulty {

	/** For a first run: fewer, slower and calmer robots. */
	EASY("Easy", 4, -1, -1, 0.75f, 1.35f, -0.15f, 3,
			"Fewer and slower robots, and an extra life."),
	/** The levels as they are designed. */
	MEDIUM("Medium", 3, 0, 0, 1.0f, 1.0f, 0.0f,
			GameSettings.DEFAULT_ENEMY_BULLET_SPEED,
			"The levels as they are meant to be played."),
	/** For players who know the levels: more, faster and tougher robots. */
	HARD("Hard", 3, 1, 1, 1.45f, 0.7f, 0.15f, 5,
			"More robots, faster shooting and tougher ones.");

	/** Narrowest formation a mode may produce, in columns. */
	private static final int MIN_COLUMNS = 3;
	/** Widest formation that still fits between the side margins. */
	private static final int MAX_COLUMNS = 8;
	/** Shortest formation a mode may produce, in rows. */
	private static final int MIN_ROWS = 2;
	/** Tallest formation that still leaves room to play above the ship. */
	private static final int MAX_ROWS = 6;
	/** Shortest gap between two enemy shots, in milliseconds. */
	private static final int MIN_SHOOTING_INTERVAL = 150;

	/** Text drawn on the Difficulty screen. */
	private final String label;
	/** Lives a run starts with in this mode, and can never exceed. */
	private final int startingLives;
	/** Columns added to every level's enemy formation. */
	private final int extraColumns;
	/** Rows added to every level's enemy formation. */
	private final int extraRows;
	/** Factor applied to the enemies' speed. */
	private final float speedFactor;
	/** Factor applied to the time between two enemy shots. */
	private final float shootingTimeFactor;
	/** Share of the formation that becomes two-hit enemies, on top. */
	private final float extraToughShare;
	/** Speed of the enemies' bullets, in pixels per frame. */
	private final int enemyBulletSpeed;
	/** One line describing the mode. */
	private final String description;

	/**
	 * Constructor, establishes the numbers a mode scales the levels with.
	 *
	 * @param label
	 *            Text drawn on the Difficulty screen.
	 * @param startingLives
	 *            Lives a run starts with.
	 * @param extraColumns
	 *            Columns added to every formation.
	 * @param extraRows
	 *            Rows added to every formation.
	 * @param speedFactor
	 *            Factor applied to the enemies' speed.
	 * @param shootingTimeFactor
	 *            Factor applied to the time between two enemy shots.
	 * @param extraToughShare
	 *            Share of the formation that becomes two-hit enemies.
	 * @param enemyBulletSpeed
	 *            Speed of the enemies' bullets.
	 * @param description
	 *            One line describing the mode.
	 */
	Difficulty(final String label, final int startingLives,
			final int extraColumns, final int extraRows,
			final float speedFactor, final float shootingTimeFactor,
			final float extraToughShare, final int enemyBulletSpeed,
			final String description) {
		this.label = label;
		this.startingLives = startingLives;
		this.extraColumns = extraColumns;
		this.extraRows = extraRows;
		this.speedFactor = speedFactor;
		this.shootingTimeFactor = shootingTimeFactor;
		this.extraToughShare = extraToughShare;
		this.enemyBulletSpeed = enemyBulletSpeed;
		this.description = description;
	}

	/**
	 * Scales one level's settings to this mode. Everything the level itself
	 * decides - the diagonal movement, for example - is kept as it is.
	 *
	 * @param level
	 *            The level's own settings.
	 * @return A new settings object for the same level in this mode.
	 */
	public final GameSettings apply(final GameSettings level) {
		int columns = clamp(level.getFormationWidth() + this.extraColumns,
				MIN_COLUMNS, MAX_COLUMNS);
		int rows = clamp(level.getFormationHeight() + this.extraRows,
				MIN_ROWS, MAX_ROWS);
		int speed = Math.max(1,
				Math.round(level.getBaseSpeed() * this.speedFactor));
		int shooting = Math.max(MIN_SHOOTING_INTERVAL,
				Math.round(level.getShootingFrecuency()
						* this.shootingTimeFactor));
		int enemies = columns * rows;
		int tough = Math.round(level.getTwoHitEnemyCount()
				+ enemies * this.extraToughShare);
		// At least one ordinary enemy has to be left, or the formation could
		// never be cleared without a two-hit kill.
		tough = clamp(tough, 0, enemies - 1);

		return new GameSettings(columns, rows, speed, shooting,
				level.isDiagonalMovement(), tough, this.enemyBulletSpeed);
	}

	/** Keeps a value inside a range. */
	private static int clamp(final int value, final int min, final int max) {
		return Math.max(min, Math.min(max, value));
	}

	/** @return Text drawn on the Difficulty screen. */
	public final String getLabel() {
		return this.label;
	}

	/** @return One line describing the mode. */
	public final String getDescription() {
		return this.description;
	}

	/** @return Lives a run starts with, and can never exceed. */
	public final int getStartingLives() {
		return this.startingLives;
	}

	/** @return How fast the enemies move, compared to the level's own speed. */
	public final float getSpeedFactor() {
		return this.speedFactor;
	}

	/** @return Speed of the enemies' bullets, in pixels per frame. */
	public final int getEnemyBulletSpeed() {
		return this.enemyBulletSpeed;
	}
}
