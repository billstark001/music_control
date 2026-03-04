# Music Control – Metadata Format

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
    └── minecraft/
        └── sounds.json       ← full override (generated from metadata + vanilla)
```

---

## Schema

```jsonc
{
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
  }
}
```

### Field reference

| Field                        | Type            | Description |
|------------------------------|-----------------|-------------|
| `minecraft_version`          | string          | The game version when this metadata was saved. |
| `changes`                    | object          | Map of sound-event IDs to their change records. Keys are sorted alphabetically. Events identical to vanilla are omitted. |
| `changes.<id>.remove`        | string[]        | Sound file identifiers that were in vanilla but removed by the user. Sorted alphabetically. |
| `changes.<id>.add`           | string[]        | Sound file identifiers that were not in vanilla but added by the user. Sorted alphabetically. |

Both `"remove"` and `"add"` are optional; an entry only includes the keys that are non-empty.

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
