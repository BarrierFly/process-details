# Process Details

**English** | [中文](README_cn.md)

A client-side Fabric mod for **Minecraft 1.21.11** that adds detailed progress
readouts to Minecraft's hidden loading processes. It merges the former
*Loading Progress Details* and *Xaero Loading Details* mods and adds world-save
progress when exiting a singleplayer world.

## Features

### Resource reload details

While a resource reload is running (startup or F3+T), two extra lines are drawn
under the vanilla progress bar on the Mojang splash screen:

- **Line 1** – current stage (`Preparing resources` / `Applying resources` /
  `Reload complete`), progress percentage matching the bar, and elapsed time.
- **Line 2** – prepared/applied task counts, plus the reloaders that are still
  pending (e.g. `model_loader`, `sprite_uploader`), so you can see exactly what
  is taking so long.
- **Colormap section** – one line per biome color map (grass, foliage, dry
  foliage) with its live state and the resource-pack chain behind its texture
  (e.g. `file/MyPack ← vanilla`). A queued colormap drops the chain and shows
  what it is waiting for instead: how many submitted prepare tasks are still
  ahead of it in vanilla's shared background pool, plus the live worker load
  (e.g. `behind 4 queued prepare task(s) (7/7 threads busy)`). A read-but-not-
  applied colormap shows whether all reloaders have finished reading and
  whether its own apply turn has come (vanilla applies one reloader at a
  time).

Reloaders registered through Fabric API show their Fabric id instead of an
obfuscated class name.

### Text pipeline guard

During the first resource reload of a session, `ShaderManager`'s shader source
cache is still empty, and vanilla only preloads `pipeline/gui` and
`pipeline/gui_textured`; `pipeline/gui_text` (the pipeline behind every font
draw) compiles lazily on the first text draw — which fails, logging
`Couldn't find source for VERTEX shader (minecraft:core/rendertype_text)` and
`Couldn't compile pipeline minecraft:pipeline/gui_text`. The invalid pipeline
stays cached and subsequent font draws silently no-op outside the IDE.

Before drawing detail text, the mod precompiles `gui_text` the same way
`preloadUiShader` does (from the current pack stack first, falling back to the
vanilla pack), so the error no longer appears and the detail lines render as
soon as fonts are applied. It also logs diagnostics: the effective pack chain
in priority order and which pack actually serves
`shaders/core/rendertype_text.vsh/.fsh`. If the pipeline still cannot compile,
a WARN-level diagnostic is logged to help pinpoint the pack that broke the
shader.

### World save details (new)

When leaving a singleplayer world, the vanilla "Saving world" screen is
augmented with:

- **Line 1** – overall save percentage and elapsed time.
- **Line 2** – the dimension currently being saved, chunk write progress, and
  the dimension index (e.g. `minecraft:overworld · Chunks 123/456 · Dimension 1/3`).

Progress is measured on the integrated server's save path
(`MinecraftServer#stopServer` → `ServerLevel#save` → `ChunkMap#saveAllChunks`),
so it does not appear when disconnecting from multiplayer servers (nothing is
saved there) or during background autosaves.

### Xaero's World Map details (optional)

When [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) is
installed, its hard-coded "Preparing World Map..." screen is replaced by a
detailed live view of the map pipeline: the phase stack of the most recently
active processor thread with elapsed time, live processor state (world id,
dimension, pause flags, region load/save queues, the region currently in view).
If Xaero's World Map is **not** installed, this feature is skipped entirely
(its mixins are disabled through a mixin config plugin) and everything else
keeps working.

The mod compiles against, but never redistributes, Xaero's obfuscation-free
classes (`xaero.map.*`).

## Compatibility with RRLS

The reload details are designed to coexist with
[RRLS (Remove Reloading Screen)](https://modrinth.com/mod/rrls):

- The mod injects at the tail of `LoadingOverlay#drawProgressBar` and does not
  wrap, redirect or cancel any vanilla call, so it does not conflict with
  RRLS's own injections into the same method (which only recolor/re-lerp the
  bar itself).
- When RRLS is configured to skip the splash screen entirely, the dummy
  graphics it uses no-ops all draw calls, so this mod's text silently
  disappears instead of crashing.
- When RRLS is set to `PROGRESS` mode, the detail lines are drawn under RRLS's
  own progress bar as well.

## Building

Place Xaero's jars (All Rights Reserved — not committed) into `libs/`:

```
libs/xaeroworldmap-fabric-1.21.11-1.40.16.jar
libs/xaerolib-fabric-1.21.11-1.1.15.jar   (extracted from worldmap's META-INF/jars/)
```

Then:

```bash
./gradlew build
```

The built jar can be found in `build/libs/`.

## License

[MIT](LICENSE)
