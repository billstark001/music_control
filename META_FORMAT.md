# Music Control – Metadata Format

For the runtime graph algorithm, activation order, audio state machine, and dependency
packaging, see [DEVELOPER_ARCHITECTURE.md](DEVELOPER_ARCHITECTURE.md).

`music_control_meta.json` is saved alongside `sounds.json` in the resource pack directory
whenever the user saves their music configuration.  It records **only the changes** the
user made relative to the vanilla (unmodified) Minecraft sound list, making it easy to
migrate a personalised configuration to a new Minecraft version.

---

## Location

```
<resource-pack-root>/
├── pack.mcmeta
├── pack.png
├── music_control_meta.json   ← incremental metadata
└── assets/
    ├── minecraft/
    │   └── sounds.json       ← full override (generated from metadata + vanilla)
    └── music_control/
        └── music_control/music_graph/user.json  ← mod routing graph (schema v1)
```

---

## Schema

```jsonc
{
  // First public metadata format.
  "format_version": 1,

  // The Minecraft version the metadata was generated against.
  "minecraft_version": "1.21.9",

  // Only events that differ from vanilla are listed.
  // Keys are sorted alphabetically.
  "changes": {

    // Key = full sound-event identifier  (namespace:path)
    "minecraft:music.game": {

      // Sounds present in vanilla but REMOVED by the user.
      // Array is sorted alphabetically.
      "remove": [
        "minecraft:music/game/sweden"
      ],

      // Sounds NOT in vanilla but ADDED by the user.
      // Array is sorted alphabetically.
      "add": [
        "minecraft:music/game/cat"
      ]
    }
  },

  // Events deliberately defined as replace:true with no sounds.
  "silent_events": [
    "example:music.silent_region"
  ],

  // Exact event-to-event references (not physical sound files).
  "event_links": {
    "minecraft:music.game": [
      "example:music.shared"
    ]
  }
}
```

### Field reference

| Field                        | Type            | Description |
|------------------------------|-----------------|-------------|
| `format_version`             | integer         | Metadata schema version. The current and first public format is `1`. |
| `minecraft_version`          | string          | The game version when this metadata was saved. |
| `changes`                    | object          | Map of sound-event IDs to their change records. Keys are sorted alphabetically. Events identical to vanilla are omitted. |
| `changes.<id>.remove`        | string[]        | Sound file identifiers that were in vanilla but removed by the user. Sorted alphabetically. |
| `changes.<id>.add`           | string[]        | Sound file identifiers that were not in vanilla but added by the user. Sorted alphabetically. |
| `silent_events`              | string[]        | Events intentionally kept empty with `replace: true`; distinct from an unconfigured synthetic event. |
| `event_links`                | object          | Exact map of event IDs to referenced event IDs used by `type: "event"` entries. |

Both `"remove"` and `"add"` are optional; an entry only includes the keys that are non-empty.

---

## Music graph (schema v1)

The client-resource JSON files below `assets/<namespace>/music_control/music_graph/*.json`
patch the runtime event DAG. Files from higher-priority resource packs are applied after
the built-in version profile. Omitted node fields inherit their current values; the one
implicit definition for a new node is `parentMix: "exclusive"` plus
`whenEmpty: "vanilla"`.

```jsonc
{
  "schemaVersion": 1,
  "nodes": {
    "example:music.deep_dark": {
      "parentMix": "exclusive", // exclusive, half, proportional, parents_only
      "whenEmpty": "silent",    // vanilla, silent, parents
      "parents": ["minecraft:music.game"]
    }
  },
  "biomes": {
    "minecraft:deep_dark": "example:music.deep_dark",
    "example:unmanaged_biome": null // remove a lower-priority binding
  },
  "dimensions": {
    "example:dimension": "example:music.dimension"
  },
  "hiddenEvents": ["example:music.internal"],
  "visibleEvents": ["minecraft:music.overworld.old_growth_taiga"]
}
```

`disabled: true` removes a node and its biome/dimension bindings. An explicitly present
empty `parents` array clears inherited parent edges. The graph is accepted atomically:
unknown parents, unknown binding targets, cycles, invalid identifiers, or another schema
version reject the complete reload and leave the previous snapshot active.

The two node properties are orthogonal. `parentMix` controls a non-empty direct pool;
`whenEmpty` controls an empty direct pool. `vanilla` preserves the music selected by the
active vanilla/data-pack biome attributes, including intentional vanilla silence. Thus
the built-in profile does not hard-code Deep Dark silence, while a resource pack can opt
into it with a biome binding and `whenEmpty: "silent"`.

---

## Migration guide

When upgrading to a new Minecraft version:

1. The mod loads the new vanilla `sounds.json`.
2. It applies the user's `changes`:
    - Remove every sound listed under `"remove"` from the corresponding event.
    - Add every sound listed under `"add"` to the corresponding event.
3. New vanilla sounds (not in `"remove"`) are automatically included — no manual
   reconfiguration required.
4. The updated `sounds.json` and a refreshed `music_control_meta.json` are written.

The generated `sounds.json` also projects configured fine-grained biome events onto
their native parent events. This is a compatibility view for using the resource pack
without Music Control. The metadata remains the logical, pre-projection source of truth,
so repeated loads and saves do not make projected child tracks stick to the parent.
