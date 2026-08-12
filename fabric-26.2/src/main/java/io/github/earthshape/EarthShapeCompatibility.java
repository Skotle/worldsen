package io.github.earthshape;

import net.fabricmc.loader.api.FabricLoader;

/** Loader-neutral compatibility state used by the common density functions. */
public final class EarthShapeCompatibility {
    private static volatile boolean terralithLoaded;
    private EarthShapeCompatibility() {}
    public static void initialize() {
        terralithLoaded = FabricLoader.getInstance().isModLoaded("terralith");
    }
    public static boolean disablesWorldgen() { return false; }
    public static boolean isTerralithLoaded() { return terralithLoaded; }
    public static boolean isTerraBlenderLoaded() { return FabricLoader.getInstance().isModLoaded("terrablender"); }
    public static boolean isBiomesOPlentyLoaded() { return FabricLoader.getInstance().isModLoaded("biomesoplenty"); }
}
