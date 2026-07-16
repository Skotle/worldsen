package io.github.earthshape;

import net.neoforged.fml.ModList;

/** Fails before a world is opened when known unsafe world-generation combinations are installed. */
final class WorldgenCompatibilityGuard {
    private WorldgenCompatibilityGuard() {}

    static void verify() {
        ModList mods = ModList.get();
        boolean tectonic = mods.isLoaded("tectonic");
        if (tectonic) throw new IllegalStateException("EarthShape blocked Tectonic: EarthShape has no dedicated Tectonic compatibility patch. Remove Tectonic or install a future dedicated compatibility build.");
        if (mods.isLoaded("biolith") && mods.isLoaded("bloomingnature")) {
            EarthShape.LOGGER.info("[EarthShape] Biolith + BloomingNature compatibility enabled: Biolith's resilient feature indexer will resolve BloomingNature feature-order cycles before C2ME workers generate chunks.");
        }
    }
}
