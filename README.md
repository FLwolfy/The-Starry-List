# The-Starry-List Mod Documentation

**The-Starry-List** is an extensible leaderboard mod for Minecraft 26.1 Fabric. It stores gameplay statistics in vanilla scoreboard objectives and gives every player independent control over enabled boards, visibility, and rotation timing.

简体中文文档见[这里](./README.cn.md)。

All gameplay features run on the server. Players on a dedicated server do not need The-Starry-List on their clients: an unmodified client can view the sidebar and use the `/starry` container menu. Client installation is only needed for the ModMenu / Cloth Config editor used by single-player and LAN integrated servers.

This project is a complete rewrite forked from [TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList).

---

## Features

- Automatically discovered board modules, with mining, placing, mob kills, player kills, deaths, and travel distance built in.
- Scores stored by vanilla scoreboard objectives without separate player score files.
- Independent sidebar settings for every player.
- Board selection, hiding, and optional rotation in each module's stable declared order.
- Player-name regular-expression blacklisting for scoring and visibility.
- Server defaults with persistent per-player overrides.
- World-scoped scores and display preferences that survive server restarts.
- A localized, inventory-style `/starry` menu plus complete command-based administrative score and profile management.
- Server messages in `en_us` and `zh_cn`.
- Dedicated-server-only deployment; clients do not need the mod.
- Optional Cloth Config graphical editor.

Adding a `StarryListBoard` subclass below this project's `com.flwolfy.starrylist.board` module tree registers it automatically on the next start; no central ID or registration list is edited.

---

## Compatibility and dependencies

| Component | Version or requirement | Purpose |
|---|---|---|
| Minecraft | `26.1` | Supported game version |
| Java | `25` or newer | Running the server and building the project |
| Fabric Loader | `0.19.3` or compatible | Required |
| Fabric API | `0.145.1+26.1` or compatible | Required |
| Groovy | `5.0.7` | Bundled trusted server-script runtime |
| SGui | `2.0.0+26.1` | Bundled inside The-Starry-List; do not install separately |
| Cloth Config | `26.1.154` | Optional local configuration screen |
| ModMenu | `18.0.0` | Optional local configuration screen |

The mod has no hard dependency on another leaderboard, economy, or permissions mod. Administrator access uses Minecraft's vanilla permission levels from `0` through `4`.

---

## Installation and deployment

### Dedicated server

1. Install Fabric Loader for Minecraft 26.1.
2. Place Fabric API and the The-Starry-List JAR in the server's `mods` directory.
3. Start the server. The first startup creates `config/starrylist.json`.
4. Edit the configuration if needed, then run `/starryadmin reload` or restart the server.

A dedicated server does not need Cloth Config or ModMenu. Regular players do not need to install this mod on their clients.

### Single-player or LAN world

Install all of the following to use the in-client graphical editor:

- The-Starry-List
- Fabric API
- Cloth Config
- ModMenu

Open The-Starry-List from ModMenu. The screen edits the current game directory's local configuration and therefore affects only a single-player or LAN integrated server started by that client.

### Remote server configuration

The client configuration screen cannot edit a remote dedicated server. A remote server administrator must edit the server's `config/starrylist.json` and apply it with the administrator reload command.

---

## The six built-in boards

| Board ID | Objective | Default English name | Statistic |
|---|---|---|---|
| `mining` | `sl_mining` | Mining | Blocks successfully broken by a player |
| `placing` | `sl_placing` | Placing | Successful player block-placement actions |
| `mob_kills` | `sl_mob_kills` | Mob Kills | Non-player living entities killed by a player |
| `player_kills` | `sl_player_kills` | War God | Players killed by another player |
| `deaths` | `sl_deaths` | Deaths | Player deaths from any cause |
| `travel_distance` | `sl_travel` | Travel Distance | Whole blocks accumulated from vanilla continuous-movement statistics |

Board IDs are used by configuration and commands. Objective names are the internal names stored in the vanilla scoreboard. Each discovered module contributes its own validated identifiers.

### Mining

`mining` increases by `1` after `PlayerBlockBreakEvents.AFTER` confirms a successful player block break.

- Tool choice, empty-hand mining, and game mode do not change the counting rule.
- Only block breaks successfully completed by a player count.
- Explosions, pistons, fluids, natural changes, and other non-player block changes do not count.
- A break cancelled by another mod or a server rule does not reach the successful callback and therefore does not count.

### Placing

`placing` increases by `1` after `BlockItem.place` returns a successful result.

