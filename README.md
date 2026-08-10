# The-Starry-List

[简体中文](README.cn.md)

The-Starry-List is a Minecraft 26.1 Fabric server-side leaderboard system. It tracks gameplay with vanilla scoreboard objectives, lets each player choose an independent sidebar rotation, and supports unlimited trusted JEXL boards. This project is a complete rewrite forked from [TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList).

## Built-in boards

- `mining`: successfully broken blocks.
- `placing`: successful block placement actions.
- `mob_kills`: non-player living entities killed by a player.
- `player_kills`: players killed by a player.
- `deaths`: player deaths from every cause.
- `travel_distance`: vanilla-confirmed continuous travel, accumulated in blocks without counting teleportation.

## Installation and configuration

Install Fabric Loader, Fabric API, and this mod on the dedicated server. Clients do not need StarryList and can join with an unmodified vanilla client.

The server configuration is `config/starrylist.json`. Installing StarryList, Cloth Config, and ModMenu on a client adds a local editor for single-player and LAN integrated servers; it cannot edit a remote dedicated server.

Custom boards subscribe to gameplay events with ordered `ADD` or `SET` JEXL sources. `scheduled` sources run for online players and use `SET`. Scripts receive typed `player`, `context`, `previousScore`, `board`, and `now` values plus `math`, `minecraft`, and `shell` namespaces.

> **Security:** Anyone who can edit `starrylist.json` has effective Minecraft console and server operating-system account authority. Never accept untrusted scripts. Event scripts run synchronously and a slow shell command will freeze the server; use `scheduled` sources for external data whenever possible.

## Commands

Players use `/starry`, `/starry boards [page]`, and `/starry display ...` to inspect boards, follow server defaults, hide the sidebar, select/reorder boards, and configure personal rotation.

Administrators use `/starryadmin reload`, `/starryadmin score ...`, `/starryadmin profile ...`, and the two-step `/starryadmin prune [confirm]` workflow.

## License

The-Starry-List is licensed under [LGPL-3.0](LICENSE).
