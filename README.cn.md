# The-Starry-List 模组使用文档

**The-Starry-List** 是面向 Minecraft 26.1 Fabric 的排行榜模组。它通过原版 scoreboard objective 保存六类游戏统计，并为每名玩家独立控制侧边栏的榜单开关、显示状态与轮转间隔。

See the English document [here](./README.md).

本模组的所有游戏功能都在服务端完成。独立服务器上的玩家无需安装 The-Starry-List，也可以使用原版客户端查看排行榜并使用 `/starry` 容器菜单。客户端安装仅用于给单人游戏或 LAN 集成服务器提供 ModMenu / Cloth Config 配置界面。

本项目完全重写并 fork 自 [TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList)。

---

## 功能与特性

- 六个固定内置榜单：挖掘、放置、怪物击杀、玩家击杀、死亡和移动距离。
- 使用原版 scoreboard 保存分数，不额外创建玩家分数文件。
- 每名玩家拥有相互独立的侧边栏设置。
- 支持选择榜单、隐藏侧边栏以及开启或关闭轮转；榜单始终使用固定内置顺序。
- 支持使用玩家名正则表达式配置计分与显示黑名单。
- 支持服务器默认显示配置和玩家个人覆盖配置。
- 分数和显示偏好按世界保存，服务器重启后继续保留。
- 提供本地化的 `/starry` 物品栏菜单，以及完整的管理员分数/profile 管理指令。
- 服务端消息支持 `en_us` 和 `zh_cn`。
- 可作为纯服务端模组部署，客户端无需安装。
- 可选的 Cloth Config 图形化配置界面。

The-Starry-List 只提供下文列出的六个榜单，不支持新增其他榜单类型。

---

## 兼容性与依赖

| 组件 | 版本或要求 | 用途 |
|---|---|---|
| Minecraft | `26.1` | 当前支持的游戏版本 |
| Java | `25` 或更高 | 运行服务器和构建项目 |
| Fabric Loader | `0.19.3` 或兼容版本 | 必需 |
| Fabric API | `0.145.1+26.1` 或兼容版本 | 必需 |
| SGui | `2.0.0+26.1` | 已内嵌在 The-Starry-List 中，无需另行安装 |
| Cloth Config | `26.1.154` | 仅本地配置界面可选 |
| ModMenu | `18.0.0` | 仅本地配置界面可选 |

模组未声明对其他排行榜、经济或权限模组的硬依赖。管理员权限使用 Minecraft 原版权限等级 `0`～`4`。

---

## 安装与部署

### 独立服务器

1. 安装适用于 Minecraft 26.1 的 Fabric Loader。
2. 将 Fabric API 和 The-Starry-List JAR 放入服务器的 `mods` 文件夹。
3. 启动服务器。首次启动会生成 `config/starrylist.json`。
4. 根据需要修改配置，然后执行 `/starryadmin reload` 或重启服务器。

独立服务器不需要安装 Cloth Config 或 ModMenu。普通玩家也不需要在客户端安装本模组。

### 单人游戏或 LAN 世界

要在客户端内使用图形化配置界面，请同时安装：

- The-Starry-List
- Fabric API
- Cloth Config
- ModMenu

然后在 ModMenu 中打开 The-Starry-List 的配置页面。该页面编辑当前游戏目录的本地配置，只会影响该客户端启动的单人游戏或 LAN 集成服务器。

### 远程服务器配置

客户端上的配置页面不能编辑远程独立服务器。远程服务器管理员必须直接编辑服务器端的 `config/starrylist.json`，并使用管理员重载指令应用修改。

---

## 六个内置榜单

| 榜单 ID | Objective | 默认显示名 | 统计内容 |
|---|---|---|---|
| `mining` | `sl_mining` | 挖掘榜 | 玩家成功破坏方块的次数 |
| `placing` | `sl_placing` | 放置榜 | 玩家成功执行方块放置动作的次数 |
| `mob_kills` | `sl_mob_kills` | 击杀榜 | 玩家击杀非玩家生物的数量 |
| `player_kills` | `sl_player_kills` | 战神榜 | 玩家击杀其他玩家的数量 |
| `deaths` | `sl_deaths` | 死亡榜 | 玩家死亡的次数 |
| `travel_distance` | `sl_travel` | 移动距离榜 | 原版连续移动统计累计的整格距离 |

