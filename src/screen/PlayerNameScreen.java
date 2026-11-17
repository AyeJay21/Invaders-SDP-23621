package screen;

import java.awt.event.KeyEvent;

import engine.Cooldown;
import engine.Core;
import engine.PlayerSession;

/**
 * Implements the player name screen, shown once when the game starts.
 * The player picks a three-letter arcade name, which is used to save the
 * scores of every game played until the application is closed.
 */
public class PlayerNameScreen extends Screen {

	/** Milliseconds between changes in user selection. */
	private static final int SELECTION_TIME = 200;
	/** Code of first mayus character. */
	private static final int FIRST_CHAR = 'A';
	/** Code of last mayus character. */
	private static final int LAST_CHAR = 'Z';

	/** Player name being edited. */
	private char[] name;
	/** Character of the name selected for change. */
	private int nameCharSelected;
	/** Time between changes in user selection. */
	private Cooldown selectionCooldown;

	/**
	 * Constructor, starts from the last player's name if there is one.
	 *
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second, frame rate at which the game is run.
	 */
	public PlayerNameScreen(final int width, final int height, final int fps) {
		super(width, height, fps);

		String last = Core.getFileManager().loadLastPlayer();
		this.name = (last != null ? last : PlayerSession.DEFAULT_NAME)
				.toCharArray();
		this.nameCharSelected = 0;
		this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
		this.selectionCooldown.reset();
		// 0 quits the game; confirming a name sets it to 1.
		this.returnCode = 0;
	}

	/**
	 * Starts the action.
	 *
	 * @return Next screen code: 1 once a name is confirmed, 0 to quit.
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
		if (!this.inputDelay.checkFinished())
			return;

		if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
			this.returnCode = 0;
			this.isRunning = false;
			return;
		}
		if (inputManager.isKeyDown(KeyEvent.VK_SPACE)
				|| inputManager.isKeyDown(KeyEvent.VK_ENTER)) {
			confirm();
			return;
		}

		if (this.selectionCooldown.checkFinished()) {
			if (inputManager.isKeyDown(KeyEvent.VK_RIGHT)) {
				this.nameCharSelected = (this.nameCharSelected + 1)
						% PlayerSession.NAME_LENGTH;
				this.selectionCooldown.reset();
			}
			if (inputManager.isKeyDown(KeyEvent.VK_LEFT)) {
				this.nameCharSelected = (this.nameCharSelected
						+ PlayerSession.NAME_LENGTH - 1)
						% PlayerSession.NAME_LENGTH;
				this.selectionCooldown.reset();
			}
			if (inputManager.isKeyDown(KeyEvent.VK_UP)) {
				changeLetter(1);
				this.selectionCooldown.reset();
			}
			if (inputManager.isKeyDown(KeyEvent.VK_DOWN)) {
				changeLetter(-1);
				this.selectionCooldown.reset();
			}
		}
	}

	/**
	 * Moves the selected letter up or down, wrapping from Z to A.
	 *
	 * @param step
	 *            1 for the next letter, -1 for the previous one.
	 */
	private void changeLetter(final int step) {
		int letter = this.name[this.nameCharSelected] + step;
		if (letter > LAST_CHAR)
			letter = FIRST_CHAR;
		if (letter < FIRST_CHAR)
			letter = LAST_CHAR;
		this.name[this.nameCharSelected] = (char) letter;
	}

	/**
	 * Registers the chosen name as the current player and remembers it for
	 * the next time the game is opened.
	 */
	private void confirm() {
		String chosen = new String(this.name);
		PlayerSession.getInstance().setName(chosen);
		Core.getFileManager().saveLastPlayer(chosen);
		logger.info("Player chosen: " + chosen);
		this.returnCode = 1;
		this.isRunning = false;
	}

	/**
	 * Draws the elements associated with the screen.
	 */
	private void draw() {
		drawManager.initDrawing(this);

		drawManager.drawScreenTitle(this, "WELCOME");
		drawManager.drawPlayerNameInput(this, this.name,
				this.nameCharSelected, "Choose your player name");
		drawManager.drawKeyHints(this, "arrows change, space ok, esc quit");

		drawManager.completeDrawing(this);
	}
}