/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.neoforged.fml.ModList
 */
package io.github.earthshape;

import io.github.earthshape.EarthShape;
import java.util.LinkedHashSet;
import java.util.Set;
import net.neoforged.fml.ModList;

public final class EarthShapeCompatibility {
    private static final Set<String> HARD_WORLDGEN_MODS = Set.of("climate_rivers", "climaterivers");
    private static volatile Set<String> conflicts = Set.of();
    private static volatile boolean terralithLoaded;
    private static volatile boolean terraBlenderLoaded;

    private EarthShapeCompatibility() {
    }

    public static void initialize() {
        terralithLoaded = ModList.get().isLoaded("terralith");
        terraBlenderLoaded = ModList.get().isLoaded("terrablender");
        LinkedHashSet<String> found = new LinkedHashSet<String>();
        for (String modId : HARD_WORLDGEN_MODS) {
            if (!ModList.get().isLoaded(modId)) continue;
            found.add(modId);
        }
        conflicts = Set.copyOf(found);
        if (!conflicts.isEmpty()) {
            EarthShape.LOGGER.error("[EarthShape] incompatible world-generation mods detected: {}. EarthShape worldgen is disabled; see COMPATIBILITY.md.", conflicts);
        }
        if (terralithLoaded) {
            EarthShape.LOGGER.info("[EarthShape] Terralith detected: retaining Terralith-selected biomes and features; EarthShape only supplies map shape and density relief.");
        }
        if (terraBlenderLoaded) {
            EarthShape.LOGGER.info("[EarthShape] TerraBlender detected: EarthShape will intercept its MultiNoise biome lookup before the TerraBlender region tree runs.");
        }
    }

    public static boolean disablesWorldgen() {
        return !conflicts.isEmpty();
    }

    public static boolean isTerralithLoaded() {
        return terralithLoaded;
    }

    public static boolean isTerraBlenderLoaded() {
        if (terraBlenderLoaded) {
            return true;
        }
        boolean detected = ModList.get().isLoaded("terrablender");
        if (detected) {
            terraBlenderLoaded = true;
        }
        return detected;
    }
}

