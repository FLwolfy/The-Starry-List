# StarryList 脚本 SDK

[English](README.md)

这是 StarryList Groovy 榜单的最小编辑与编译检查工程，不是 Minecraft 服务端，也不包含
世界、Minecraft JAR 或运行配置。

## 使用方法

1. 安装 JDK 25。
2. 用支持 Gradle 和 Groovy 的编辑器将本目录作为 Gradle 工程打开。
3. 等待首次 Gradle 同步下载 Minecraft、Fabric API、Groovy 和 Gradle 依赖。
4. 编辑或添加 `config/*.groovy`，并在 `config/lang/*.json` 中维护扁平翻译条目。
5. 运行 `./gradlew compileGroovy`；Windows 使用 `gradlew.bat compileGroovy`，检查语法和
   import。
6. 将完成的脚本复制到 `config/starrylist/boards/`，语言文件复制到
   `config/starrylist/lang/`，再执行
   `/starryadmin scripts validate` 和 `/starryadmin scripts reload`。

新导入的 Groovy 榜单默认停用。检查脚本后，在配置界面打开它的“开启/关闭”，或把其 ID
加入 `boards.enabledScriptBoards`，保存并 reload 后，它才会创建 objective 或接收受管事件。

模板默认使用动态 Groovy，避免要求服主为 Fabric Event 的 Closure 参数逐个声明类型。
`compileGroovy` 负责检查语法和类路径解析；服务端验证仍负责榜单元数据、已安装模组兼容性
和重载行为检查。

SDK 内置的本地 Maven 仓库会自动关联 StarryList 主程序与源码。完成 Gradle 同步后，IDE
无需手动附加 sources JAR 即可显示方法注释并跳转到源码。

## 脚本翻译

在榜单的 `translations()` 中使用 `translatableText(titleKey, loreKeys...)`。声明的每个 key
都必须存在于 `config/lang/en_us.json`；其他语言可以省略 key，服务端会回退英文并记录警告。
locale 由文件名动态发现，外部文件仅影响 Groovy 榜单标题和 lore。通过 `text()` 编写的
字面量 map 仍然有效。

## 内置示例

`config/example.groovy` 实现了一个较完整的趣味“星光远征榜”，用到了全部公开 registrar
API，包括两种 `listen()`、
`onActiveStateChanged()`、`addAutomatic()`、`setAutomatic()`、
`display()`、`isEnabled()`、两种玩家状态访问方式、`accumulate()`、`runtime()` 与
`server()`。代码内的注释说明了各 API 的使用目的，以及哪些数据应当持久化。

黑名单过滤完全在内部完成。`addAutomatic()`、`setAutomatic()` 和 `accumulate()` 会自动
拒绝黑名单玩家，registrar 不暴露黑名单判断接口。通用 `state()` 需要镜像分数时，应像
示例一样写入自动计分操作返回的结果。

## 可选资源生命周期

普通 `registrar.listen()` 订阅由 StarryList 自动管理。脚本若自行创建缓存、采样 baseline
或动态监听器，可以注册一组生命周期 Closure：

```groovy
registrar.onActiveStateChanged(
    { -> rebuildResources() },
    { -> releaseResources() }
)
```

榜单被停用、替换、删除或服务器停止时会执行清理。validate 和编辑器预览不会执行这些回调。
