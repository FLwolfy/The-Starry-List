# The-Starry-List Mod Documentation

**The-Starry-List** is a leaderboard mod for Minecraft 26.1 Fabric. It stores six gameplay statistics in vanilla scoreboard objectives and gives every player independent control over enabled boards, visibility, and rotation timing.

简体中文文档见[这里](./README.cn.md)。

All gameplay features run on the server. Players on a dedicated server do not need The-Starry-List on their clients: an unmodified client can view the sidebar and use the `/starry` container menu. Client installation is only needed for the ModMenu / Cloth Config editor used by single-player and LAN integrated servers.

This project is a complete rewrite forked from [TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList).

---

## Features

- Six fixed built-in boards: mining, placing, mob kills, player kills, deaths, and travel distance.
- Scores stored by vanilla scoreboard objectives without separate player score files.
- Independent sidebar settings for every player.
- Board selection, hiding, and optional rotation in a fixed built-in order.
- Player-name regular-expression blacklisting for scoring and visibility.
- Server defaults with persistent per-player overrides.
- World-scoped scores and display preferences that survive server restarts.
- A localized, inventory-style `/starry` menu plus complete command-based administrative score and profile management.
- Server messages in `en_us` and `zh_cn`.
- Dedicated-server-only deployment; clients do not need the mod.
- Optional Cloth Config graphical editor.

The-Starry-List provides only the six boards documented below. Additional board types cannot be registered.

---

## Compatibility and dependencies

| Component | Version or requirement | Purpose |
|---|---|---|
| Minecraft | `26.1` | Supported game version |
| Java | `25` or newer | Running the server and building the project |
| Fabric Loader | `0.19.3` or compatible | Required |
| Fabric API | `0.145.1+26.1` or compatible | Required |
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

Board IDs are used by configuration and commands. Objective names are the internal names stored in the vanilla scoreboard. All six IDs and objective names are fixed.

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

- All six boards use vanilla objectives with the `dummy` criterion.
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
  "blacklist": {
    "playerNamePatterns": []
  }
}
```

The mod writes these defaults when the file does not exist. Ordinary missing fields are filled with defaults and saved. At startup, an unparseable or invalid configuration is backed up before defaults are restored; a failed manual reload keeps the previously valid active configuration.

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
| `enabledBoards` | `string[]` | `["mining", "placing", "mob_kills"]` | Enabled default boards; only the six fixed IDs are accepted, with no duplicates; an empty list is allowed |

Valid `enabledBoards` values are:

```text
mining
placing
mob_kills
player_kills
deaths
travel_distance
```

Array order does not customize display order. Enabled entries always use the built-in order shown above; disabling `rotationEnabled` pins the first enabled board. An empty list is valid and means that no StarryList board is currently available for display. Legacy `defaultBoards` is migrated automatically when loaded, and a legacy empty list remains empty.

### Blacklist (`blacklist`)

| Field | Type | Default | Description |
|---|---|---|---|
| `playerNamePatterns` | `string[]` | `[]` | Case-insensitive Java regular expressions matched against the complete player name |

A matching player stops accumulating all six automatic statistics and is removed from visible objectives. Existing scores are archived per world rather than deleted and return when the player no longer matches. Invalid or blank expressions fail configuration validation. For example, `bot_.*` matches names beginning with `bot_`.

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

`/starry` requires no administrator permission and directly opens one fixed, non-paginated six-board inventory menu. It has no arguments or subcommands; console and command-block sources receive a player-only error.

The menu has five rows. Gray glass fills the top and bottom rows; light-gray glass provides the remaining separators and borders. Six adjacent board cards appear in fixed order. Enabled cards glow and disabled cards do not; the former skull icons use an iron sword, diamond sword, and totem so the glint remains visible. All six boards may be disabled; this leaves no StarryList sidebar to display while retaining the board toggles, rotation setting, and interval in the profile. Bottom controls restore defaults, independently hide or show the sidebar, toggle rotation, edit the `1`–`3600` second interval through an anvil screen with a Cancel button, and close the menu. Successful changes send a crisp experience-orb sound directly to the player, save immediately, and refresh the sidebar.

The menu is implemented with the SGui library bundled in the mod JAR. It uses vanilla container packets, so players do not install SGui or The-Starry-List locally. Menu text follows the player's reported `en_us` or `zh_cn` language and falls back to the configured server language and then English.

---

## Administrator commands

`/starryadmin` requires vanilla permission level `2` by default. The server console is always allowed. `<targets>` uses vanilla online-player selection, including player names and selectors such as `@a` and `@p`.

Administrative operations remain command-only: `/starryadmin` does not open or provide a container GUI.

| Command | Function |
|---|---|
| `/starryadmin` | Shows the administrative command categories |
| `/starryadmin reload` | Reads, validates, and applies the configuration again |
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
- **Display**: default visibility, rotation, interval, plus one collapsible six-board subcategory. Every board has its own toggle and reset button; the first three are enabled by default.
- **Blacklist**: an editable player-name regex list with inline highlighting; newly added input fields have a border and example placeholder.

Saving uses the same complete validation as the JSON configuration. Success or failure appears as a toast, and detailed exceptions are written to the log.

The screen cannot modify a remote server over the network. Even when a remote server also runs The-Starry-List, the client ModMenu page still edits only that client's game directory.

---

## Frequently asked questions

### Must players install the mod?

No. The server handles leaderboards, sidebar packets, scores, and commands, so an unmodified client can use everything.

### Can I add a seventh board?

No. This version manages only six fixed boards. Servers and players can choose a subset and rotation behavior, but cannot change the built-in order.

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
./gradlew clean build -x test
```

Windows:

```powershell
gradlew.bat clean build -x test
```

Artifacts are written to `build/libs/`. The regular JAR is the installable mod; the JAR with a `-sources` suffix contains source code.

---

## Credits

- Original project: [TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList)
- Fabric Loader and Fabric API
- SGui, Cloth Config, and ModMenu

## License

The-Starry-List is licensed under the [GNU Lesser General Public License v3.0](./LICENSE).
