# The-Starry-List：Minecraft 26.1 完整重构计划

本文档是 The-Starry-List 2.0.0-26.1 的完整设计、实施和验收依据。旧版 1.16.5 代码只作为行为参考；不保留旧 API、包名、配置、命令或数据迁移能力。

## 1. 目标与固定决策

最终模组必须满足：

- 专用服务器安装后，未安装本模组的原版客户端也能加入并使用排行榜和指令。
- 客户端可选安装本模组、ModMenu 和 Cloth Config，以编辑本地 `config/starrylist.json`，供单人游戏或 LAN 集成服务器使用。
- 排行榜分数、玩家显示偏好和移动余量按 Minecraft 世界独立保存。
- 普通玩家及管理员指令完全不依赖客户端模组。
- 支持任意数量 JSON 自定义排行榜，同时支持事件增量和定时完整重算。
- JEXL 对齐 Pay-To-Teleport，包含 Minecraft 控制台命令和系统 shell 能力。

固定标识：

| 项目 | 值 |
|---|---|
| 项目名称 | `The-Starry-List` |
| Java 前缀 | `StarryList` |
| Fabric mod ID | `the-starry-list` |
| Java 包名 / Maven group | `com.flwolfy.starrylist` |
| 主类 | `com.flwolfy.starrylist.StarryListMod` |
| 作者 | `FLwolfy` |
| 版本 | `2.0.0-26.1` |
| 导出文件 | `the-starry-list-2.0.0-26.1.jar` |
| 玩家指令 | `/starry` |
| 管理指令 | `/starryadmin` |
| 主配置 | `config/starrylist.json` |
| 许可证 | `LGPL-3.0` |
| 当前仓库 | `https://github.com/FLwolfy/The-Starry-List` |
| Fork 来源 | `https://github.com/crackun24/TheStarryMiningList` |

本项目不承诺面向其他模组的稳定 Java API。“API 简便”指玩家和管理员的指令接口稳定、清晰、易用。

## 2. 工具链、构建与工程结构

### 2.1 固定版本

