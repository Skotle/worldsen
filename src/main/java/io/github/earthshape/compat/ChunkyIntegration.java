package io.github.earthshape.compat;

import io.github.earthshape.EarthShape;
import io.github.earthshape.map.RiversMask;
import net.neoforged.fml.ModList;
import org.popcraft.chunky.shape.Shape;
import org.popcraft.chunky.shape.ShapeFactory;

/** Optional Chunky integration. Loaded only when Chunky is installed. */
public final class ChunkyIntegration {
   private static final String CHUNKY_MOD_ID = "chunky";
   private static final String EARTH_SHAPE = "earth";

   private ChunkyIntegration() {
   }

   public static void registerIfPresent() {
      if (!ModList.get().isLoaded(CHUNKY_MOD_ID)) {
         return;
      }

      // Chunky evaluates Shape#isBounding before requesting a chunk. Registering
      // this shape therefore skips ocean-only chunks instead of generating and
      // discarding them afterwards.
      ShapeFactory.registerCustom(EARTH_SHAPE, (selection, bounding) -> new LandOnlyShape());
      EarthShape.LOGGER.info("[EarthShape] Registered Chunky shape '/chunky shape earth' (land-only pregeneration).");
   }

   private static final class LandOnlyShape implements Shape {
      @Override
      public boolean isBounding(double blockX, double blockZ) {
         int x0 = (int)Math.floor(blockX);
         int z0 = (int)Math.floor(blockZ);
         // Chunky calls this for every candidate. Query the exact source-pixel
         // rectangle in one pass rather than repeating the full coordinate
         // transform up to ten times at common map scales.
         return RiversMask.INSTANCE.intersectsPregenerationChunk(x0, z0);
      }

      @Override
      public String name() {
         return EARTH_SHAPE;
      }
   }
}