- One successful use action adds exactly one point.
- Beds, doors, and double-height plants can create multiple block states but still count as one placement action.
- Failed placements, client prediction, and results that do not consume the action do not count.
- This board counts `BlockItem` placement, not commands, structure generation, pistons, or natural world changes.

### Mob kills and player kills

Fabric's server combat event confirms kills, and Minecraft's credited damage source determines the player receiving the point.

- A player victim increases only `player_kills`.
- A non-player victim increases only `mob_kills`.
- The boards are mutually exclusive; one kill never enters both.
- Melee attacks, projectiles, and other damage that Minecraft attributes to a player can count.
- Environmental or uncredited kills do not enter a player's kill board.

### Deaths

`deaths` increases by `1` after a player death is completed. Environmental damage, mobs, PvP, commands, and any other source producing a real player death use the same rule.

### Travel distance

`travel_distance` consumes vanilla continuous-movement centimeter statistics reported by `ServerPlayer.awardStat`; it does not estimate distance from coordinate differences.

The currently recognized vanilla movement statistics include:

- Walking, crouching, and sprinting.
- Walking on water, walking underwater, swimming, climbing, and falling.
- Flying and elytra flight.
- Minecart, boat, pig, horse, strider, happy ghast, and nautilus travel.

Each player stores a remainder from `0` to `99` centimeters. Every accumulated `100` centimeters adds `1` block to the board. For example, a `50 cm` increment followed by `75 cm` adds `1` block and retains `25 cm` for the next increment.

Teleportation does not create these continuous-movement statistics, so `/tp`, ender pearls, portals, dimension changes, respawning, and APIs that directly move an entity do not count. Normal high-speed elytra and vehicle movement still count.

---

## Scoreboard and saved data

- Every registered board uses a vanilla objective with the `dummy` criterion.
- The internal score owner is the player's UUID string, preventing a rename from creating a second score.
- When a player joins, existing entries receive the player's current game name as their display component.
- Vanilla `scoreboard.dat` stores scores, including negative values assigned by administrators.
- The current world's SavedData stores display profiles, travel-centimeter remainders, and blacklisted-score archives.
- Different worlds have independent scores, personal display settings, and travel remainders.
- A vanilla sidebar displays at most the 15 highest score entries.
- The mod does not permanently occupy one global sidebar objective. It sends the selected objective separately to each player, allowing different players to view different boards at the same time.

---

## Configuration

### File location

```text
config/starrylist.json
```