| 组件 | 版本 |
|---|---|
| Minecraft | `26.1` |
| Java | `25` |
| Gradle Wrapper | `9.5.1` |
| Fabric Loom | `1.17-SNAPSHOT` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.145.1+26.1` |
| Cloth Config | `26.1.154` |
| ModMenu | `18.0.0` |
| Apache Commons JEXL | `3.7.0` |
| Commons Logging | `1.3.5` |

Minecraft 26.1 使用 Mojang 提供的非混淆开发产物：Loom 插件为 `net.fabricmc.fabric-loom`，不声明 Yarn mappings，不执行 `remapJar`，Java source、target 和 release 均为 25。

### 2.2 Gradle 与仓库文件

`build.gradle` 必须使用普通 `implementation`/`api`；JEXL 与 Commons Logging 通过 `include` 嵌入最终 JAR；Cloth Config 和 ModMenu 仅用于可选客户端 GUI，不成为专用服务器硬依赖。生成 sources JAR，并把 `LICENSE` 放入构建产物。所有版本只在 `gradle.properties` 声明。

同步要求：

- Gradle wrapper 为 9.5.1，`gradlew` 有可执行位。
- `settings.gradle` 包含 Fabric、Maven Central 和 Plugin Portal 所需仓库。
- `.gitattributes` 统一换行；`.gitignore` 覆盖 Gradle、IDE、`run/`、macOS 和 JVM crash 文件。
- CI 使用 Ubuntu 24.04、Microsoft JDK 25、wrapper 校验、`./gradlew build` 和 `build/libs/` artifact；push/PR 构建，不自动发布 Release。

### 2.3 Fabric metadata

`fabric.mod.json`：

- `environment` 为 `*`。
- `main` 仅注册 `StarryListMod`；`modmenu` 注册 `StarryListModMenu`；无单独 client initializer。
- 硬依赖：loader `>=0.19.3`、Minecraft `~26.1`、Java `>=25`、`fabric-api`。
- `cloth-config2` 和 `modmenu` 只放在 `suggests`。
- license 为 `LGPL-3.0`，homepage/sources 指向当前仓库。
- mixin 文件为 `the-starry-list.mixins.json`，不引用不存在的 icon。

### 2.4 Java 分层及规范

```text
com.flwolfy.starrylist
├── StarryListMod / StarryListRuntime
├── command
│   ├── StarryListCommand
│   ├── StarryListAdminCommand
│   └── StarryListBoardArgument
├── data
│   ├── config/*
│   ├── lang/*
│   ├── script/* + script/context/*
│   └── state/*
├── scoreboard/*
├── display/*
├── event/*
├── mixin/*
└── modmenu/*
```

类型名统一 `StarryList*`；常量使用大写下划线；record 字段、局部变量和 JSON 字段使用 lower camel case；Java 两空格缩进；注释和 Javadoc 使用英文；公共语义、持久化和复杂流程必须有说明；不使用 Lombok。

## 3. 配置、状态和数据模型

### 3.1 默认配置

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
    "defaultBoards": ["mining", "placing", "mob_kills", "player_kills"]
  },
  "customBoards": []
}
```

验证规则：

- 语言必须是已支持枚举；管理权限 0–4；显示间隔 1–3600 秒。
- 未默认隐藏时至少有一个有效、启用的默认榜单；保留顺序且拒绝重复 ID。
- 自定义 ID 匹配 `[a-z0-9._-]{1,64}`，不得与内置或其他自定义 ID 冲突。
- objective name 非空且唯一，不得占用其他系统的 objective。
- 启用的自定义榜单至少有一个 source；脚本必须能在无副作用的情况下成功编译。
- scheduled 间隔 1–86400 秒且更新模式只能为 `SET`。

配置文件不存在时写默认 JSON；普通新增字段自动补齐并漂亮格式保存。保存和 reload 均先完整反序列化、验证、编译和 objective 预检，全部成功后才切换活动快照。失败时保留旧配置、榜单和显示状态。启动配置无效时备份为 `.invalid-<timestamp>.json`，明确记录字段并恢复默认值。配置快照使用不可变 record，管理器使用读写锁和原子替换。

### 3.2 六个内置榜单

| ID | Objective | 统计规则 |
|---|---|---|
| `mining` | `sl_mining` | 玩家成功破坏方块时 +1 |
| `placing` | `sl_placing` | 一次成功 BlockItem 放置动作 +1 |
| `mob_kills` | `sl_mob_kills` | 玩家击杀非玩家 LivingEntity 时 +1 |
| `player_kills` | `sl_player_kills` | 玩家击杀 ServerPlayer 时 +1 |
| `deaths` | `sl_deaths` | 玩家任意死亡时 +1 |
| `travel_distance` | `sl_travel` | 连续移动累计每 100 cm +1 格 |

破坏涵盖任意方块、工具和空手，只接受成功完成的玩家破坏；爆炸、活塞、自然变化和取消行为不计。放置只统计成功动作，床、双层植物等一次动作仍计一次。击杀按 Minecraft 的玩家 killer 归属，包含玩家投射物；玩家和非玩家击杀互斥。死亡包含环境、命令、怪物和 PvP。所有游戏模式参与。

### 3.3 移动距离

监听 `ServerPlayer.awardStat` 中原版确认的连续移动厘米统计，而不是坐标差：walk、crouch、sprint、水上/水下行走、swim、climb、fall、fly、elytra、minecart、boat、pig、horse、strider、happy ghast、nautilus，以及 26.1 同类骑乘统计。

因此 `/tp`、末影珍珠、传送门、跨维度、重生和传送 API 不计，高速鞘翅和载具正常计。每名玩家的 0–99 cm 余量保存在世界数据中；达到 100 cm 才增加 scoreboard 整格。自定义 travel context 同时提供本次厘米、格数和移动类型。

### 3.4 权威分数和世界状态

分数使用 `ServerScoreboard` dummy objectives。内部 score holder 是 UUID 字符串，display component 保存最近在线名称。分数随原版 `scoreboard.dat` 保存并允许负数。脚本返回值必须是精确 32 位整数；null、非整数浮点、溢出和错误类型均不修改分数。所有管理操作和脚本统一经过 `StarryListScoreService`。

世界 `SavedDataType<StarryListState>` 保存：

- UUID → `StarryListDisplayProfile`。
- UUID → 移动厘米余量。
- UUID → 最近玩家名。
- board ID → 本模组管理的 objective。
- 可安全清理的 orphan objective 集合。

显示 profile：

```text
mode: DEFAULT | CUSTOM | HIDDEN
boards: 有序 board ID 列表
rotationEnabled: boolean
rotationIntervalSeconds: int
```

无记录等同 DEFAULT；`display default` 删除覆盖。CUSTOM 完全独立于服务器默认轮转；暂时无效的 ID 在有效序列中过滤但原顺序保留；玩家离线不删除偏好；所有修改调用 `setDirty()`。不创建跨世界的玩家 JSON 文件。

## 4. 事件与 JEXL 自定义榜单

### 4.1 事件来源和顺序

- `PlayerBlockBreakEvents.AFTER`：成功破坏。
- `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY`：实体/玩家击杀。
- `ServerLivingEntityEvents.AFTER_DEATH`：玩家死亡。
- `ServerPlayConnectionEvents.JOIN/DISCONNECT`：登录/离开。
- `ServerTickEvents.END_SERVER_TICK`：显示和定时任务。
- `BlockItem.place` RETURN mixin：返回 `consumesAction()` 后记录一次成功放置。
- `ServerPlayer.awardStat` mixin：捕获允许的连续移动厘米统计。

事件先构造只读类型化 context，再更新内置榜，然后按自定义榜单配置顺序和 source 顺序执行。单 source 失败不阻断其他 source。

### 4.2 自定义 JSON

```json
{
  "id": "diamond_miner",
  "objectiveName": "sl_diamond",
  "displayName": "Diamond Miner",
  "enabled": true,
  "sources": [
    {
      "trigger": "block_break",
      "update": "ADD",
      "intervalSeconds": 0,
      "script": "context.blockBreak().block().id() == 'minecraft:diamond_ore' ? 1 : 0"
    }
  ]
}
```

定时外部数据示例：

```json
{
  "id": "vip",
  "objectiveName": "sl_vip",
  "displayName": "VIP",
  "enabled": true,
  "sources": [{
    "trigger": "scheduled",
    "update": "SET",
    "intervalSeconds": 300,
    "script": "shell:runInt('vip-score ' + player.uuid())"
  }]
}
```

trigger：`block_break`、`block_place`、`mob_kill`、`player_kill`、`player_death`、`travel`、`player_join`、`player_leave`、`scheduled`。`ADD` 对当前分数加返回值，`SET` 替换；scheduled 固定 SET、只处理在线玩家并在登录时立即补算。

### 4.3 脚本变量和 context

公共变量：

- `player.uuid()/name()`。
- `context`：当前事件 context。
- `previousScore`：当前 source 执行前分数。
- `board.id()/displayName()/objectiveName()`。
- `now`：Unix 毫秒。

事件字段：

- block break：block ID/properties、位置/维度、工具 ID/count/damage/customName。
- block place：block、位置、item。
- mob kill：victim UUID/type/name、damage type。
- player kill：victim UUID/name、damage type。
- player death：可空玩家 killer、damage type。
- travel：centimeters、blocks、movementType、position。
- join/leave/scheduled：玩家位置快照。

namespace：`math` 为 Java Math；`minecraft:execute(command)` 以控制台最高权限运行命令并返回整数；`shell:execute` 返回 exit code/stdout/stderr；`shell:run` 要求退出码为零并返回 stdout；`shell:runInt` 要求 stdout 是单一整数。命令开头 `/` 可省略。

安全规则：配置编辑者等同拥有 Minecraft 控制台和服务器操作系统账号权限；不得接受玩家提交的不可信脚本；事件脚本同步运行，慢 shell 会卡服；外部查询优先 scheduled。

### 4.4 执行隔离

JEXL 使用 strict、non-silent 和 secure permissions 基线，只开放 StarryList context、执行器及其嵌套类型。源码编译结果缓存；验证只编译不执行副作用。

事件 source 在服务器线程按序同步执行。scheduled source 使用虚拟线程：主线程构造不可变快照，结果回服务器线程写分；Minecraft 命令调度回服务器线程；同 board/source/player 不重入；generation 变化、玩家离线或停服时丢弃结果；停服中断任务并终止 shell 子进程。单 source 失败保持分数，重复错误按 source 节流日志。

## 5. Scoreboard 展示与生命周期

### 5.1 Objective 协调

启动或成功 reload 时注册六个内置及所有启用自定义榜单，创建缺失 dummy objective，更新显示名和 integer render type，并对活动 objective 开始 tracking。服务器不永久占用全局 SIDEBAR；每名玩家使用原版 display objective packet 独立选择。

- board ID 和 objective 都未变：复用分数。
- 同 board ID 改 objective：复制全部分数，新名称生效，旧名称进入 orphan。
- `enabled=false`：停止更新和显示但保留数据。
- 从配置删除：进入 orphan，不自动删除。
- 与非 StarryList objective 冲突：整个 reload 失败。
- `/starryadmin prune` 只预览；`prune confirm` 才删除 SavedData 明确认定的 orphan。

### 5.2 默认和个人显示

服务器默认可见，序列为 mining、placing、mob_kills、player_kills，20 秒轮转。`hiddenByDefault` 隐藏 DEFAULT 玩家；关闭轮转固定第一个有效榜单；无效/停用 ID 被过滤。原版 sidebar 最多显示分数最高的 15 条。仅 objective 真正变化或强制刷新时发 packet；隐藏发送 SIDEBAR/null。登录、重生、reload 和个人设置后立即重算。

玩家指令树：

```text
/starry
/starry boards [page]
/starry display status
/starry display default
/starry display hide
/starry display set <boardIds>
/starry display add <boardId>
/starry display remove <boardId>
/starry display move <boardId> <index>
/starry display rotation <true|false>
/starry display interval <seconds>
```

`set` 接受逗号或空格分隔并保序去重，任一无效则全失败。`add` 追加；`remove` 不允许产生空 CUSTOM；`move` 使用从 1 开始的位置。DEFAULT/HIDDEN 上执行编辑操作时先复制当时服务器默认成为 CUSTOM。所有 board 参数有建议，普通玩家无权限门槛。控制台可看帮助/boards，但不能创建个人 profile。

管理员指令树：

```text
/starryadmin
/starryadmin reload
/starryadmin score get <boardId> <player>
/starryadmin score set <boardId> <targets> <value>
/starryadmin score add <boardId> <targets> <value>
/starryadmin score reset <boardId> <targets>
/starryadmin score reset-all <boardId>
/starryadmin profile get <player>
/starryadmin profile reset <targets>
/starryadmin profile reset-all
/starryadmin prune
/starryadmin prune confirm
```

权限等级来自配置，默认 2；控制台可执行；targets 使用原版玩家选择器。破坏性操作反馈受影响数量，所有反馈使用服务器配置语言。

## 6. 多语言和客户端配置

首版支持 `en_us`、`zh_cn`。服务器语言由配置全局决定，读取内置 JSON 后发送 literal component，保证无客户端模组也能看到完整译文；缺 key 回退英语，英语也缺失时显示 key 并记录警告。客户端 GUI 使用 translatable component 并跟随客户端语言。

ModMenu/Cloth Config 页面包含：

- General：语言和管理权限。
- Display：默认隐藏、轮转、间隔、默认榜单选择和排序。
- Custom Boards：新增、复制、删除、启停、排序；编辑 ID/objective/display name；source 新增、删除和排序；trigger、ADD/SET、scheduled interval、JEXL 编辑/导入/验证。
- All：普通配置汇总；复杂自定义榜单保留专用编辑入口。

保存前显示字段验证；失败 toast 展示摘要、日志保留堆栈。顶部明确界面只改本地文件，只影响单人/LAN，不能修改远程独立服务器。集成服务器运行时通过服务器线程走同一两阶段热重载；未运行时只保存 JSON。

## 7. 文档、许可证和清理

`README.md` 为英文，`README.cn.md` 为中文，互相链接。两者只介绍功能、fork 来源、六个内置榜、自定义 JEXL、服务端安装、客户端无需安装、可选 GUI、命令摘要、配置路径、安全警告和 LGPL-3.0。

删除旧 `xyz.mcsls.starryMiningListRebuilt`、旧 assets/mixin、错误命名半成品、Yarn/Java 21/旧 Fabric/旧导出名、旧 release workflow、旧 `/miningboard` 命令、旧配置路径和错误 GPL metadata。许可证文件统一为 `LICENSE`。

不迁移 `miningList.properties`、旧 `MiningList` objective、旧命令和旧个人显示状态。

## 8. 实施里程碑

1. 计划落盘：用本文档替换旧计划，保留与任务无关的用户修改。
2. 工程基线：26.1/Java 25/Gradle 9.5.1、metadata、CI、新入口，消除 Loom/Yarn 工具链错误。
3. 配置/语言/状态：JSON record、验证、默认合并、两阶段 reload、双语服务端消息、SavedData codec。
4. 内置榜单：objective registry/reconcile、UUID holder、六类事件，验证重启持久化。
5. JEXL：上下文、ADD/SET、scheduled、登录补算、命令/shell、隔离和停服清理。
6. 显示与指令：逐玩家 sidebar、DEFAULT/CUSTOM/HIDDEN、轮转、完整玩家和管理员命令。
7. GUI/文档：ModMenu、普通字段、自定义榜单/source CRUD、JEXL 编辑、双语资源和 README。
8. 验证交付：自动测试、专用服务器、LAN GUI、clean build、JAR 内容和残留检查。

## 9. 测试和验收

### 9.1 自动测试

- 配置默认 round-trip、缺字段补齐、各无效字段拒绝、失败 reload 保留旧快照、objective 改名迁移。
- 脚本 ADD 正/零/负、SET、context、scheduled 登录补算、math/命令/shell stub、编译和结果错误不改分、旧 generation 不应用、不重入。
- 显示 DEFAULT/CUSTOM/HIDDEN 转换、轮转顺序、关闭轮转、无效过滤、default 删除 profile、间隔边界、reload 强刷。
- 移动 50+50 cm 得一格、余量持久化、多种统计合并、teleport 不产生增量。

### 9.2 游戏内场景

1. 空手和各种工具成功破坏都使 mining +1，取消破坏不计。
2. 普通方块、床、双层植物成功放置各算一次，失败放置不计。
3. 近战/投射物怪物击杀进入 mob kills；PvP 只进入 player kills。
4. 环境和 PvP 死亡都进入 deaths。
5. 步行、游泳、鞘翅和载具进入 travel；TP、珍珠、传送门、跨维度和重生不计。
6. 两名玩家能显示不同榜单，并能隐藏、default、自定义顺序和独立轮转。
7. 重启保留分数、余量和 profile。
8. 热新增 ADD 榜立即计分；VIP scheduled SET 能读取 shell 且登录补算。
9. 无效 reload 不影响旧配置；删除榜单保留分数，prune 两阶段正确。
10. 无 StarryList 客户端可加入和用 `/starry`；无 Cloth/ModMenu 的专用服务器可启动。
11. ModMenu 保存本地 JSON，并在集成服务器运行时立即生效。

### 9.3 最终构建

- Java 25 + Gradle 9.5.1，`./gradlew clean build` 与 CI 成功。
- 输出 `the-starry-list-2.0.0-26.1.jar`，内含 JEXL、Commons Logging 和许可证。
- JAR 无旧包、旧语言目录、旧 mixin、Yarn 或旧版本残留。
- metadata 使用 `fabric-api`；专用服务器只需 Loader、Fabric API 和本模组。
- README 清楚说明 fork、功能、配置和高权限 JEXL 风险。

## 10. 明确假设

- 额外内置榜仅死亡和移动距离，不实现在线时长、独立坠落或伤害输出。
- 移动距离包含玩家自身及所有原版连续载具移动。
- 默认只显示四个核心榜，死亡和移动需由配置或玩家加入。
- 自定义 display name 是单一 literal，不做逐榜多语言映射。
- 服务端消息语言全局统一，不按客户端语言返回。
- sidebar 遵循原版最多 15 条限制。
- 不兼容旧配置、objective、命令或 Java API。
- JEXL 的 shell 和 Minecraft 命令能力是受信任管理员的有意功能，不削弱为安全沙箱。
