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

Newly imported Groovy boards are disabled by default. Review the board, enable its **On/Off**
control in configuration (or add its ID to `boards.enabledScriptBoards`), save, and reload before
it can create an objective or receive managed events.

The included project intentionally uses dynamic Groovy. `compileGroovy` checks syntax and classpath
resolution without forcing explicit types onto Fabric event Closure parameters. Server validation
remains authoritative for board metadata, installed-mod compatibility, and reload behavior.

The bundled local Maven repository associates the StarryList binary with its sources. After Gradle
sync, IDE documentation and source navigation should work without manually attaching a sources JAR.

## Included example

`config/example.groovy` implements a playful **Starlight Expedition** board. It uses every public
registrar API, including both forms of
`listen()`, `onActiveStateChanged()`, `addAutomatic()`, `setAutomatic()`,
`display()`, `isEnabled()`, both player-state access forms, `accumulate()`, `runtime()`, and
`server()`. Inline comments explain why each API appears and which values should be persistent.

Blacklist filtering is entirely internal. `addAutomatic()`, `setAutomatic()`, and `accumulate()`
reject excluded players without script-side checks, and the registrar intentionally exposes no
blacklist predicate. When generic `state()` should mirror a score, write the automatic operation's
returned value as demonstrated by the example.

## Optional resource lifecycle

Normal `registrar.listen()` subscriptions are managed automatically. A board that creates its own
caches, sampling baselines, or dynamic listeners can register one lifecycle pair:

```groovy
registrar.onActiveStateChanged(
    { -> rebuildResources() },
    { -> releaseResources() }
)
```

Cleanup runs when the board is disabled, replaced, removed, or the server stops. Validation and
editor preview do not execute these callbacks.