榜单 ID 用于配置和指令，Objective 是保存在原版 scoreboard 中的内部名称。六个 ID 和 Objective 名称固定不可修改。

### 挖掘榜

`mining` 在 `PlayerBlockBreakEvents.AFTER` 确认一次玩家方块破坏成功后增加 `1`。

- 工具、空手和游戏模式不会改变计数规则。
- 只有玩家成功完成的方块破坏会计数。
- 爆炸、活塞、流体、自然变化或其他非玩家方块变化不会计数。
- 被其他模组或服务端规则取消的破坏不会到达成功回调，因此不会计数。

### 放置榜

`placing` 在 `BlockItem.place` 返回成功结果后增加 `1`。

- 一次成功的使用动作只增加一次。
- 床、门和双层植物等可能产生多个方块状态，但仍按一次放置动作计算。
- 放置失败、客户端预测或没有消费操作结果的行为不计数。
- 该榜统计 `BlockItem` 放置，不统计命令、结构生成、活塞或世界自然变化。

### 怪物击杀榜与玩家击杀榜

击杀由 Fabric 的服务端战斗事件确认，并按照 Minecraft 认定的攻击来源归属给玩家。

- 受害者是玩家时，只增加 `player_kills`。
- 受害者不是玩家时，只增加 `mob_kills`。
- 两个榜单互斥，同一次击杀不会同时进入两个榜单。
- 玩家近战、投射物和其他能够由 Minecraft 追溯到玩家的伤害均可计入。
- 环境或没有玩家归属的击杀不会计入玩家击杀榜。

### 死亡榜

`deaths` 在玩家死亡事件完成后增加 `1`。环境伤害、怪物、PvP、命令和其他能够造成真实玩家死亡的来源都使用同一规则。

### 移动距离榜

`travel_distance` 使用 `ServerPlayer.awardStat` 中由原版确认的连续移动厘米统计，不通过两次坐标之间的差值推测距离。

当前计入的原版移动统计包括：

- 步行、潜行和疾跑。
- 水面行走、水下行走、游泳、攀爬和下落。
- 飞行和鞘翅飞行。
- 矿车、船、猪、马、炽足兽、快乐恶魂和鹦鹉螺等载具移动。

每名玩家会保存 `0`～`99` 厘米的余量。累计达到 `100` 厘米时，移动距离榜增加 `1` 格。例如先移动 `50 cm`，再移动 `75 cm`，会增加 `1` 格并保留 `25 cm` 供下一次累计。

传送不会产生这些连续移动统计，因此 `/tp`、末影珍珠、传送门、跨维度、重生和其他直接修改位置的行为不会被当作移动距离。正常高速鞘翅和载具移动仍会计入。

---

## Scoreboard 与数据保存

- 六个榜单使用 criterion 为 `dummy` 的原版 objective。
- 内部分数 owner 使用玩家 UUID 字符串，避免改名后产生第二份分数。
- 玩家在线时，分数条目的显示名称会刷新为当前游戏名称。
- 分数由原版 `scoreboard.dat` 保存，允许管理员设置负数。
- 玩家显示 profile、移动厘米余量和黑名单分数归档保存在当前世界的 SavedData 中。
- 不同世界拥有独立的分数、个人显示配置和移动余量。
- 原版侧边栏最多显示分数最高的 15 个条目。
- 模组不会永久占用一个全服共享的 sidebar objective，而是向每名玩家分别发送当前应显示的 objective，因此不同玩家可以同时观看不同榜单。

---

## 配置文件

### 文件位置

```text
config/starrylist.json
```

路径相对于当前 Minecraft 游戏目录或独立服务器根目录。

### 默认配置

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

配置文件不存在时，模组会写入以上默认值。缺少普通字段时会补齐默认值并重新保存。配置无法解析或字段无效时，启动流程会备份无效文件并恢复默认配置；手动执行重载失败时则继续使用此前有效的活动配置。

### 通用设置（`general`）

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `language` | `string` | `en_us` | 服务端指令反馈和榜单名称使用的语言；支持 `en_us`、`zh_cn` |
| `adminPermissionLevel` | `int` | `2` | 执行 `/starryadmin` 所需的原版权限等级，范围 `0`～`4` |

