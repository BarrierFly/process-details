# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

## What this is

A client-only Fabric mod for Minecraft **1.21.11** ("Process Details") that shows
detailed progress for three loading processes:

1. Vanilla resource reloads (Mojang splash screen) — always active.
2. Singleplayer world saving while quitting a world — always active.
3. Xaero's World Map "Preparing World Map..." replacement — **optional**, only
   when Xaero's World Map is installed.

There is no Xaero source code here. The mod compiles against, and mixes into,
Xaero's **obfuscation-free** classes (`xaero.map.*`), whose Minecraft type
references are remapped from intermediary to named mappings by Fabric Loom.
Mappings are **Mojang official** (`loom.officialMojangMappings()`).

## Layout

```
libs/                        Xaero mod jars needed to compile/run (NOT committed, see below)
src/main/java/dev/processdetails/
  ProcessDetails.java        Mod initializer + logger
  reload/ReloadDetailsRenderer.java     Vanilla reload detail lines
  worldsave/
    WorldSaveTracker.java    Thread-safe save session state (server thread writes)
    WorldSaveDetailsRenderer.java     Drawn on the vanilla "Saving world" screen
  xaero/
    LoadingStatus.java       Thread-aware phase stack shared with the renderer
    LoadingScreenRenderer.java        Custom Xaero loading screen drawing
  mixin/
    ProcessDetailsMixinPlugin.java    Gates the xaero.* mixins on mod presence
    LoadingOverlayMixin.java          Reload details (tail of drawProgressBar)
    SimpleReloadInstanceAccessor.java Exposes SimpleReloadInstance counters
    GenericMessageScreenMixin.java    Save details on the "Saving world" screen
    MinecraftServerMixin.java         Save session begin/finish (stopServer)
    ServerLevelMixin.java             Per-dimension save hooks
    ChunkMapMixin.java                Chunk total + per-chunk write counting
    xaero/                            Only applied when Xaero's World Map exists
      GuiMapMixin.java, MapProcessorMixin.java, MapSaveLoadMixin.java,
      WorldDataHandlerMixin.java, WorldDataReaderMixin.java
src/main/resources/
  fabric.mod.json             xaeroworldmap is under "suggests", not "depends"
  process-details.mixins.json
  assets/process-details/lang/en_us.json, zh_cn.json
```

## Prerequisites for building

`libs/` is git-ignored because Xaero's jars are All Rights Reserved (do not
commit them). Place these files there before building:

```
libs/xaeroworldmap-fabric-1.21.11-1.40.16.jar
libs/xaerolib-fabric-1.21.11-1.1.15.jar   (extracted from worldmap's META-INF/jars/)
```

JDK 21 is required.

## Build / test commands

```bash
./gradlew build        # compile + remap -> build/libs/*.jar
./gradlew runClient    # dev client; Xaero + Fabric API are modLocalRuntime deps
```

There are no automated tests. Verify changes by running the client, (re)loading
resources (F3+T), quitting a singleplayer world, and opening the world map.

## Conventions

- Java 21, tabs for indentation, 4-wide tab stops, package `dev.processdetails`.
- Vanilla mixin handlers are named `processdetails$<what>`; Xaero mixin handlers
  are named `pd$<what>`. Keep them `private` and minimal.
- Do not add code comments unless they explain non-obvious intent.
- `WorldSaveTracker` and `LoadingStatus` are written from server/map threads and
  read from the render thread; keep them dependency-light and thread-safe.
- User-facing strings for the reload/save features go through lang keys in the
  `process-details.` namespace (en_us + zh_cn). The Xaero loading screen keeps
  its hard-coded English debug-style lines.

## Mixin rules (important)

- Vanilla targets use Mojang-mapped names; Loom generates the refmap.
- Xaero target method **names** are Xaero names and are not obfuscated; they
  stay as-is. Handler parameter lists must match the target method's runtime
  descriptor exactly for the arguments you declare. Current handlers are
  known-good against Xaero 1.40.16 / 1.21.11; re-verify hooks against the
  remapped Xaero jar (Loom caches it under `.gradle/loom-cache/remapped_mods/`,
  inspect with `javap -p -classpath <remapped-jar> xaero.map...`) when the
  target mod updates.
- Avoid Minecraft type names inside `@At(target = "...")` strings; plain Xaero
  names and JDK types are safe.
- Anything under `mixin/xaero/` must not be referenced from non-mixin code
  except through classes only loaded by those mixins (lazy class loading keeps
  the game working without Xaero).

## License

Project code is MIT (see `LICENSE`). Xaero's mods remain All Rights Reserved
and are only referenced as a compile/runtime dependency, never redistributed
here.
