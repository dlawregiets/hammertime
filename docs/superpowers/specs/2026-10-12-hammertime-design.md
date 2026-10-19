# HammerTime — Server-Side Cube Mining Mod (Fabric, MC 26.2)

**Date:** 2026-10-12
**Status:** Design approved; awaiting spec review
**Target:** Minecraft Java Edition **26.2** (Chaos Cubed), Fabric server-side mod

## 1. Purpose

Let a player mine a **cube-shaped region** of blocks with a single break, similar to
"hammer" mods, on a **Fabric Java server**.

The player connects from a **Bedrock client through Geyser**. Therefore the mod must be
**100% server-side**: no client mods, no resource packs, no datapacks. Everything is
server-authoritative and works with a vanilla/Geyser client.

**Success criteria**

- Sneak-breaking a block with a suitable tool mines a `size³` cube of identical blocks.
- Cube size is selectable per player (3×3×3 or 5×5×5) via an in-game command.
- Drops, Fortune/Silk Touch, XP, durability, and statistics behave like normal mining.
- A player without the mod (any Java client, and all Bedrock/Geyser clients) needs
  nothing installed.

**Non-goals**

- Client-side rendering or UI (no client code at all).
- Adding new tools/items. Trigger is the vanilla sneak-break.
- World protection / region claims (no external protection mods).
- Mining blocks other than the exact type of the target block.

## 2. Decisions (agreed with user)

| Topic | Decision |
|---|---|
| Minecraft version | 26.2 |
| Client requirement | None — server-only mod |
| Dependencies | Fabric API allowed (Loader + Fabric API). No other libraries. |
| Trigger | Block break while **sneaking**, holding a tool that can correctly harvest the target |
| Eligible blocks | **Tool-gated only** — only if the held tool is correct for the target block |
| Match rule | **Exact same `Block`** as the target (state/rotation ignored) |
| Cube orientation | **Drill into the wall, target centered**: looked-at face is the front face; target is the center of that face; cube extends `size` blocks into the wall |
| Cube size | 3×3×3 or 5×5×5, **per player**, via command |
| Config | Global JSON config + per-player JSON store (Gson, bundled with MC) |
| Commands | `/hammer` self-service for everyone; op-level for others/reload |
| Default | Enabled at size 3 for all players |

## 3. Behavior

### 3.1 Trigger conditions

`PlayerBlockBreakEvents.AFTER` fires on the server after a successful break. The mod
proceeds only if **all** hold:

1. `requireSneak` config is true (default) **and** `player.isShiftKeyDown()` is true.
2. The player is not a spectator.
3. The main-hand `ItemStack` is non-empty.
4. `stack.isCorrectToolForDrops(targetState)` is true — the held tool can properly
   harvest the target. (This is the "tool-gated only" rule; it also means a pickaxe
   cannot touch chests, soft plants, etc.)
5. The player has hammer enabled in their per-player settings.
6. The break happened in a `ServerLevel`.

### 3.2 Cube geometry

Given target position `P` and the player's look vector `look`:

- `forward = Direction.getNearest(look.x, look.y, look.z)` — the axis the player looks
  along, i.e. *into the wall*. (Exact overload verified at implementation.)
- The **front face** is the plane perpendicular to `forward`, centered on `P`.
- The cube extends `size` blocks **into the wall** along `forward` (depth offsets
  `0 … size-1`).
- The two perpendicular axes are derived from `forward`; the cube is symmetric on both,
  so axis orientation does not affect the result.
- `half = size / 2` (integer): `3 → 1`, `5 → 2`.

Offsets iterated:

```
for depth  in 0 .. size-1
  for lateral in -half .. half
    for vertical in -half .. half
      candidate = P + forward*depth + right*lateral + up*vertical
```

Total candidate positions = `size³` = **27** or **125**.

**Note:** the target block is at the **center of the front face**, not at a corner of
the cube. The already-broken target position is skipped (it is air by now).

### 3.3 Matching and breaking

- A candidate is broken only if `level.getBlockState(candidate).getBlock() ==
  targetState.getBlock()` (exact block, state ignored) and it is not air.
- Each matching block is broken through the **vanilla player-break path**
  (`ServerPlayerGameMode#destroyBlock`, exact method verified at implementation) so that
  drops, enchantments, XP, tool durability, statistics, sounds, and block-entity
  contents behave exactly like normal mining.
- A **re-entrancy guard** (per-player set/flag on the server thread) prevents the extra
  breaks from recursively triggering the event.

