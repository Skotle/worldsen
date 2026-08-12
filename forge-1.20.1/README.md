# EarthShape Forge 1.20.1

This is the Forge 47.x (Minecraft 1.20.1) port build. It derives the common
world-generation sources from the repository root and applies the small loader
and Minecraft API adaptations during the build, so fixes to the shared code do
not need to be copied between the NeoForge and Forge targets.

Use JDK 17; the included Gradle 8.8 wrapper downloads the matching Gradle version:

```powershell
cd forge-1.20.1
.\gradlew.bat build
```

The Forge jar is written to `forge-1.20.1/build/libs/`.
