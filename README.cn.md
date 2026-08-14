# The-Starry-List 模组使用文档

**The-Starry-List** 是面向 Minecraft Fabric 的可扩展服务端排行榜模组，提供六个内置榜单、玩家独立侧边栏设置、管理员工具、黑名单和可热重载的 Groovy 榜单。

See the English document [here](./README.md).

玩家可以使用未修改的原版客户端：计分、侧边栏、指令和 `/starry` 物品栏菜单都在服务端运行。本项目完全重写自 [TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList)。

---

## 功能与特性

- 六个内置榜单：挖掘、放置、怪物击杀、玩家击杀、死亡和移动距离。
- 使用原版 scoreboard objective 保存按世界持久化的分数。
- 每名玩家独立选择榜单、显示状态、轮转开关和间隔。
- 无需客户端模组即可使用物品栏形式的 `/starry` 设置菜单。
- 支持玩家名正则黑名单，并自动冻结和恢复分数。
- 支持可热重载的 Groovy 榜单和自动生成的编辑 SDK。
- 服务端消息与榜单介绍支持英语和简体中文。
- 单人游戏和 LAN 主机可选用 Cloth Config 与 ModMenu 编辑配置。

---

## 安装

独立服务器安装步骤：

1. 安装 Fabric Loader 和 Fabric API。
2. 将 The-Starry-List 放入服务器的 `mods/` 目录。
3. 启动服务器，配置和 Groovy 目录会自动生成。

独立服务器不需要 Cloth Config 或 ModMenu，普通玩家也无需安装任何内容。单人游戏或 LAN 若要使用图形化配置，请在主机客户端安装 The-Starry-List、Fabric API、Cloth Config 和 ModMenu。客户端配置界面只会修改当前游戏目录，不能管理远程服务器。

---

## 内置榜单

| 榜单 ID | Objective | 名称 | 统计内容 |
|---|---|---|---|
| `mining` | `sl_mining` | 挖掘榜 | 玩家成功破坏的方块数 |
| `placing` | `sl_placing` | 放置榜 | 玩家成功放置方块的次数 |
| `mob_kills` | `sl_mob_kills` | 击杀榜 | 玩家击杀的非玩家生物数 |
| `player_kills` | `sl_player_kills` | 战神榜 | 玩家击杀其他玩家的次数 |
| `deaths` | `sl_deaths` | 死亡榜 | 玩家因任意原因死亡的次数 |
| `travel_distance` | `sl_travel` | 移动距离榜 | 原版连续移动统计累计的整格距离 |

移动距离包括步行、游泳、攀爬、飞行、鞘翅、矿车、船和支持的坐骑；传送不会增加移动距离。

---

## 指令

### 玩家指令

| 指令 | 功能 |
|---|---|
| `/starry` | 打开玩家设置菜单 |

玩家可以在菜单中选择榜单、隐藏或显示侧边栏、切换轮转、修改轮转间隔或恢复服务器默认设置。这些设置只影响当前玩家。

### 管理员指令

`/starryadmin` 默认要求原版权限等级 `2`，服务器控制台始终可以使用。分数与 profile 指令在对应位置支持在线玩家和原版选择器。

| 指令 | 功能 |
|---|---|
| `/starryadmin` | 显示管理员指令提示 |
| `/starryadmin reload` | 同时重载 JSON 配置和 Groovy 榜单 |
| `/starryadmin prune` | 永久清理已不存在榜单 ID 对应的保存数据 |
| `/starryadmin scripts validate` | 编译并验证所有 Groovy 文件，但不应用 |
| `/starryadmin scripts reload` | 重载 Groovy 榜单，保留当前 JSON 设置 |
| `/starryadmin scripts list` | 列出脚本 ID、objective 和活动订阅数 |
| `/starryadmin score get <boardId> <player>` | 查看在线玩家的分数 |
| `/starryadmin score set <boardId> <targets> <value>` | 用有符号整数覆盖分数 |
| `/starryadmin score add <boardId> <targets> <value>` | 为分数增加有符号整数 |
| `/starryadmin score reset <boardId> <targets>` | 删除所选分数条目 |
| `/starryadmin score recalculate <boardId> <targets>` | 根据榜单的权威数据重算在线玩家分数 |
| `/starryadmin score reset-all <boardId>` | 删除一个榜单的所有分数 |
| `/starryadmin profile get <player>` | 查看在线玩家保存的显示 profile |
| `/starryadmin profile reset <targets>` | 让玩家恢复服务器默认 profile |
| `/starryadmin profile reset-all` | 删除所有玩家的个人显示覆盖 |

