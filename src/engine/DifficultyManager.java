package engine;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Keeps the difficulty mode chosen on the Difficulty screen.
 *
 * The choice is saved next to the game like the other saves, so a run starts
 * in the last mode even after a restart. Saving never throws: if the file
 * cannot be written, the choice still holds for the running session.
 */
public final class DifficultyManager {

	/** Name of the save file. */
	private static final String FILE_NAME = "difficulty";
	/** Property the mode is saved under. */
	private static final String MODE_KEY = "mode";
	/** Mode used until the player chooses another one. */
	private static final Difficulty DEFAULT = Difficulty.MEDIUM;

	/** Shared instance. */
	private static DifficultyManager instance;

	/** Application logger. */
	private final Logger logger;
	/** Mode in use. */
	private Difficulty difficulty;

	/**
	 * Loads the saved mode. Never throws: a missing or broken file just means
	 * the default mode.
	 */
	private DifficultyManager() {
		this.logger = Core.getLogger();
		this.difficulty = DEFAULT;
		load();
	}

	/** @return Shared difficulty manager. */
	public static DifficultyManager getInstance() {
		if (instance == null)
			instance = new DifficultyManager();
		return instance;
	}

	/** @return Mode in use. */
	public Difficulty getDifficulty() {
		return this.difficulty;
	}

	/**
	 * Remembers the mode for the next runs and saves it.
	 *
	 * @param mode
	 *            Mode to use; null is ignored.
	 */
	public void setDifficulty(final Difficulty mode) {
		if (mode == null || mode == this.difficulty)
			return;
		this.difficulty = mode;
		save();
		this.logger.info("Difficulty set to " + mode.getLabel() + ".");
	}

	/** Reads the saved mode, falling back to the default for anything odd. */
	private void load() {
		Properties saved = new Properties();
		try {
			File file = getSaveFile();
			if (!file.exists())
				return;
			try (Reader reader = Files.newBufferedReader(file.toPath(),
					StandardCharsets.UTF_8)) {
				saved.load(reader);
			}
		} catch (IOException | IllegalArgumentException e) {
			this.logger.warning("Couldn't load the difficulty.");
			return;
		}

		String name = saved.getProperty(MODE_KEY, DEFAULT.name()).trim();
		try {
			this.difficulty = Difficulty.valueOf(name);
		} catch (IllegalArgumentException e) {
			this.difficulty = DEFAULT;
			this.logger.warning("Skipping unknown difficulty " + name);
		}
	}

	/**
	 * Writes the mode. Writes a temporary file first and then replaces the
	 * save, so a crash while saving never leaves a half-written file.
	 */
	private void save() {
		Properties saved = new Properties();
		saved.setProperty(MODE_KEY, this.difficulty.name());
		try {
			Path file = getSaveFile().toPath();
			Path temp = file.resolveSibling(FILE_NAME + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp,
					StandardCharsets.UTF_8)) {
				saved.store(writer, "Space Invaders difficulty mode");
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			this.logger.warning("Couldn't save the difficulty: "
					+ e.getMessage());
		}
	}

	/**
	 * @return The save file, next to the game like the other saves.
	 * @throws IOException
	 *             In case the application directory cannot be decoded.
	 */
	private File getSaveFile() throws IOException {
		String path = DifficultyManager.class.getProtectionDomain()
				.getCodeSource().getLocation().getPath();
		path = URLDecoder.decode(path, "UTF-8");
		return new File(new File(path).getParent(), FILE_NAME);
	}
}
