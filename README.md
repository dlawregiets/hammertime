# HammerTime

Server-side Fabric mod for Minecraft 26.2. While sneaking, break a block with a tool
that can correctly harvest it to mine a cube of identical blocks.

- No client mod, resource pack, or datapack required (works from Bedrock via Geyser).
- Per-player size, toggled in game with `/hammer`.
- Requires Fabric Loader and Fabric API on the server only.

## Behavior

When a player breaks a block while **sneaking**, holding a tool that can correctly
harvest it, the mod mines a cube of blocks **identical to the target block**:

- The face you are looking at becomes the cube's **front face**.
- The target block sits at the **centre of that front face**.
- The cube extends **into the wall** (3 blocks deep for 3×3, 5 for 5×5).

Only blocks matching the target block exactly are broken. Drops, Fortune/Silk Touch,
XP, tool durability, and statistics behave exactly like normal mining. A tool that
cannot harvest the block (or an empty hand) does nothing extra.

## Configuration

Global config is written to `config/hammertime.json` on first run:

| Key | Default | Meaning |
|---|---|---|
| `defaultSize` | `3` | Cube size for players with no saved setting |
| `allowedSizes` | `[3, 5]` | Sizes players may select with `/hammer size` |
| `requireSneak` | `true` | Require the player to be sneaking |
| `durabilityPerBlock` | `true` | Each extra block consumes tool durability |
| `maxBlocksPerBreak` | `128` | Safety cap on blocks broken per swing |
| `enabledByDefault` | `true` | New players start with the hammer enabled |

Per-player settings are stored in `config/hammertime-players.json`, keyed by UUID.

## Commands

| Command | Permission | Effect |
|---|---|---|
| `/hammer size <n>` | everyone | Set your own cube size |
| `/hammer on` / `/hammer off` | everyone | Enable/disable your hammer |
| `/hammer status` | everyone | Show your size and enabled state |
| `/hammer <player> size <n>` | op (level 2) | Set another player's size |
| `/hammer reload` | op (level 2) | Reload `config/hammertime.json` |

## Building

Requires JDK 25.

```bash
./gradlew build
```

The mod jar is produced at `build/libs/hammertime-1.0.0.jar`. Drop it in the server's
`mods/` folder alongside Fabric API. Clients do not need it.
