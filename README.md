# The-Starry-List Mod Documentation

**The-Starry-List** is an extensible server-side leaderboard mod for Minecraft Fabric. It provides six built-in boards, per-player sidebar settings, administrator tools, blacklist support, and hot-reloadable Groovy boards.

简体中文文档见[这里](./README.cn.md)。

Players can use an unmodified client: scoring, the sidebar, commands, and the `/starry` inventory menu all run on the server. This project is a complete rewrite of [TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList).

---

## Features

- Six built-in boards: mining, placing, mob kills, player kills, deaths, and travel distance.
- Vanilla scoreboard objectives with world-persistent scores.
- Independent board selection, visibility, rotation, and interval settings for every player.
- Inventory-style `/starry` settings menu with no client mod required.
- Player-name regex blacklist with automatic score freezing and restoration.
- Hot-reloadable Groovy boards and a generated editing SDK.
- Server messages and board presentations in English and Simplified Chinese.
- Optional Cloth Config and ModMenu editor for single-player and LAN hosts.

---

## Installation

For a dedicated server:

1. Install Fabric Loader and Fabric API.
2. Place The-Starry-List in the server's `mods/` directory.
3. Start the server. Configuration and Groovy directories are generated automatically.

The dedicated server does not need Cloth Config or ModMenu, and players do not install anything. For single-player or LAN graphical configuration, install The-Starry-List, Fabric API, Cloth Config, and ModMenu on the host client. The client editor only changes that game directory; it cannot configure a remote server.

---

## Built-in boards

| Board ID | Objective | Name | Statistic |
|---|---|---|---|
| `mining` | `sl_mining` | Mining | Blocks successfully broken by a player |
| `placing` | `sl_placing` | Placing | Successful player block placements |
| `mob_kills` | `sl_mob_kills` | Mob Kills | Non-player living entities killed by a player |
| `player_kills` | `sl_player_kills` | War God | Other players killed by a player |
| `deaths` | `sl_deaths` | Deaths | Player deaths from any cause |
| `travel_distance` | `sl_travel` | Travel Distance | Whole blocks from vanilla continuous-movement statistics |

Travel includes walking, swimming, climbing, flying, elytra, minecarts, boats, and supported mounts. Teleportation does not increase travel distance.

---

## Commands

### Player command

| Command | Description |
|---|---|
| `/starry` | Opens the player settings menu |

The menu lets each player choose boards, hide or show the sidebar, toggle rotation, change the rotation interval, or return to server defaults. These settings affect only that player.

### Administrator commands

`/starryadmin` requires vanilla permission level `2` by default. The server console is always allowed. Score and profile target arguments accept online players and vanilla selectors where shown.

| Command | Description |
|---|---|
| `/starryadmin` | Shows the administrator command hint |
| `/starryadmin reload` | Reloads JSON configuration and Groovy boards together |
| `/starryadmin prune` | Permanently removes saved data belonging to board IDs that no longer exist |
| `/starryadmin scripts validate` | Compiles and validates all Groovy files without applying them |
| `/starryadmin scripts reload` | Reloads Groovy boards while retaining the active JSON settings |
| `/starryadmin scripts list` | Lists script IDs, objectives, and active subscription counts |
| `/starryadmin score get <boardId> <player>` | Reads an online player's score |
| `/starryadmin score set <boardId> <targets> <value>` | Replaces scores with a signed integer |
| `/starryadmin score add <boardId> <targets> <value>` | Adds a signed integer to scores |
| `/starryadmin score reset <boardId> <targets>` | Removes the selected score entries |
| `/starryadmin score recalculate <boardId> <targets>` | Rebuilds online players' scores from the board's authoritative data |
| `/starryadmin score reset-all <boardId>` | Removes every score in one board |
| `/starryadmin profile get <player>` | Shows an online player's saved display profile |
| `/starryadmin profile reset <targets>` | Returns players to the server default profile |
| `/starryadmin profile reset-all` | Removes every personal display override |

---

## Configuration

### File location

```text
config/starrylist/starrylist.json
```

Every world/server startup automatically reloads the JSON file and Groovy boards once. After editing while the world is running, use `/starryadmin reload`. Cloth Config saves the same JSON file; saving the screen does not immediately change the running server, so reload afterward.

### Default configuration

```json
{
  "general": {
    "language": "en_us",
    "adminPermissionLevel": 2
  },
  "display": {
    "hiddenByDefault": false,
    "rotationEnabled": true,
    "rotationIntervalSeconds": 20,
    "enabledBoards": [
      "mining",
      "placing",
      "mob_kills"
    ]
  },
  "boards": {
    "disabledBoards": [],
    "enabledScriptBoards": []
  },
  "blacklist": {
    "playerNamePatterns": []
  }
}
```

