package io.github.earthshape.compat;

import io.github.earthshape.EarthShape;
import net.fabricmc.loader.api.FabricLoader;

/** Optional Chunky integration. Loaded only when Chunky is installed. */
public final class ChunkyIntegration {
   private static final String CHUNKY_MOD_ID = "chunky";
   private static final String EARTH_SHAPE = "earth";

   private ChunkyIntegration() {
   }

   public static void registerIfPresent() {
      if (!FabricLoader.getInstance().isModLoaded(CHUNKY_MOD_ID)) {
         return;
      }
      EarthShape.LOGGER.info("[EarthShape] Chunky detected; Fabric 26.2 shape registration awaits Chunky's Fabric API.");
   }
}
