# Music Control developer architecture

This document describes the runtime event graph, resource-reload pipeline, music
state machine, and dependency packaging. The serialized resource-pack formats are
specified separately in [META_FORMAT.md](META_FORMAT.md).

## Build and dependency layout

The root source set contains version-independent gameplay, graph, GUI, and mixin
code. Each `versions/fabric-<minecraft>` project adds one narrow
`versions/shared-mc-<minecraft>` compatibility source set and its own resources.
`buildAll` builds every supported target (including its tests) and synchronizes one
runtime JAR per version into `build/modrinth`; `checkAll` runs the corresponding
verification tasks for all targets.

LibGui is a normal external Gradle dependency at compile time, but it is shipped
inside every runtime mod JAR. `gradle/fabric-target.gradle` declares it as
`modImplementation include(...)` for remapped targets and `implementation
include(...)` for non-remapped targets. Fabric Loom's `include` produces a nested
JAR such as `META-INF/jars/LibGui-17.0.0+26.2.jar` in the final artifact. Therefore:

- players do not install LibGui separately;
- LibGui source code is not copied into this repository;
- LibGui classes are not shaded into Music Control's package namespace;
- Fabric Loader discovers and loads the nested LibGui mod at runtime.

Cloth Config is packaged with the same nested-JAR mechanism. Fabric API and Mod
Menu are ordinary runtime dependencies and are not embedded by these declarations.

## Event graph model

### Concepts

The immutable runtime graph is `MusicGraphSnapshot`. A node is identified by a full
sound-event identifier and contains:

- a direct pool discovered from the active sound manager and `sounds.json` event
  links;
- zero or more parent node identifiers;
- `parentMix`, used when the direct pool is non-empty;
- `whenEmpty`, used when the direct pool is empty.

The graph is a DAG. Parent edges describe pool inheritance. Biome and dimension
bindings are activation mappings, not parent edges. Hidden events only affect UI
discovery; they do not remove nodes or tracks.

### Node semantics

`parentMix` has the following behavior for a non-empty direct pool:

| Value | Result |
| --- | --- |
| `exclusive` | Use only the node's direct pool. |
| `parents_only` | Ignore the direct pool and use the resolved parents. If no parent produces music, preserve the incoming vanilla/runtime pool. |
| `half` | Choose the direct pool or resolved parent pool with equal probability. If parents produce no music, use the direct pool. |
| `proportional` | Union the direct and resolved parent pools. |

`whenEmpty` is evaluated only when the direct pool is empty:

| Value | Result |
| --- | --- |
| `vanilla` | Preserve the incoming runtime pool exactly. This can be playable or empty. |
| `silent` | Produce policy silence. This is distinct from an empty playable result. |
| `parents` | Resolve and combine parent nodes. |

The incoming pool is called “vanilla” in the API, but it is more precisely the
pool produced by all earlier runtime context layers. This makes sequential overlays
possible without hard-coding Minecraft or resource-pack contents into Java.

Multiple parents are resolved independently and their playable pools are unioned.
An unknown node or a cycle encountered defensively during resolution preserves the
incoming pool; invalid published graphs are normally rejected before this can occur.

### Graph construction and precedence

A complete snapshot is rebuilt during every sound-resource reload:

1. The target-specific `music_control/version_profile.json` supplies built-in nodes,
   edges, biome/dimension bindings, hidden events, and portable projections.
2. `MusicControlApi` contributors declare integration nodes and edges.
3. Resource files below
   `assets/<namespace>/music_control/music_graph/*.json` patch the builder in pack
   priority and resource-id order.
4. The builder validates all parent references, bindings, and cycles.
5. A valid snapshot is published atomically. An invalid reload logs the error and
   retains the previous snapshot.

Omitted fields in a resource patch preserve lower-priority values. A new node has
the single default `{parentMix: exclusive, whenEmpty: vanilla}`. An explicit empty
`parents` array clears inherited parents; `disabled: true` removes the node and its
bindings.

### Runtime activation context

Minecraft and active data packs remain authoritative for choosing the initial
background-music event and timing envelope. `SoundEventRegistry` then builds an
ordered, duplicate-free context:

1. the sound event actually selected by Minecraft, if it is a declared graph node;
2. the node bound to the current dimension;
3. the node bound to the current biome;
4. overworld night, rain, and thunder nodes;
5. riding, driving, and fall-flying nodes.

Each layer resolves against the pool produced by the previous layer. A lightweight
`probe` first determines whether the context leaves Minecraft's selection unchanged,
needs a synthetic override event, or enforces silence. An override retains the
original `Music` timing envelope. Track selection later performs full pool resolution
using the same ordered context.

Dimension activation is deliberately not materialized as parent edges. For example,
the five vanilla Nether biome events do not list `music.nether` as a parent. Instead,
`minecraft:the_nether` activates `music.nether` after the vanilla biome pool. This
covers modded Nether biomes and allows dimension-wide behavior without changing every
child node's inheritance policy.

### Sound pools and portable packs

`MusicCategories.init` rebuilds direct event pools from the active sound manager.
It follows `type: event` references through `EVENTS_OF_EVENT`, preserves explicit
empty replacements, and discovers mod namespaces dynamically. Concrete biome names
and target-specific grouping remain in JSON profiles rather than Java conditionals.