### General settings

| Field | Type | Description |
|---|---|---|
| `general.language` | `string` | Server language discovered from bundled core resources and active script locales |
| `general.adminPermissionLevel` | `int` | Vanilla permission level required for `/starryadmin`, from `0` to `4` |

### Core and script languages

Core server languages are discovered at startup from this mod's
`assets/the-starry-list/lang/<locale>.json` resources. Adding a bundled language requires only a
valid flat Minecraft language JSON; no Java enum or registry edit is needed. Each file must include
its native display name, for example:

```json
{
  "starrylist.language.name": "English"
}
```

`en_us.json` is required and is the fallback for missing core translation keys. Bundled resources
are immutable after startup. By contrast, `config/starrylist/lang/*.json` contains only Groovy board
titles and lore and is rediscovered by script preview, validation, and reload.

### Default display settings

| Field | Type | Description |
|---|---|---|
| `display.hiddenByDefault` | `boolean` | Hides the sidebar for players using server defaults |
| `display.rotationEnabled` | `boolean` | Rotates through multiple default boards |
| `display.rotationIntervalSeconds` | `int` | Rotation interval from `1` to `3600` seconds |
| `display.enabledBoards` | `string[]` | Boards shown by the default player profile |

Array order does not change board order. An empty `enabledBoards` list is valid and displays no sidebar board.

### Board loading settings

| Field | Type | Description |
|---|---|---|
| `boards.disabledBoards` | `string[]` | Built-in board IDs that must not load |
| `boards.enabledScriptBoards` | `string[]` | Groovy board IDs explicitly allowed to load |

Built-in boards load by default. Every newly generated or imported Groovy board is disabled until its ID is added to `enabledScriptBoards` or enabled in Cloth Config. Loading and default display are separate: a board must be loaded before it can run, while `display.enabledBoards` only selects it for the default sidebar profile.

### Blacklist

| Field | Type | Description |
|---|---|---|
| `blacklist.playerNamePatterns` | `string[]` | Case-insensitive Java regexes matched against complete player names |

A matching player disappears from visible objectives and automatic scoring freezes. Existing scores are archived rather than deleted. Removing the player from the blacklist restores those scores without adding activity collected during the blacklisted period. `addAutomatic`, `setAutomatic`, and `accumulate` enforce the blacklist internally; Groovy boards do not need a blacklist check. Administrator score commands can still change a blacklisted player's archived score.

---

## Player display and saved data

Players use one of three display modes:

| Mode | Behavior |
|---|---|
| `DEFAULT` | Follows the current server `display` settings |
| `CUSTOM` | Uses the player's own board, rotation, and interval choices |
| `HIDDEN` | Hides the sidebar while retaining the player's other choices |

Scores use player UUIDs, so changing a player name does not lose progress. Scoreboards, personal display settings, Groovy board state, and archived scores are saved with the world. Disabling or removing a board archives its score data; `/starryadmin prune` is the explicit permanent cleanup operation for IDs that no longer exist.

---

## Groovy boards

Trusted server owners can add boards without rebuilding the mod. Place one `*.groovy` file per board in:

```text
config/starrylist/boards/
```

If missing, StarryList creates `ore.groovy` as an editable example. Generated and imported scripts are still disabled by default.

Recommended workflow:

1. Run `./gradlew build` in the mod project and open `the-starry-list-<version>-script-sdk.zip`, or use a released SDK ZIP.
2. Edit `config/*.groovy` and `config/lang/*.json` in the SDK, then run `./gradlew compileGroovy`.
3. Copy the script to `config/starrylist/boards/` and its language files to `config/starrylist/lang/`.
4. Run `/starryadmin scripts validate`.
5. Add its board ID to `boards.enabledScriptBoards` or enable it through Cloth Config.
6. Run `/starryadmin reload`, then verify subscriptions with `/starryadmin scripts list`.

### Minimal example