语言设置由服务器统一决定。服务端发送已经翻译完成的 literal 文本，因此未安装模组的客户端也能看到完整消息。

### 默认显示设置（`display`）

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `hiddenByDefault` | `boolean` | `false` | 没有个人覆盖的玩家是否默认隐藏侧边栏 |
| `rotationEnabled` | `boolean` | `true` | 默认 profile 是否轮转多个榜单 |
| `rotationIntervalSeconds` | `int` | `20` | 默认轮转间隔，范围 `1`～`3600` 秒 |
| `enabledBoards` | `string[]` | `["mining", "placing", "mob_kills"]` | 默认启用的榜单，只能使用六个固定 ID，不允许重复；允许使用空数组 |

`enabledBoards` 可使用：

```text
mining
placing
mob_kills
player_kills
deaths
travel_distance
```

JSON 中的数组顺序不会改变显示顺序。启用项始终按上面的内置顺序显示和轮转；关闭 `rotationEnabled` 时固定显示第一个已启用榜单。空数组是合法设置，表示当前没有 StarryList 榜单可显示。旧版 `defaultBoards` 会在加载时自动迁移，旧版空数组也会保持为空。

### 黑名单（`blacklist`）

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `playerNamePatterns` | `string[]` | `[]` | 对完整玩家名执行大小写不敏感的 Java 正则匹配 |

命中任意表达式的玩家停止自动累计六个榜单，并从可见 objective 中移除。已有分数会按世界归档而不是删除；玩家不再命中黑名单时会恢复。无效或空白正则会使配置验证失败。例如 `bot_.*` 可匹配所有以 `bot_` 开头的玩家名。

修改配置后执行：

```text
/starryadmin reload
```

成功重载会立即更新服务端语言、objective 显示名和所有在线玩家的侧边栏。配置无效时不会部分应用。

---

## 玩家显示模型

每名玩家的 profile 有三种模式：

| 模式 | 行为 |
|---|---|
| `DEFAULT` | 实时跟随服务器 `display` 配置；这是没有个人记录时的默认模式 |
| `CUSTOM` | 使用玩家自己的榜单开关、轮转开关和间隔 |
| `HIDDEN` | 不显示 StarryList 侧边栏，同时保留隐藏前的全部设置 |

玩家在 `/starry` 中选择**使用服务器默认设置**时会删除个人覆盖，而不是复制一份当前默认值。管理员以后修改服务器默认配置时，这些玩家会自动跟随新设置。

玩家修改榜单、轮转或间隔时会基于当前有效设置保存个人 profile。隐藏期间修改设置不会自动取消隐藏；再次点击显示后恢复这些设置。个人设置只影响该玩家。玩家加入或修改设置时会立即从第一个已启用榜单开始显示，经过完整轮转间隔后才切换下一榜。

---

## 玩家菜单

`/starry` 无需管理员权限，会直接打开固定、无分页的六榜物品栏菜单。该指令没有参数或子命令；控制台和命令方块执行时会收到“仅玩家可用”的提示。

菜单为五行容器：最上、最下两行为灰色玻璃，其余分隔与包边使用浅灰色玻璃；中间六张榜单卡片按固定顺序相邻排列。已启用卡片带附魔光效，停用卡片不发光；三个原头颅图标改用铁剑、钻石剑和不死图腾以确保光效清晰。允许停用全部六个榜单，此时不会显示 StarryList 侧边栏，但榜单开关、轮转和间隔仍保存在 profile 中。底部控件可恢复服务器默认值、隐藏或显示侧边栏、切换轮转、通过带取消按钮的铁砧界面输入 `1`～`3600` 秒间隔，以及关闭菜单。成功修改会向操作者播放清脆的经验球音效，立即保存到当前世界 SavedData 并刷新侧边栏。

菜单由内嵌在模组 JAR 中的 SGui 实现，只发送原版容器 packet；玩家客户端无需单独安装 SGui 或 The-Starry-List。菜单优先使用玩家上报的 `zh_cn` 或 `en_us`，否则回退到服务器配置语言，最后回退到英语。

---

## 管理员指令

`/starryadmin` 默认需要原版权限等级 `2`。服务器控制台始终可以执行。`<targets>` 使用原版在线玩家选择器，因此支持玩家名以及 `@a`、`@p` 等选择器。

管理员操作只保留指令形式；`/starryadmin` 不会打开或提供容器 GUI。

