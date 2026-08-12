# StarryList Script SDK

[简体中文](README.cn.md)

This is a minimal editing and compile-check project for StarryList Groovy boards. It is not a
Minecraft server and contains no world, Minecraft JAR, or run configuration.

## Use

1. Install JDK 25.
2. Open this directory as a Gradle project in an editor with Groovy support.
3. Allow the first Gradle sync to download Minecraft, Fabric API, Groovy, and Gradle dependencies.
4. Edit or add `config/*.groovy` files.
5. Run `./gradlew compileGroovy` (`gradlew.bat compileGroovy` on Windows) to check syntax and
   imports.
6. Copy finished scripts to the server's `config/starrylist/boards/` directory, then run
   `/starryadmin scripts validate` and `/starryadmin scripts reload` on the server.

The included project intentionally uses dynamic Groovy. `compileGroovy` checks syntax and classpath
resolution without forcing explicit types onto Fabric event Closure parameters. Server validation
remains authoritative for board metadata, installed-mod compatibility, and reload behavior.

The bundled local Maven repository associates the StarryList binary with its sources. After Gradle
sync, IDE documentation and source navigation should work without manually attaching a sources JAR.
