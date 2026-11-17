package engine;

import java.util.Locale;

/**
 * Remembers who is playing while the game is open.
 * Names follow the arcade convention: exactly three letters, A to Z.
 */
public final class PlayerSession {

	/** Number of letters in a player name. */
	public static final int NAME_LENGTH = 3;
	/** Name used when nobody has been chosen yet. */
	public static final String DEFAULT_NAME = "AAA";

	/** Singleton instance of the class. */
	private static PlayerSession instance;

	/** Current player name, null until a player has been chosen. */
	private String name;

	/**
	 * Private constructor.
	 */
	private PlayerSession() {
	}

	/**
	 * Returns shared instance of PlayerSession.
	 *
	 * @return Shared instance of PlayerSession.
	 */
	public static PlayerSession getInstance() {
		if (instance == null)
			instance = new PlayerSession();
		return instance;
	}

	/**
	 * @return True when a player has been chosen.
	 */
	public boolean hasPlayer() {
		return this.name != null;
	}

	/**
	 * @return Current player name, or null if nobody has been chosen.
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * @return Current player name, or the default name if nobody has been
	 *         chosen. Never null, so it is safe to save.
	 */
	public String getNameOrDefault() {
		return this.name != null ? this.name : DEFAULT_NAME;
	}

	/**
	 * Sets the current player. The name is trimmed and upper-cased first.
	 *
	 * @param name
	 *            Player name, three letters.
	 * @throws IllegalArgumentException
	 *             If the name is not exactly three letters A to Z.
	 */
	public void setName(final String name) {
		String cleaned = normalize(name);
		if (!isValidName(cleaned))
			throw new IllegalArgumentException("Invalid player name: " + name);
		this.name = cleaned;
	}

	/**
	 * Forgets the current player.
	 */
	public void clear() {
		this.name = null;
	}

	/**
	 * Trims a name and converts it to upper case.
	 *
	 * @param name
	 *            Raw name, may be null.
	 * @return Cleaned name, empty if the input was null.
	 */
	public static String normalize(final String name) {
		if (name == null)
			return "";
		return name.trim().toUpperCase(Locale.ROOT);
	}

	/**
	 * Checks a name against the arcade rule.
	 *
	 * @param name
	 *            Name to check, expected already normalized.
	 * @return True if it is exactly three letters A to Z.
	 */
	public static boolean isValidName(final String name) {
		if (name == null || name.length() != NAME_LENGTH)
			return false;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c < 'A' || c > 'Z')
				return false;
		}
		return true;
	}
}