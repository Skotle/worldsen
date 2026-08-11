# EarthShape safe server start

NeoForge resolves mod files and required dependencies before EarthShape itself
is constructed. Consequently, no normal mod can unload another mod inside the
same JVM after that mod has crashed. These wrappers implement the safe version:
after a **mod-constructor** crash, identify its mod ID, move only that JAR out of
`mods`, and restart the server in a clean JVM.

Linux:

```bash
chmod +x earthshape-safe-start.sh
./earthshape-safe-start.sh ./run.sh nogui
```

Windows:

```powershell
.\earthshape-safe-start.ps1 -CommandLine '.\run.bat nogui'
```

Isolated files are retained in `mods-disabled-by-earthshape`; they are not
deleted. `minecraft`, `neoforge`, and `earthshape` are protected and are never
isolated. The retry limit defaults to five and can be changed with
`EARTHSHAPE_MAX_CRASH_RETRIES` on Linux or `-MaximumCrashRetries` on Windows.

Discovery errors, missing required dependencies, duplicate mod IDs, mixin
bootstrap failures, and JVM/native crashes happen too early to identify and
unload a safe optional mod. The wrapper stops without moving anything in those
cases.