### 3.4 Safety rules

- **Tool check each iteration**: before each break, re-verify the tool is still correct
  (it may have broken or been swapped). If not, stop.
- Skip air / positions outside the world build height.
- Cap breaks at `maxBlocksPerBreak` (default 128, above the 125 max) to bound work.
- Creative insta-break: the feature applies in creative too, gated by the same rules.
  Spectators are excluded. (An explicit creative on/off config option is out of scope
  for the first version.)
- No line-of-sight requirement — the whole cube is mined, like hammer mods.
- `durabilityPerBlock` (default true): when true, every broken block consumes one
  durability point via the vanilla break path. When false, only the originally broken
  block consumes durability and the extra blocks do not.

## 4. Configuration

### 4.1 Global — `config/hammertime.json`

```json
{
  "defaultSize": 3,
  "allowedSizes": [3, 5],
  "requireSneak": true,
  "durabilityPerBlock": true,
  "maxBlocksPerBreak": 128,
  "enabledByDefault": true
}
```

- Missing file → written with defaults on first load.
- Malformed file → log a warning, fall back to defaults, do not crash.

### 4.2 Per-player — `config/hammertime-players.json`

```json
{
  "<uuid>": { "size": 3, "enabled": true }
}
```

- Keyed by player UUID string.
- Saved when a player changes their settings and on server stop.
- Players absent from the file use the global defaults.

Both files use **Gson**, which Minecraft bundles — no added dependency.

## 5. Commands

Brigadier tree registered via `CommandRegistrationCallback`:

| Command | Permission | Effect |
|---|---|---|
| `/hammer size <n>` | everyone | Set own cube size (must be in `allowedSizes`) |
| `/hammer on` / `/hammer off` | everyone | Enable/disable own hammer |
| `/hammer status` | everyone | Show own size + enabled state |
| `/hammer <player> size <n>` | op (level 2) | Set another player's size |
| `/hammer reload` | op (level 2) | Reload global config from disk |

Invalid sizes return a clear error listing `allowedSizes`.

## 6. Architecture

Small, single-purpose units:

| Unit | Responsibility |
|---|---|
| `HammerTime` | `ModInitializer`: register event + command, load config/store |
| `HammerConfig` | Global settings; Gson load/save |
| `PlayerSettingsStore` | Per-player size/enabled; load/save keyed by UUID |
| `PlayerSettings` | Immutable per-player record (size, enabled) |
| `CubeGeometry` | **Pure function** `(size, forward, half) → List<offset>`; no MC types, unit-testable |
| `CubeMiner` | Filter candidates by block type/tool, perform breaks, re-entrancy guard |
| `HammerCommand` | Brigadier command tree |

`fabric.mod.json` declares `"environment": "server"` so clients are never required.

## 7. Toolchain (26.2, official mappings)

- Java Edition is **unobfuscated** since 26.1 → code against **Mojang official names**.
- **Java SE 25** (26.2 minimum).
- **Gradle 9.5.1**, **Fabric Loom 1.17+** (example mod uses 1.18-SNAPSHOT).
- **Fabric Loader 0.19.5**, **Fabric API `0.161.0+26.2`**.
- New `net.fabricmc.fabric-loom` plugin: plain `implementation`, plain `jar` output
  (no `modImplementation` / `remapJar`).

## 8. Testing

### 8.1 Automated
- Unit tests for `CubeGeometry`: exact expected offset sets for size 3 and 5 across all
  six `forward` directions.

### 8.2 Manual in-game matrix
- Sizes 3 and 5.
- Faces: horizontal (wall), floor (looking down), ceiling (looking up).
- Blocks: stone, ore, wood/log, dirt.
- Negative: tool too weak (must not trigger), not sneaking (must not trigger), wrong
  block type in cube (left untouched).
- Durability: tool breaks mid-cube → loop stops cleanly.
- **Geyser/Bedrock client** end-to-end (highest-risk item — test first).

## 9. Risks

| Risk | Mitigation |
|---|---|
| Geyser sneak + block-break translation differs | Verify end-to-end before building the rest |
| Re-entrancy / infinite loop when breaking extras | Per-player guard |
| Tool breaks mid-loop | Re-check correctness each iteration |
| Many blocks in one tick | `maxBlocksPerBreak` cap; max is 125 |
| 26.2 API names differ from expectations | Verify exact signatures against the decompiled/official sources at implementation |
