package screen;

import java.awt.event.KeyEvent;

import engine.Cooldown;
import engine.Core;
import engine.Difficulty;
import engine.DifficultyManager;

/**
 * Implements the difficulty screen.
 *
 * The three game modes are listed with the one in use marked, so the player
 * sees what a mode changes before choosing it. Moving the cursor applies the
 * mode right away, like the volumes on the settings screen, and Escape goes
 * back to the menu. The choice is kept for the next runs.
 */
public class DifficultyScreen extends Screen {

	/** Milliseconds between selection steps. */
	private static final int SELECTION_TIME = 200;
	/** Spacing between the mode rows. */
	private static final int SPACING = 30;

	/** Cooldown for moving the cursor. */
	private final Cooldown selectionCooldown;
	/** Mode in use, also the one the cursor is on. */
	private Difficulty selected;

	/**
	 * Constructor, establishes the properties of the screen.
	 *
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second, frame rate at which the game is run.
	 */
	public DifficultyScreen(final int width, final int height,
			final int fps) {
		super(width, height, fps);
		this.returnCode = 1;
		this.selected = DifficultyManager.getInstance().getDifficulty();
		this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
		this.selectionCooldown.reset();
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

		if (this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE)
				&& this.inputDelay.checkFinished()) {
			this.isRunning = false;
			return;
		}

		if (this.selectionCooldown.checkFinished()) {
			if (this.inputManager.isKeyDown(KeyEvent.VK_UP)
					|| this.inputManager.isKeyDown(KeyEvent.VK_W)) {
				step(-1);
			} else if (this.inputManager.isKeyDown(KeyEvent.VK_DOWN)
					|| this.inputManager.isKeyDown(KeyEvent.VK_S)) {
				step(1);
			}
		}

		if (this.inputManager.isMouseMoved()) {
			final int mouseY = this.inputManager.getMouseY();
			for (int row = 0; row < Difficulty.values().length; row++)
				if (this.drawManager.isOnMenuRow(rowBaseline(row), SPACING,
						mouseY))
					choose(Difficulty.values()[row]);
		}
	}

	/**
	 * Moves the cursor one mode up or down, wrapping around at both ends.
	 *
	 * @param direction
	 *            -1 for up, 1 for down.
	 */
	private void step(final int direction) {
		final Difficulty[] modes = Difficulty.values();
		final int index = Math.floorMod(this.selected.ordinal() + direction,
				modes.length);
		choose(modes[index]);
	}

	/**
	 * Puts the cursor on a mode and uses it from now on.
	 *
	 * @param mode
	 *            Mode to use.
	 */
	private void choose(final Difficulty mode) {
		if (mode == this.selected)
			return;
		this.selected = mode;
		this.selectionCooldown.reset();
		DifficultyManager.getInstance().setDifficulty(mode);
	}

	/**
	 * Baseline of a mode row. Drawing and mouse hover both use this, so they
	 * cannot drift apart.
	 *
	 * @param row
	 *            Row index, from the top.
	 * @return Baseline of the row's text.
	 */
	private int rowBaseline(final int row) {
		return this.getHeight() / 3 + SPACING * row;
	}

	/**
	 * Draws the elements associated with the screen.
	 */
	private void draw() {
		this.drawManager.initDrawing(this);
		this.drawManager.drawScreenTitle(this, MenuItem.DIFFICULTY.getTitle());

		final Difficulty current = DifficultyManager.getInstance()
				.getDifficulty();
		final Difficulty[] modes = Difficulty.values();
		for (int row = 0; row < modes.length; row++) {
			final String mark = modes[row] == current ? "* " : "  ";
			this.drawManager.drawMenuRow(this,
					mark + modes[row].getLabel(), rowBaseline(row),
					modes[row] == this.selected);
		}

		final int detailsRow = modes.length + 1;
		this.drawManager.drawMenuRow(this,
				"starting lives: " + this.selected.getStartingLives(),
				rowBaseline(detailsRow), false);
		this.drawManager.drawMenuRow(this, this.selected.getDescription(),
				rowBaseline(detailsRow + 1), false);
		this.drawManager.drawKeyHints(this, "arrows choose, esc back");

		this.drawManager.completeDrawing(this);
	}
}
