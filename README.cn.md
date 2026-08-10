# The-Starry-List

[English](README.md)

The-Starry-List 是面向 Minecraft 26.1 Fabric 的纯服务端排行榜系统。它用原版 scoreboard objective 保存统计数据，允许每名玩家独立选择和轮转侧边栏，并支持任意数量的受信任 JEXL 自定义榜单。本项目完全重写并 fork 自 [TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList)。

## 内置榜单

- `mining`：成功破坏方块的次数。
- `placing`：成功执行方块放置的次数。
- `mob_kills`：玩家击杀非玩家生物的数量。
- `player_kills`：玩家击杀其他玩家的数量。
- `deaths`：玩家因任意原因死亡的次数。
- `travel_distance`：原版确认的连续移动距离，按格累计且不统计传送。

## 安装与配置

独立服务器只需安装 Fabric Loader、Fabric API 和本模组；客户端无需安装 StarryList，原版客户端也能加入并使用排行榜及指令。

服务端配置位于 `config/starrylist.json`。客户端可选安装 StarryList、Cloth Config 和 ModMenu，用本地界面编辑单人游戏或 LAN 集成服务器配置；该界面无法修改远程独立服务器。

自定义榜单通过有序的 `ADD` 或 `SET` JEXL source 订阅游戏事件。`scheduled` source 只处理在线玩家并固定使用 `SET`。脚本可访问类型化的 `player`、`context`、`previousScore`、`board`、`now`，以及 `math`、`minecraft`、`shell` namespace。

> **安全警告：**能修改 `starrylist.json` 的人等同拥有 Minecraft 控制台与服务器操作系统账号权限。不得接受不可信脚本。事件脚本在服务器线程同步执行，慢速 shell 会直接造成服务器卡顿；外部数据应优先使用 `scheduled` source。

## 指令

玩家使用 `/starry`、`/starry boards [page]` 和 `/starry display ...` 查看榜单、跟随服务器默认值、隐藏侧边栏、选择或调整榜单顺序，以及设置个人轮转。

管理员使用 `/starryadmin reload`、`/starryadmin score ...`、`/starryadmin profile ...`，以及两阶段的 `/starryadmin prune [confirm]` 清理流程。

## 许可证

The-Starry-List 使用 [LGPL-3.0](LICENSE) 许可证。