```groovy
import com.flwolfy.starrylist.board.script.StarryListScriptBoard
import com.flwolfy.starrylist.board.script.StarryListScriptRegistrar
import com.flwolfy.starrylist.data.state.StarryListBoardState
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.OptionalInt

final class OreMiningBoard extends StarryListScriptBoard {
  String id() { "ore_mining" }
  String objectiveName() { "sl_ore" }
  int order() { 100 }
  ItemStack icon() { Items.RAW_IRON.defaultInstance }

  OptionalInt recalculate(ServerPlayer player, StarryListBoardState state) {
    long total = 0
    BuiltInRegistries.BLOCK.each { block ->
      if (block.defaultBlockState().is(ConventionalBlockTags.ORES)) {
        total += Math.max(0, player.stats.getValue(Stats.BLOCK_MINED.get(block)))
      }
    }
    OptionalInt.of((int) Math.min(Integer.MAX_VALUE, total))
  }

  Map translations() {
    translatableText(
      "starrylist.script.ore_mining.title",
      "starrylist.script.ore_mining.lore.0"
    )
  }

  void subscribe(StarryListScriptRegistrar registrar) {
    registrar.listen("block_break", PlayerBlockBreakEvents.AFTER) {
      level, player, position, state, blockEntity ->
      if (player instanceof ServerPlayer && state.is(ConventionalBlockTags.ORES)) {
        registrar.addAutomatic(player, 1)
      }
    }
  }
}
```

Each file must define exactly one concrete `StarryListScriptBoard`. IDs, objective names, and order values must be unique.

Score reconstruction is optional. Override
`OptionalInt recalculate(ServerPlayer player, StarryListBoardState state)` to make a board available
to `/starryadmin score recalculate`; return an absolute score and let StarryList perform the write.
Boards that keep the inherited `OptionalInt.empty()` implementation remain valid and are omitted
from that command's `boardId` suggestions. Targets are online players, including vanilla selectors
such as `@a`. Administrator recalculation also replaces a blacklisted player's archived score.

`translatableText()` reads flat string entries from `config/starrylist/lang/<locale>.json`. Every declared key must exist in `en_us.json`; a missing key in another locale falls back to English with a warning. Language files are discovered on preview, validation, and reload, and only affect Groovy board titles and lore. Existing literal maps made with `text()` remain supported.

### Registrar API

| API | Purpose |
|---|---|
| `listen(key, event, callback)` | Registers a managed void-returning Fabric event |
| `listen(key, event, inactiveResult, callback)` | Registers a managed value-returning Fabric event |
| `onActiveStateChanged(onActivated, onDeactivated)` | Creates and cleans up script-owned caches or secondary resources |
| `addAutomatic(player, delta)` | Adds a saturated score while automatically respecting the blacklist |
| `setAutomatic(player, value)` | Synchronizes an absolute score while automatically respecting the blacklist |
| `accumulate(player, key, amount, unitsPerWhole)` | Persists partial units and returns completed whole units |
| `state(player)` / `state(uuid)` | Accesses persistent per-board, per-player script state |
| `display(player)` / `isEnabled(player)` | Reads a player's effective sidebar settings |
| `runtime()` / `server()` | Accesses advanced StarryList or Minecraft server services |

Use `registrar.listen()` instead of `Event.register()` so reload and board enable/disable work correctly. A callback that throws is disabled until the next successful reload. Use `onActiveStateChanged` only for resources created outside managed listeners; ordinary subscriptions need no cleanup.

Groovy files are fully trusted server code, not sandboxed configuration. They can access public Minecraft, Fabric, Java, and installed-mod APIs. Only install scripts from sources you trust.

---

## Frequently asked questions

### Must players install the mod?

No. Vanilla clients can see the sidebar and use `/starry`.

### Why is a new Groovy board not scoring?

New scripts are disabled by default. Validate the file, add its ID to `boards.enabledScriptBoards`, run `/starryadmin reload`, and confirm its subscriptions are active with `/starryadmin scripts list`.

### Why did teleportation not increase travel distance?

The board reads vanilla continuous-movement statistics. Teleportation changes position directly and is not counted.

### What happens when reload fails?

The previous valid configuration and script catalog remain active. Check the server log or validation output, fix the error, and reload again.

---

## Compatibility & deployment

| Type                                | Support                                                                     |
|-------------------------------------|-----------------------------------------------------------------------------|
| Mod loader                          | Fabric Loader                                                               |
| Minecraft                           | 26.1+                                                                       |
| Dedicated server                    | ✅ install on the server only                                               |
| Single-player / LAN                 | ✅ install on the host client                                               |
| Player client on a dedicated server | Not required                                                                |
| Client configuration UI             | Optional with Cloth Config and ModMenu                                      |
| Languages                           | Core locales discovered from bundled resources, plus dynamic script locales |

---

## Credits

- Original project and author: [TheStarryMiningList by crackun24](https://github.com/crackun24/TheStarryMiningList)
- Fabric Loader and Fabric API
- SGui, Cloth Config, ModMenu, and Apache Groovy