---

## 配置文件

### 文件位置

```text
config/starrylist/starrylist.json
```

每次世界/服务器启动都会自动联合重载一次 JSON 和 Groovy 榜单。世界运行时修改配置后，请执行 `/starryadmin reload`。Cloth Config 保存的是同一份 JSON；保存界面不会立即改变运行中的服务器，因此仍需 reload。

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
  "boards": {
    "disabledBoards": [],
    "enabledScriptBoards": []
  },
  "blacklist": {
    "playerNamePatterns": []
  }
}
```

### 通用设置

| 字段 | 类型 | 说明 |
|---|---|---|
| `general.language` | `string` | 从内置核心资源和活动脚本 locale 自动发现的服务端语言 |
| `general.adminPermissionLevel` | `int` | `/starryadmin` 所需原版权限等级，范围 `0`～`4` |

### 核心语言与脚本语言

核心服务端语言会在启动时从本模组的
`assets/the-starry-list/lang/<locale>.json` 自动发现。新增随模组打包的语言只需添加一个合法的
扁平 Minecraft 语言 JSON，无需再修改 Java enum 或注册表。每个文件必须包含该语言自己的
显示名称，例如：

```json
{
  "starrylist.language.name": "简体中文"
}
```

`en_us.json` 必须存在，并作为核心翻译 key 缺失时的回退。打包资源只在模组启动时加载一次。
与之不同，`config/starrylist/lang/*.json` 只保存 Groovy 榜单标题和 lore，并会在脚本 preview、
validate 和 reload 时重新发现。

### 默认显示设置

| 字段 | 类型 | 说明 |
|---|---|---|
| `display.hiddenByDefault` | `boolean` | 默认隐藏使用服务器设置玩家的侧边栏 |
| `display.rotationEnabled` | `boolean` | 在多个默认榜单间轮转 |
| `display.rotationIntervalSeconds` | `int` | 轮转间隔，范围 `1`～`3600` 秒 |
| `display.enabledBoards` | `string[]` | 默认玩家 profile 显示的榜单 |

数组顺序不会改变榜单顺序。空的 `enabledBoards` 合法，表示侧边栏不显示任何榜单。

### 榜单加载设置

| 字段 | 类型 | 说明 |
|---|---|---|
| `boards.disabledBoards` | `string[]` | 不应加载的内置榜单 ID |
| `boards.enabledScriptBoards` | `string[]` | 明确允许加载的 Groovy 榜单 ID |

内置榜单默认加载。所有新生成或导入的 Groovy 榜单默认停用，必须加入 `enabledScriptBoards` 或在 Cloth Config 中启用。加载与默认显示是两件事：榜单必须先加载才能运行，而 `display.enabledBoards` 只决定它是否出现在默认侧边栏 profile 中。

### 黑名单

| 字段 | 类型 | 说明 |
|---|---|---|
| `blacklist.playerNamePatterns` | `string[]` | 对完整玩家名进行大小写不敏感匹配的 Java 正则表达式 |

命中黑名单的玩家会从可见 objective 中消失，自动计分也会冻结。已有分数会归档而不是删除；解除黑名单后恢复原分数，不会补算黑名单期间的数据。`addAutomatic`、`setAutomatic` 和 `accumulate` 会在内部执行黑名单策略，Groovy 榜单无需自行判断。管理员分数指令仍可修改黑名单玩家的归档分数。

---

## 玩家显示与数据保存

玩家有三种显示模式：

| 模式 | 行为 |
|---|---|
| `DEFAULT` | 跟随当前服务器 `display` 设置 |
| `CUSTOM` | 使用玩家自己的榜单、轮转和间隔设置 |
| `HIDDEN` | 隐藏侧边栏，同时保留玩家的其他选择 |

分数使用玩家 UUID，因此改名不会丢失进度。Scoreboard、个人显示设置、Groovy 榜单状态和归档分数都随世界保存。停用或删除榜单会归档其分数；只有 `/starryadmin prune` 会永久清理已经不存在的榜单 ID 数据。

---

## Groovy 榜单

受信任的服主无需重新构建模组即可添加榜单。每个榜单使用一个 `*.groovy` 文件，放入：

```text
config/starrylist/boards/
```

若目录中缺少 `ore.groovy`，StarryList 会自动创建它作为可编辑示例；自动生成和手动导入的脚本仍然默认停用。

推荐工作流：

1. 在模组项目运行 `./gradlew build`，解压 `the-starry-list-<版本>-script-sdk.zip`；也可以使用发布包中的 SDK。
2. 在 SDK 中编辑 `config/*.groovy` 与 `config/lang/*.json`，执行 `./gradlew compileGroovy`。
3. 将脚本复制到 `config/starrylist/boards/`，语言文件复制到 `config/starrylist/lang/`。
4. 执行 `/starryadmin scripts validate`。
5. 将榜单 ID 加入 `boards.enabledScriptBoards`，或通过 Cloth Config 启用。
6. 执行 `/starryadmin reload`，再用 `/starryadmin scripts list` 检查订阅。

### 最小示例

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

每个文件必须只定义一个具体的 `StarryListScriptBoard`。ID、objective 名和 order 必须唯一。

分数重算是可选能力。覆盖
`OptionalInt recalculate(ServerPlayer player, StarryListBoardState state)` 后，该榜单才会出现在
`/starryadmin score recalculate` 的 `boardId` 补全中；方法返回绝对分数，实际写入由
StarryList 统一完成。保留默认 `OptionalInt.empty()` 的旧脚本仍可正常验证和加载。目标仅限
在线玩家，支持 `@a` 等原版选择器；管理员重算黑名单玩家时会覆盖其归档分数。

`translatableText()` 从 `config/starrylist/lang/<locale>.json` 的扁平字符串条目读取文案。声明的每个 key 都必须存在于 `en_us.json`；其他语言缺少 key 时会回退英文并记录警告。preview、validate 和 reload 都会重新发现语言文件，外部文件只影响 Groovy 榜单的标题与 lore。原有通过 `text()` 编写的字面量 map 仍然受支持。

### Registrar API

| API | 用途 |
|---|---|
| `listen(key, event, callback)` | 注册受管的 void Fabric Event |
| `listen(key, event, inactiveResult, callback)` | 注册带返回值的受管 Fabric Event |
| `onActiveStateChanged(onActivated, onDeactivated)` | 创建和清理脚本自己的缓存或附加资源 |
| `addAutomatic(player, delta)` | 使用整数饱和规则增加分数，并自动处理黑名单 |
| `setAutomatic(player, value)` | 同步绝对分数，并自动处理黑名单 |
| `accumulate(player, key, amount, unitsPerWhole)` | 保存不足一个单位的余数，并返回完成的整数单位 |
| `state(player)` / `state(uuid)` | 访问按榜单、按玩家隔离的持久状态 |
| `display(player)` / `isEnabled(player)` | 读取玩家当前生效的侧边栏设置 |
| `runtime()` / `server()` | 访问高级 StarryList 服务或 MinecraftServer |

请使用 `registrar.listen()`，不要直接调用 `Event.register()`，这样 reload 和榜单启停才能正确工作。回调抛出异常后，该订阅会停用到下一次成功 reload。只有受管监听之外的自有资源才需要 `onActiveStateChanged`；普通订阅无需清理。

Groovy 文件是完全受信任的服务端代码，不是沙箱配置。它可以访问公开的 Minecraft、Fabric、Java 和已安装模组 API，请只安装可信来源的脚本。

---

## 常见问题

### 玩家必须安装模组吗？

不需要。原版客户端可以看到侧边栏并使用 `/starry`。

### 为什么新 Groovy 榜单没有计分？

新脚本默认停用。请先验证文件，将 ID 加入 `boards.enabledScriptBoards`，执行 `/starryadmin reload`，再用 `/starryadmin scripts list` 确认订阅已启用。

### 为什么传送没有增加移动距离？

移动榜读取原版连续移动统计。传送直接改变位置，不会被计入。

### reload 失败会怎样？

之前有效的配置和脚本目录会继续运行。请查看服务端日志或验证输出，修正错误后再次 reload。

---

## 兼容性与部署

| 类型                       | 支持情况                                             |
|----------------------------|------------------------------------------------------|
| 模组加载器                 | Fabric Loader                                        |
| Minecraft                  | 26.1+                                                |
| 独立服务器                 | ✅ 只需安装在服务端                                  |
| 单人游戏 / LAN             | ✅ 安装在主机客户端                                  |
| 进入独立服务器的玩家客户端 | 无需安装                                             |
| 客户端配置界面             | 可选，需 Cloth Config 与 ModMenu                     |
| 语言                       | 从打包资源动态发现核心 locale，并支持动态脚本 locale |

---

## 致谢

- 原项目与作者：[crackun24 的 TheStarryMiningList](https://github.com/crackun24/TheStarryMiningList)
- Fabric Loader 与 Fabric API
- SGui、Cloth Config、ModMenu 与 Apache Groovy