The path is relative to the Minecraft game directory or dedicated-server root.

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
    "disabledBoards": []
  },
  "blacklist": {
    "playerNamePatterns": []
  }
}
```

The mod writes these defaults when the file does not exist. On startup and manual reload, it merges the file field by field: readable valid values are retained, invalid values and unknown fields are discarded, missing values use defaults, and the complete canonical JSON replaces the original file. A syntactically unreadable root is replaced with defaults; no `invalid-*` backup is generated.

### General settings (`general`)

| Field | Type | Default | Description |
|---|---|---|---|
| `language` | `string` | `en_us` | Language for server command feedback and board names; supports `en_us` and `zh_cn` |
| `adminPermissionLevel` | `int` | `2` | Vanilla permission level required for `/starryadmin`, from `0` through `4` |

The server selects one global message language. It sends already-rendered literal text, so clients without the mod still receive complete messages.

### Default display settings (`display`)

| Field | Type | Default | Description |
|---|---|---|---|
| `hiddenByDefault` | `boolean` | `false` | Whether players without an override hide the sidebar by default |
| `rotationEnabled` | `boolean` | `true` | Whether the default profile rotates through multiple boards |
| `rotationIntervalSeconds` | `int` | `20` | Default rotation interval from `1` through `3600` seconds |
| `enabledBoards` | `string[]` | `["mining", "placing", "mob_kills"]` | Enabled registered board IDs, with no duplicates; an empty list is allowed |

Built-in `enabledBoards` values are:

```text
mining
placing
mob_kills
player_kills
deaths
travel_distance
```

Array order does not customize display order. Enabled entries use the order declared by their board modules; disabling `rotationEnabled` pins the first enabled board. An empty list is valid and means that no StarryList board is currently available for display.

### Board loading settings (`boards`)

| Field | Type | Default | Description |
|---|---|---|---|
| `disabledBoards` | `string[]` | `[]` | Discovered boards not loaded into gameplay; duplicates and unknown IDs are invalid |

Loading and default-profile selection are independent. A loaded board owns its objective, collects events, and appears in configuration and SGUI controls. An unloaded board archives and removes its objective, disables its managed event callbacks, and disappears from runtime UI, while its DEFAULT and player profile selections are retained for restoration. Cloth Config exposes **On/Off**, **Default on/off**, and **Reset** separately. Saving the screen only writes the JSON file; `/starryadmin reload` applies both settings in the active world without restarting.

### Blacklist (`blacklist`)

| Field | Type | Default | Description |
|---|---|---|---|
| `playerNamePatterns` | `string[]` | `[]` | Case-insensitive Java regular expressions matched against the complete player name |

A matching player stops accumulating all registered automatic statistics and is removed from visible objectives. Existing scores are archived per world rather than deleted and return when the player no longer matches. Invalid or blank expressions fail configuration validation. For example, `bot_.*` matches names beginning with `bot_`.

After editing the file, run:

```text
/starryadmin reload
```

A successful reload immediately updates server language, objective display names, and every online player's sidebar. Invalid configuration is never partially applied.

---

## Player display model

Every player profile has one of three modes:

| Mode | Behavior |
|---|---|
| `DEFAULT` | Follows the server `display` configuration live; this is the implicit mode with no saved profile |
| `CUSTOM` | Uses the player's enabled boards, rotation setting, and interval |
| `HIDDEN` | Displays no StarryList sidebar while retaining all pre-hide settings |

Choosing **Use server defaults** in `/starry` removes the player's override instead of copying current defaults. If an administrator later changes server defaults, those players automatically follow the new settings.

Changing a board, rotation, or interval stores a personal profile based on the effective settings. Editing while hidden does not automatically show the sidebar; showing it again restores those settings. Personal settings affect only that player. Joining or changing settings starts at the first enabled board; rotation begins only after the configured interval elapses.

---

## Player menu

`/starry` requires no administrator permission and directly opens a fixed five-row board inventory menu. It has no arguments or subcommands; console and command-block sources receive a player-only error.

The menu has five rows. Gray glass fills the top and bottom rows; light-gray glass provides the remaining separators and borders. Up to seven adjacent board cards appear per page in registry order. The current six-board layout is unchanged; when more than seven boards exist, cyclic previous/next arrows occupy the two side-border slots. Enabled cards glow and disabled cards do not. All boards may be disabled; this leaves no StarryList sidebar to display while retaining the board toggles, rotation setting, and interval in the profile. Bottom controls restore defaults, independently hide or show the sidebar, toggle rotation, edit the `1`–`3600` second interval through an anvil screen with a Cancel button, and close the menu. Successful changes send a crisp experience-orb sound directly to the player, save immediately, and refresh the sidebar.

The menu is implemented with the SGui library bundled in the mod JAR. It uses vanilla container packets, so players do not install SGui or The-Starry-List locally. Menu and scoreboard text always use the server language selected in `general.language`.

---

## Administrator commands

`/starryadmin` requires vanilla permission level `2` by default. The server console is always allowed. `<targets>` uses vanilla online-player selection, including player names and selectors such as `@a` and `@p`.

Administrative operations remain command-only: `/starryadmin` does not open or provide a container GUI.

| Command | Function |
|---|---|
| `/starryadmin` | Shows the administrative command categories |
| `/starryadmin reload` | Reloads valid Groovy boards and the JSON configuration; invalid scripts are skipped and reported |
| `/starryadmin prune` | Permanently removes SavedData belonging to board IDs that no longer exist |
| `/starryadmin scripts validate` | Compiles and validates every enabled script without applying it |
| `/starryadmin scripts reload` | Reloads only Groovy boards and keeps the active JSON settings |
| `/starryadmin scripts list` | Lists active script sources, IDs, objectives, and subscription health |
| `/starryadmin score get <boardId> <player>` | Shows an online player's score and internal UUID owner |
| `/starryadmin score set <boardId> <targets> <value>` | Replaces target players' scores with an integer |
| `/starryadmin score add <boardId> <targets> <value>` | Adds a signed integer to target players' scores |
| `/starryadmin score reset <boardId> <targets>` | Removes target players' entries from the objective |
| `/starryadmin score reset-all <boardId>` | Removes every entry, including offline-player entries, from the board |
| `/starryadmin profile get <player>` | Shows an online player's saved profile |
| `/starryadmin profile reset <targets>` | Restores target online players to `DEFAULT` |
| `/starryadmin profile reset-all` | Removes every personal display override in the world and refreshes online players |

Examples:

```text
/starryadmin score get mining Steve
/starryadmin score add deaths @a 1
/starryadmin score set travel_distance Steve 5000
/starryadmin score reset player_kills Steve
/starryadmin profile reset @a
```

Score operations use signed 32-bit integers. If an addition would overflow that range, it fails instead of storing a wrapped value.
Administrator score commands read and modify a blacklisted player's hidden archive. Those values return to the objective only after the player no longer matches the blacklist.

---

## Cloth Config screen

With Cloth Config and ModMenu installed on the client, the configuration screen contains:

- **All**: the first category, containing expanded, synchronized copies of every real setting below.
- **General**: server message language and administrator permission level.
- **Display**: default visibility, rotation, and interval.
- **Boards**: separate expanded Built-in and Custom script board groups. Every board has Loaded/Disabled, Default Shown/Hidden, and Reset controls. Custom also has a Refresh button that updates script previews in place without applying them to the running server.
- **Blacklist**: an editable player-name regex list with inline highlighting; newly added input fields have a border and example placeholder.

Saving uses the same complete validation as the JSON configuration, but does not change the running server. Success or failure appears as a toast, and detailed exceptions are written to the log. Run `/starryadmin reload` to apply the saved file.

The screen cannot modify a remote server over the network. Even when a remote server also runs The-Starry-List, the client ModMenu page still edits only that client's game directory.

The screen layout is generated recursively from `StarryListConfigData` records. Supported scalar fields automatically receive labels and tooltips using `starrylist.config.<full.path>` keys, while existing fields keep their current text through path metadata. New scalar types can be rendered by registering a `StarryListConfigEntryBuilder`; field paths can override a type builder, and a top-level record can register a `StarryListConfigSectionBuilder` when several fields must be consumed together. All and dedicated categories always receive separate widget instances bound to one `StarryListConfigEditorModel`, which keeps focus, IME composition, dynamic rows, validation, and reset state isolated while synchronizing their values.

The ModMenu implementation is organized by responsibility: `screen` owns lifecycle and layout, `builder` owns extension contracts and dispatch, `model` owns record mapping and editable state, `section` owns multi-field sections, and `entry` is split into `common`, `scalar`, `board`, and `blacklist` widgets.

---

## Hot-reloadable Groovy boards

Trusted server owners can add boards without rebuilding the mod. Script candidates are the `*.groovy` files in `config/starrylist/boards/`. When `ore.groovy` is absent, the mod creates it as an enabled default board that counts every block in Fabric's conventional `ORES` tag, including compatible modded ores. No disabled example file is generated. Edit scripts, run `/starryadmin scripts validate`, and then run `/starryadmin scripts reload`. Files are not watched automatically.

Each file must contain exactly one public concrete `StarryListScriptBoard` subclass. It supplies `id()`, `objectiveName()`, unique `order()`, `icon()`, `translations()`, and `subscribe(registrar)`. `translations()` must contain `en_us`; locale keys use the `ll_cc` form and missing locales fall back to English. All locales for a board must contain the same number of lore lines. Metadata is validated against built-in and other script boards.

Fabric callbacks must use the reload-managed registrar:

```groovy
registrar.listen("stable_key", SomeFabricEvent.EVENT) { arguments ->
  // callback
}

