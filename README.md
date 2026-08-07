# Music Control

## Description

This mod allows you to take full control of Minecraft music. If you ever felt like you had to wait too much time between
music, or if you ever wanted to skip a music, this mod is made for you.

And it doesn't stop here! You can switch music player mode to play music as before, discs only, resource pack/modded
music only, or any music of the game. You may also replay a music, display its name, and even change music volume
directly with keybinds.

Last but not least, you can completely customize which music plays when with a new GUI *(beta)*. In this music panel,
you can select any music or sound event to play it. You can also configure for each music, in which sound events it can
be played, and vice versa.

## FAQ

- **Do I need it on my server?** No. This mod is client side, so you shouldn't put it on your server

- **Can I include it in my modpack?** Yes, you are free to include this mod into your modpack

- **For any other things:** Feel free to share your experience, problems, enhancement ideas in the Discord server, or
  directly creating issues on the GitHub

## Development

Gradle generates a separate IntelliJ IDEA client and server run configuration for every supported Minecraft target.
All configurations intentionally share the root `run/` directory, while their module, Loom launch file, and Java
runtime remain version-specific. Minecraft 1.21.11 uses Java 21; Minecraft 26.x uses Java 25.

Reloading the Gradle project refreshes these configurations automatically. They can also be rebuilt from a terminal:

```powershell
.\gradlew.bat syncIdeaRunConfigurations
```

The refresh deletes only the generated `Minecraft_Client_*fabric-*.xml` and
`Minecraft_Server_*fabric-*.xml` files before recreating them. This removes configurations belonging to deleted
targets instead of leaving stale entries in IDEA.

When adding or removing a Minecraft target:

1. Add or remove its metadata entry in `build.gradle`.
2. Add or remove the matching `versions/fabric-<minecraft>` project in `settings.gradle` and its source directories.
3. Update the CI and release matrices in `.github/workflows`.
4. Reload the Gradle project, or run `syncIdeaRunConfigurations`.

The aggregate `buildAll` and `checkAll` tasks derive their project lists from `targets`, so they do not need separate
updates when the target list changes.
