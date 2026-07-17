package io.github.earthshape;

import java.util.LinkedHashSet;
import java.util.Set;
import net.neoforged.fml.ModList;

/** Prevents two incompatible Overworld router owners from silently corrupting generation. */
public final class EarthShapeCompatibility {
    // Climate Rivers owns the same source-river stage.  Other biome additions are preserved
    // by TerrainBiomeMixin instead of being treated as a global worldgen conflict.
    private static final Set<String> HARD_WORLDGEN_MODS = Set.of("climate_rivers", "climaterivers");
    private static volatile Set<String> conflicts = Set.of();
    private static volatile boolean terralithLoaded;

    private EarthShapeCompatibility() {}

    public static void initialize() {
        terralithLoaded = ModList.get().isLoaded("terralith");
        Set<String> found = new LinkedHashSet<>();
        for (String modId : HARD_WORLDGEN_MODS) if (ModList.get().isLoaded(modId)) found.add(modId);
        conflicts = Set.copyOf(found);
        if (!conflicts.isEmpty()) {
            EarthShape.LOGGER.error("[EarthShape] incompatible world-generation mods detected: {}. EarthShape worldgen is disabled; see COMPATIBILITY.md.", conflicts);
        }
        if (terralithLoaded) {
            EarthShape.LOGGER.info("[EarthShape] Terralith detected: retaining Terralith-selected biomes and features; EarthShape only supplies map shape and density relief.");
        }
    }

    public static boolean disablesWorldgen() { return !conflicts.isEmpty(); }

    public static boolean isTerralithLoaded() { return terralithLoaded; }
}