registrar.listen("stable_key", SomeReturningEvent.EVENT, fallbackValue) { arguments ->
  // return a compatible result
}
```

Direct Fabric `Event.register()` calls are rejected during Groovy compilation. A stable `boardId/key` creates one permanent Java proxy; reload only replaces its Closure delegate, so repeated reloads do not duplicate callbacks. A callback exception disables only that subscription and returns its inactive result until the next successful reload. Changing the Fabric event or inactive result requires a new key.

The first `listen` argument is a stable subscription ID local to that board, not a Fabric event name. For example, `block_break` becomes `ore_mining/block_break` for a board whose ID is `ore_mining`. Keys must be unique within one board and remain unchanged across reloads while they represent the same Event and inactive result.

The registrar also exposes `addAutomatic`, `isBlacklisted`, `display`, `isEnabled`, per-player `state`, `accumulate`, `runtime`, and `server`. Automatic scoring uses the same blacklist and saturated-integer behavior as built-in boards. Board-private state and blacklist archives remain keyed by board ID.

Cloth Config's **Refresh** button sits on the right side of the Custom script boards header. It only recompiles files for the editor and replaces that subcategory's entries in place; it does not change the running catalog or recreate the screen. Invalid rows receive red text and a strike line, their controls are disabled, and they do not block saving unrelated configuration fields. A server reload compiles scripts independently with fresh classloaders, activates every valid script, skips invalid files, and reports each skipped filename and diagnostic in red chat. Removing or skipping a script cleans its ID from defaults and player profiles, archives and removes its objective, and retains board state and blacklist archives. Reintroducing the same ID restores archived scores even if the objective name changed. Open SGUI menus refresh immediately.

Persistent StarryList state is stored in `<world>/data/the-starry-list/state.dat`. `/starryadmin prune` permanently removes script-private state, inactive-objective archives, and blacklisted score entries only for board IDs absent from the current discovered catalog. Disabled boards are still discovered and are never pruned.

Groovy scripts are fully trusted server code. They may call public Minecraft, Fabric, Java, and installed-mod APIs, including files, networking, threads, and reflection. There is no sandbox or time limit. Threads, static state, reflective registration, and side effects created outside `registrar.listen()` cannot be undone by reload. Scripts cannot add Mixins or new bytecode injection points while the game is running.

---

## Adding a board module

Create a public concrete subclass of `StarryListBoard` anywhere below `com.flwolfy.starrylist.board`. It must have an implicit or explicit public no-argument constructor. The class supplies its stable `id()`, language-independent `objectiveName()`, unique `order()`, SGUI `icon()`, and statistic registration in `register(...)`; the base class resolves its localized presentation.

The bound `StarryListBoardRegistrar` provides automatic scoring with blacklist and overflow handling, delayed runtime access, and a persistent per-player board-state namespace. Board code therefore does not need to modify the config manager, score service, scoreboard manager, sidebar, commands, Cloth Config, or SGUI. The registry recursively discovers classes once during Mod initialization, validates metadata and duplicates, and aborts startup with the offending class or value if loading fails.

Register Fabric events from `register(...)` through `registrar.listen("stable_key", EVENT, callback)` rather than calling `EVENT.register(...)` directly. Fabric events have no callback-removal API: `listen` installs one permanent proxy and hot-enables or suppresses it with the board's `boards.disabledBoards` state, preventing duplicate or still-running callbacks across configuration reloads. Value-returning events use `registrar.listen("stable_key", EVENT, inactiveResult, callback)`. A board that owns caches or secondary vanilla listeners can also use `onActiveStateChanged(...)` to rebuild them when enabled and release them when disabled. The built-in placing board listens for successful vanilla `BLOCK_PLACE` game events, while travel samples deltas from the player's vanilla movement statistics at the end of each server tick. The project therefore has no Mixin classes or shared Mixin JSON to maintain.

The conventional localization keys are `starrylist.board.<id>.title` and `starrylist.board.<id>.description`. Add their text to each bundled language JSON; the base class resolves them through `StarryListLangManager`, including player-locale, configured-language, English, and key fallbacks. A module needing multiple lore lines can override `loreTranslationKeys()` while keeping all user-facing content in language resources.

New boards are loaded unless their IDs are added to `boards.disabledBoards`, but they remain absent from the DEFAULT profile until added to `display.enabledBoards`. A newly generated config intentionally selects only `mining`, `placing`, and `mob_kills` for that profile. Rebuild and restart to discover Java classes; `/starryadmin reload` never rescans Java modules or creates another static registry.

---

## Frequently asked questions

### Must players install the mod?

No. The server handles leaderboards, sidebar packets, scores, and commands, so an unmodified client can use everything.

### Can I add a seventh board?

Yes. Add a public, concrete `StarryListBoard` subclass with an implicit or explicit public no-argument constructor below `com.flwolfy.starrylist.board`, then add its title and description keys to the language JSON files. The registry, config GUI, command suggestions, sidebar, and SGUI require no edits. Rebuild and restart after adding Java classes; configuration reload does not hot-load code.

### Why does teleportation not increase travel distance?

The travel board reads vanilla continuous-movement statistics. Teleportation directly changes position and is not walking, flight, or vehicle travel.

### Why do two players see different boards?

Sidebar objectives are sent per player. Each player may have an independent `CUSTOM` profile or follow server defaults.

### Does a player lose scores after changing names?

No. The scoreboard owner is the UUID string; joining only refreshes the visible player name.

### How do I hide the sidebar completely?

Open `/starry` and choose **Hide sidebar**. To hide it for new/default players, set `display.hiddenByDefault` to `true`.

### What happens when configuration reload fails?

The current valid configuration continues running and no partial fields are applied. Check the log for invalid field paths or parse errors, fix the file, and reload again.

---

## Building from source

Java 25 is required. The repository includes the Gradle Wrapper:

```bash
./gradlew clean build
```

Windows:

```powershell
gradlew.bat clean build
```

Artifacts are written to `build/libs/`. The regular JAR is the installable mod; the JAR with a `-sources` suffix contains source code.

---

## Credits

- Original project: [TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList)
- Fabric Loader and Fabric API
- SGui, Cloth Config, and ModMenu

## License

The-Starry-List is licensed under the [GNU Lesser General Public License v3.0](./LICENSE).
