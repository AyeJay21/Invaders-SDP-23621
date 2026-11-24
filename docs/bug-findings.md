# Bug & Edge-Case Findings (`SCRUM-46` — Gameplay)

This document records potential bugs, edge cases, and state/control-flow risks identified during a code review of key gameplay and difficulty classes:
- `screen.GameScreen`
- `engine.DifficultyManager`
- `entity.EnemyShipFormation`

Note: This review documents observations without modifying gameplay behavior in this task.

---

## 1. `entity.EnemyShipFormation`

### Index Shift When Removing Multiple Empty Columns (`cleanUp`)
- **Location:** `EnemyShipFormation.cleanUp()`
- **Issue:** Empty column indices are collected into a `Set<Integer>` and removed in a loop (`this.enemyShips.remove(index)`). When more than one column is empty at the same time, removing an earlier index shifts subsequent list elements left, which can either remove the wrong column or throw `IndexOutOfBoundsException`.
- **Suggested Fix:** Remove columns in descending index order or iterate/remove empty columns safely without shifting remaining indices unexpectedly.

### Potential Out-of-Bounds Lookup in `destroy(...)`
- **Location:** `EnemyShipFormation.destroy(final EnemyShip destroyedShip)`
- **Issue:** When a destroyed ship is in `this.shooters`, the code searches `this.enemyShips` for the ship's column (`destroyedShipColumnIndex`). If the column was already removed earlier, `destroyedShipColumnIndex` remains `-1`, and `this.enemyShips.get(destroyedShipColumnIndex)` will throw `IndexOutOfBoundsException`.
- **Suggested Fix:** Guard against `destroyedShipColumnIndex < 0` (and remove the shooter entry safely if its column is no longer present).

### Formation Position / Health Initialization Edge Cases
- **Location:** `EnemyShipFormation(final GameSettings gameSettings)`
- **Issue:** `twoHitEnemyCount` from `gameSettings` is passed directly to `positions.subList(0, ...)`. If `twoHitEnemyCount` exceeds `nShipsWide * nShipsHigh` or is negative, `subList` can throw `IndexOutOfBoundsException` or `IllegalArgumentException`.
- **Suggested Fix:** Clamp `twoHitEnemyCount` to `[0, nShipsWide * nShipsHigh]`.

---

## 2. `screen.GameScreen`

### Quitting from Pause Menu Triggers Game Over Flow
- **Location:** `GameScreen.confirmPauseSelection()` (`case QUIT`)
- **Issue:** Selecting "Quit to menu" sets `this.lives = 0` and `this.paused = false` to reuse the existing end-of-run transition. However, this also triggers the game-over flow (`startGameOverSequence()`), treating a voluntary quit like losing all lives.
- **Suggested Fix:** Separate voluntary run exit from zero-lives game over if distinct end-of-run handling or stats are needed.

### Multi-Counter Coin State Across Level Restart
- **Location:** `GameScreen.restartLevel()`, `refundCoinsCollectedThisLevel()`, `updateCoins()`, `collectRemainingCoins()`
- **Issue:** Coin tracking relies on `CurrencyManager` alongside `coinsCollected` and `coinsCollectedThisLevel`, with partial rollback on restart. Any mismatch between screen state and wallet state can complicate restart consistency.
- **Suggested Fix:** Keep level coin accounting encapsulated and verify restart behavior across mid-level coin pickups and boss/normal stages.

---

## 3. `engine.DifficultyManager`

### Persistence Fallback When Save Fails
- **Location:** `DifficultyManager.save()` / `getSaveFile()`
- **Issue:** While missing/corrupted files safely fall back to `Difficulty.MEDIUM` and atomic writes are attempted via a `.tmp` file, any `IOException` during saving only logs a warning and leaves the new difficulty only in memory for the current session.
- **Suggested Fix:** Surface clear logs/diagnostics in environments where the code source path is read-only or non-standard.

---

## Summary Table

| Class | Severity | Main Risk / Edge Case |
| :--- | :--- | :--- |
| `EnemyShipFormation` | Medium | `IndexOutOfBoundsException` when removing multiple empty columns or resolving shooter column index; unguarded `subList` bound |
| `GameScreen` | Low–Medium | Pause "Quit to menu" reuses `lives = 0` game-over flow; multiple coin counters across restart |
| `DifficultyManager` | Low | Save failures fall back to in-memory session state only (expected by design, worth noting for edge environments) |