package screen;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import engine.Core;
import engine.GameState;
import engine.PlayerSession;
import engine.Score;

/**
 * Implements the score screen.
 * 
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 * 
 */
public class ScoreScreen extends Screen {

	/** Maximum number of high scores. */
	private static final int MAX_HIGH_SCORE_NUM = 7;

	/** Current score. */
	private int score;
	/** Player lives left. */
	private int livesRemaining;
	/** Total bullets shot by the player. */
	private int bulletsShot;
	/** Total ships destroyed by the player. */
	private int shipsDestroyed;
	/** List of past high scores. */
	private List<Score> highScores;
	/** Checks if current score is a new high score. */
	private boolean isNewRecord;
	/** Name of the player the score belongs to, chosen at game start. */
	private String playerName;

	/**
	 * Constructor, establishes the properties of the screen.
	 * A score that makes the ranking is saved right away, so it is not lost
	 * if the window is closed on this screen.
	 * 
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second, frame rate at which the game is run.
	 * @param gameState
	 *            Current game state.
	 */
	public ScoreScreen(final int width, final int height, final int fps,
			final GameState gameState) {
		super(width, height, fps);

		this.score = gameState.getScore();
		this.livesRemaining = gameState.getLivesRemaining();
		this.bulletsShot = gameState.getBulletsShot();
		this.shipsDestroyed = gameState.getShipsDestroyed();
		Core.getAchievementManager().recordAccuracy(this.bulletsShot,
				this.shipsDestroyed, gameState.getLevelsCompletedRun());
		this.isNewRecord = false;
		this.playerName = PlayerSession.getInstance().getNameOrDefault();

		try {
			this.highScores = Core.getFileManager().loadHighScores();
			// A score of zero never makes the ranking.
			if (this.score > 0
					&& (highScores.size() < MAX_HIGH_SCORE_NUM
					|| highScores.get(highScores.size() - 1).getScore()
					< this.score)) {
				this.isNewRecord = true;
				saveScore();
			}

		} catch (IOException e) {
			logger.warning("Couldn't load high scores!");
		}
	}

	/**
	 * Starts the action.
	 * 
	 * @return Next screen code.
	 */
	public final int run() {
		super.run();

		return this.returnCode;
	}

	/**
	 * Updates the elements on screen and checks for events.
	 */
	protected final void update() {
		super.update();

		draw();
		if (this.inputDelay.checkFinished()) {
			if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
				// Return to main menu.
				this.returnCode = 1;
				this.isRunning = false;
			} else if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
				// Play again.
				this.returnCode = 2;
				this.isRunning = false;
			}
		}

	}

	/**
	 * Saves the score as a high score, under the current player's name.
	 */
	private void saveScore() {
		highScores.add(new Score(this.playerName, score));
		Collections.sort(highScores);
		if (highScores.size() > MAX_HIGH_SCORE_NUM)
			highScores.remove(highScores.size() - 1);

		try {
			Core.getFileManager().saveHighScores(highScores);
		} catch (IOException e) {
			logger.warning("Couldn't save high scores!");
		}
	}

	/**
	 * Draws the elements associated with the screen.
	 */
	private void draw() {
		drawManager.initDrawing(this);

		drawManager.drawGameOver(this, this.inputDelay.checkFinished(),
				this.isNewRecord);
		// No shots fired means 0% accuracy, not 0 / 0 = NaN.
		float accuracy = this.bulletsShot > 0
				? (float) this.shipsDestroyed / this.bulletsShot : 0f;
		drawManager.drawResults(this, this.score, this.livesRemaining,
				this.shipsDestroyed, accuracy, this.isNewRecord);

		if (this.isNewRecord)
			drawManager.drawNewRecordBanner(this, this.playerName);
		drawManager.completeDrawing(this);
	}
}
