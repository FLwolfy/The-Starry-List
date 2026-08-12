# StarryList 脚本 SDK

[English](README.md)

这是 StarryList Groovy 榜单的最小编辑与编译检查工程，不是 Minecraft 服务端，也不包含
世界、Minecraft JAR 或运行配置。

## 使用方法

1. 安装 JDK 25。
2. 用支持 Gradle 和 Groovy 的编辑器将本目录作为 Gradle 工程打开。
3. 等待首次 Gradle 同步下载 Minecraft、Fabric API、Groovy 和 Gradle 依赖。
4. 编辑或添加 `config/*.groovy` 文件。
5. 运行 `./gradlew compileGroovy`；Windows 使用 `gradlew.bat compileGroovy`，检查语法和
   import。
6. 将完成的脚本复制到服务器的 `config/starrylist/boards/`，再执行
   `/starryadmin scripts validate` 和 `/starryadmin scripts reload`。

模板默认使用动态 Groovy，避免要求服主为 Fabric Event 的 Closure 参数逐个声明类型。
`compileGroovy` 负责检查语法和类路径解析；服务端验证仍负责榜单元数据、已安装模组兼容性
和重载行为检查。

SDK 内置的本地 Maven 仓库会自动关联 StarryList 主程序与源码。完成 Gradle 同步后，IDE
无需手动附加 sources JAR 即可显示方法注释并跳转到源码。
