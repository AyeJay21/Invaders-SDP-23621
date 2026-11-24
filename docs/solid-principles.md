# SOLID Principles Review (`SCRUM-47` — Gameplay)

This document summarizes a review of the **SOLID** principles across three key classes in the gameplay / economy area:
- `engine.CurrencyManager`
- `screen.GameScreen`
- `item.ItemSystem`

The goal is to highlight existing strengths, note coupling or responsibility overlaps, and suggest practical improvements aligned with the sprint workflow.

---

## 1. `engine.CurrencyManager`

### What Aligns Well with SOLID
- **Single Responsibility (SRP) — Invariant Protection:** The class protects coin balance rules cleanly (non-negative balance, capping at `Integer.MAX_VALUE` in `addCoins`, and atomic check-then-spend in `trySpend`).
- **Open/Closed (OCP) — Dedicated Currency Separation:** Keeping coin balance management separate from other currencies (such as diamonds) avoids cramming unrelated currency rules into a single class.

### Coupling & Responsibility Notes
- **Single Responsibility (SRP):** `CurrencyManager` handles both coin balance business logic (earning, spending, resetting, affordability checks) and disk persistence (`save()` calling `FileManager.getInstance().saveCoins(...)`).
- **Dependency Inversion (DIP):** Direct calls to `FileManager.getInstance()` tie the class to a concrete singleton, making isolated unit testing harder without touching persisted state.

### Practical Next Steps
- Consider introducing a storage abstraction if isolated unit tests are added (`SCRUM-49`), while preserving immediate save behavior during normal play.

---

## 2. `screen.GameScreen`

### What Aligns Well with SOLID
- **Single Responsibility (SRP) — Subsystem Delegation:** Several responsibilities are already delegated to specialized classes (`ItemSystem`, `CoinDropManager`, `MeteorManager`, `ScreenShake`, `GlitchEffect`, `PointsEffect`, `LightningChain`, `DamageDimEffect`), and `PauseOption` cleanly encapsulates pause menu navigation.
- **Open/Closed (OCP) — Event Subscriptions:** Subscribing visual effects to `GameEvents.Type.PLAYER_HIT` lets reactions be attached or removed without rewriting core collision loops.

### Coupling & Responsibility Notes
- **Single Responsibility (SRP):** `GameScreen` still coordinates many concerns in one place (main loop, input handling, collisions, normal waves vs. boss encounters `MidBoss`/`FinalBoss`, game-over sequence, coin collection and restart refunds, score/achievement popups, and rendering in `draw()`).
- **Liskov Substitution (LSP) & Open/Closed (OCP):** Several branches handle concrete types or special stages explicitly (such as `level == 5 || level == 10`, `midBoss != null` vs. `enemyShipFormation`, or `midBoss instanceof FinalBoss` in `resumeGameplay()`).
- **Dependency Inversion (DIP):** `GameScreen` directly references several singletons and static managers (`CurrencyManager.getInstance()`, `DiamondManager.getInstance()`, `PurchaseManager.getInstance()`, `SoundManager.getInstance()`, `Core.getAchievementManager()`, `BulletPool`, `CoinPool`).

### Practical Next Steps
- Keep extracting focused helpers when touching level restart, coin accounting, or UI/popup updates during future gameplay changes.
- Favor shared abstractions or polymorphic behavior when adding new enemy/boss behaviors or stage types.

---

## 3. `item.ItemSystem`

### What Aligns Well with SOLID
- **Single Responsibility (SRP) — Gameplay Bridge:** As documented in its class Javadoc, `ItemSystem` acts as a clean bridge between `GameScreen` and `ItemManager` / `ItemAPI` (coordinate/bounds translation, delta timing, and event-to-notice/log mapping).
- **Interface Segregation (ISP):** `ItemSystem.GameHooks` exposes a focused contract (`getLives()`, `addLife()`, `addScore(int)`) instead of passing the entire `GameScreen`.

### Coupling & Responsibility Notes
- **Single Responsibility (SRP):** Alongside run coordination, `ItemSystem` also contains static equipment ownership/equip state and file persistence (`ownedEquipment`, `equippedId`, `grantEquipment`, `equip`, `unequip`, `loadEquipment`, `saveEquipment`, `equipmentFile`), fallback drop rendering (`drawDrops(Graphics)`), and an effect-to-color mapping (`colorOf(EffectKind)`).
- **Open/Closed (OCP):** `onEvent(ItemEvent)` and `colorOf(EffectKind)` rely on `switch` blocks over enum values, meaning new event or effect kinds require edits in `ItemSystem`.
- **Dependency Inversion (DIP):** Static state (`current`, `ownedEquipment`, `equippedId`, `catalog`) and direct calls to `Core.getLogger()` and `Core.getAchievementManager()` couple the class to global/static entry points.

### Practical Next Steps
- Over time, consider separating equipment persistence and rendering helpers from per-run item coordination as the system evolves.

---

## Summary Matrix

| Principle | Key Strength | Main Watchpoint |
| :--- | :--- | :--- |
| **S** — Single Responsibility | Delegation to dedicated managers/effects | `GameScreen` coordinates many states; `ItemSystem` mixes run bridge, equipment persistence, and drawing |
| **O** — Open/Closed | Event-driven hooks (`GameEvents`) and separate currency handling | `switch` / conditional checks on effect/event enums and stage/boss types |
| **L** — Liskov Substitution | Distinct entity/boss hierarchy (`Ship`, `EnemyShip`, `Boss`) | Explicit type checks (`instanceof FinalBoss`, `midBoss` vs. formation branches) |
| **I** — Interface Segregation | `ItemSystem.GameHooks` stays narrow (lives & score) | Some APIs/views expose broad state structures |
| **D** — Dependency Inversion | `GameHooks` partially decouples item effects from `GameScreen` | Heavy reliance on singletons (`getInstance()`) and static `Core` access |