The generated resource pack stores concrete `sounds.json` changes plus the exact
Music Control graph JSON. Portable parent projection copies configured child sounds
into selected native parent events so the pack retains an approximation when the mod
is absent. This projection is a compatibility view; metadata and the graph remain the
logical source of truth.

### Integration API

Other mods register a reload-scoped contributor with `MusicControlApi.register`.
The contributor receives `GraphRegistrar`, whose minimal operations add nodes, add
parent edges, and bind biome or dimension keys. Contributions are replayed for every
resource reload and are applied before resource-pack patches. Duplicate contribution
identifiers, missing nodes, and invalid final graphs fail loudly.

## Audio state machine

### State and orthogonal policy flags

`MusicControlClient.State` describes ownership and fade progress:

| State | Meaning |
| --- | --- |
| `MINECRAFT` | Environmental/event graph playback owns automatic selection. |
| `CUSTOM` | A user-selected track owns playback and ignores environmental replacement. |
| `FADE_OUT` | The current track is ramping from gain 1 to 0 before replacement. |
| `FADE_IN` | The new track is ramping from gain 0 to 1. |

Several values are deliberately orthogonal to this enum:

- `isPaused` freezes fades, countdown, and automatic starts;
- `policySilence` records that the active graph context owns environmental silence;
- `silencedEvent` records silence discovered during full event-pool resolution;
- `currentMusic` is the selected concrete sound identifier;
- `currentEvent` is the current `Music` envelope event;
- `nextSongDelay` remains the countdown used by Minecraft's music manager.

Graph silence owns only default environmental playback. It does not stop `CUSTOM`
playback or a non-default user playlist/category. When an environment has no usable
`Music` envelope, user-owned playback receives a fallback envelope solely for timing;
track selection still comes from the user's category.

### Tick flow

`MusicTrackerMixin` cancels Minecraft's music tick while Music Control owns an active
world and reproduces the necessary stages in a fixed order:

1. Ask Minecraft for situational music. The graph wrapper records the original event,
   timing envelope, activation context, and selection mode.
2. If the graph says `SILENT` and playback is default environmental playback, advance
   policy fade-out, hold the delay at `Integer.MAX_VALUE`, and stop at zero gain.
3. Otherwise leave policy silence, restoring gain and a frequency-derived delay.
4. Advance `FADE_IN` or `FADE_OUT` if configured.
5. Detect a naturally finished sound and obtain the next delay from Minecraft's
   selected `MusicFrequency` policy.
6. Decrement the delay while unpaused and start the next track at zero.
7. Process user key requests at end-of-client-tick through the same start path.

Every start passes through the injected `startPlaying` gate. Selection priority is:

1. explicit GUI track;
2. previous-track navigation;
3. loop current track;
4. graph-resolved default event pool;
5. active non-default category/playlist.

`safeStop` is the only mod-owned stop primitive. It stops the sound, clears the
instance, and dismisses the toast together, preventing split state and duplicate
playback.

### Transitions

```text
MINECRAFT -- incompatible/forced biome change + fade-out --> FADE_OUT
MINECRAFT -- incompatible/forced biome change + fade-in  --> FADE_IN
FADE_OUT  -- gain reaches 0 + fade-in configured         --> FADE_IN
FADE_OUT  -- gain reaches 0 + no fade-in                  --> MINECRAFT
FADE_IN   -- gain reaches 1                               --> MINECRAFT
any       -- explicit track / previous track              --> CUSTOM
CUSTOM    -- world unload                                 --> MINECRAFT
any fade  -- world unload or unexpected sound loss        --> MINECRAFT
```

Pause does not create a new state: it suspends the current transition and countdown.
Changing to a non-default category remains user-owned selection even though its tracks
continue automatically after each delay.

### Biome-switch replacement policy

`BiomeSwitchBehavior` has three values:

- `ALWAYS`: replace whenever the vanilla-event plus ordered graph-context signature changes;
- `IF_INCOMPATIBLE`: resolve the complete next graph context and retain the current
  track if that concrete sound exists in the resulting pool;
- `NEVER`: allow the current track to finish regardless of biome context changes.

The default is `IF_INCOMPATIBLE`, preserving the previous effective behavior while
making unconditional replacement available explicitly. Graph policy silence remains
authoritative for default environmental playback.

### History and no-repeat window

Playback history and randomization memory are separate:

- `PLAYED_MUSICS` is session history used by the History screen and previous-track
  navigation. It stores unique tracks in most-recent order; replaying a track moves
  it to the newest position. Resource reloads and small biome pools do not clear it.
- `RECENT_MUSICS` is a bounded no-repeat window controlled by `musicQueue`. It may be
  shortened to the active pool size and is reset when sound resources are rebuilt.

This separation prevents a one-track biome from erasing the user's visible history.

## Primary mixin boundaries

- `MinecraftClientMixin`: captures Minecraft's background-music selection and applies
  graph activation.
- `MusicTrackerMixin`: owns tick, fades, user priority, track selection, and playback.
- `SoundManagerMixin`: starts graph reload and resets sound-event link parsing.
- `SoundListMixin`: captures concrete files, event links, and explicit empty events.
- `SoundSystemMixin`: rebuilds music categories after sound reload and implements
  pause/resume against active music channels.

When updating Minecraft versions, verify these invocation points and the shadowed
`MusicManager` fields before assuming mappings alone are sufficient.