| 指令 | 功能 |
|---|---|
| `/starryadmin` | 显示管理员指令类别提示 |
| `/starryadmin reload` | 重新读取、验证并应用配置 |
| `/starryadmin score get <boardId> <player>` | 查看在线玩家在指定榜单的分数和内部 UUID owner |
| `/starryadmin score set <boardId> <targets> <value>` | 将目标玩家的分数替换为指定整数 |
| `/starryadmin score add <boardId> <targets> <value>` | 对目标玩家的分数增加指定整数，可为负数 |
| `/starryadmin score reset <boardId> <targets>` | 删除目标玩家在指定 objective 中的分数条目 |
| `/starryadmin score reset-all <boardId>` | 删除指定榜单中的全部分数条目，包括离线玩家条目 |
| `/starryadmin profile get <player>` | 查看在线玩家保存的 profile |
| `/starryadmin profile reset <targets>` | 将目标在线玩家恢复为 `DEFAULT` |
| `/starryadmin profile reset-all` | 删除世界中全部个人显示覆盖并刷新在线玩家 |

示例：

```text
/starryadmin score get mining Steve
/starryadmin score add deaths @a 1
/starryadmin score set travel_distance Steve 5000
/starryadmin score reset player_kills Steve
/starryadmin profile reset @a
```

分数运算使用 32 位有符号整数。增加分数后如果发生整数溢出，操作会失败，不会写入回绕后的错误结果。
对黑名单玩家执行管理员分数指令时，读取和修改的是其隐藏归档；只有玩家不再命中黑名单后，这些分数才会重新出现在 objective 中。

---

## Cloth Config 界面

在客户端安装 Cloth Config 和 ModMenu 后，配置界面包含：

- **全部**：第一个分类，以展开子分类形式包含下述所有真实配置项，并与独立分类同步编辑。
- **常规**：服务端消息语言和管理员权限等级。
- **显示**：默认隐藏、默认轮转、轮转间隔，以及一个可折叠的六榜子分类；每榜都有独立开关和独立重置按钮，默认启用前三榜。
- **黑名单**：可增删的玩家名正则列表，无效表达式会就地高亮；新增输入框有边框和示例占位提示。

保存时使用与 JSON 配置相同的完整验证。保存成功或失败都会显示 toast，详细异常会写入日志。

该界面不能穿过网络修改远程服务器。即使玩家连接的远程服务器也安装了 The-Starry-List，客户端 ModMenu 页面编辑的仍是客户端自己的游戏目录。

---

## 常见问题

### 玩家必须安装模组吗？

不需要。排行榜、侧边栏 packet、分数和指令都由服务器处理，原版客户端可以直接使用。

### 可以添加第七个榜单吗？

不可以。当前版本只管理六个固定榜单；服务器和玩家可以选择其子集及轮转方式，但不能改变内置顺序。

### 为什么传送没有增加移动距离？

移动榜读取原版连续移动统计。传送直接改变位置，不属于步行、飞行或载具移动，因此不会计入。

### 为什么两个玩家看到的榜单不同？

侧边栏按玩家分别发送。每名玩家都可以拥有独立的 `CUSTOM` profile，或选择跟随服务器默认值。

### 玩家改名后会丢分吗？

不会。scoreboard owner 使用 UUID 字符串；玩家上线时只刷新用于显示的名称。

### 如何彻底隐藏侧边栏？

玩家打开 `/starry` 并选择**隐藏侧边栏**。若希望新玩家默认隐藏，可将 `display.hiddenByDefault` 设置为 `true`。

### 配置重载失败会怎样？

当前有效配置继续运行，不会应用一半字段。请查看日志中的无效字段或解析错误，修正后再次执行重载。

---

## 从源码构建

需要 Java 25。仓库已包含 Gradle Wrapper：

```bash
./gradlew clean build -x test
```

Windows：

```powershell
gradlew.bat clean build -x test
```

构建产物位于 `build/libs/`。普通 JAR 用于安装，带 `-sources` 后缀的 JAR 包含源码。

---

## 致谢

- 原项目：[TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList)
- Fabric Loader 与 Fabric API
- SGui、Cloth Config 与 ModMenu

## 许可证

The-Starry-List 使用 [GNU Lesser General Public License v3.0](./LICENSE) 许可证。
