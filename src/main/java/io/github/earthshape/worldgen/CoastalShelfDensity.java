package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.ContextProvider;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;

/**
 * Supports a one-block-deep shoreline and lowers that support smoothly into
 * vanilla ocean terrain. It does not place water; the vanilla aquifer does.
 */
public final class CoastalShelfDensity implements DensityFunction {
   // seaLevel=63 means the top water block is Y=62. A one-block-deep
   // standable shoreline therefore needs its solid floor at Y=61.
   private static final int SHORE_FLOOR_Y = 61;
   private static final MapCodec<CoastalShelfDensity> DATA_CODEC = MapCodec.unit(new CoastalShelfDensity());
   public static final KeyDispatchDataCodec<CoastalShelfDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
   private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

   public double compute(FunctionContext context) {
      if (EarthShapeCompatibility.disablesWorldgen()
         || !(Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
         return 0.0;
      }

      int blockX = context.blockX();
      int blockZ = context.blockZ();
      ColumnCache cache = this.columnCache.get();
      if (cache.x != blockX || cache.z != blockZ) {
         double targetFloorY = Double.NaN;
         if (RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) < 0.5) {
            double distance = RiversMask.INSTANCE.waterCoastDistanceBlocks(blockX, blockZ);
            int shelfWidth = (Integer)EarthShapeServerConfig.COAST_SHALLOW_SHELF_WIDTH_BLOCKS.get();
            int transitionWidth = (Integer)EarthShapeServerConfig.COAST_SHELF_TRANSITION_BLOCKS.get();
            if (distance <= (double)shelfWidth) {
               // A strict Y=61 floor gives a full one-block water depth at sea level.
               targetFloorY = SHORE_FLOOR_Y;
            } else if (distance < (double)(shelfWidth + transitionWidth)) {
               double t = (distance - (double)shelfWidth) / (double)transitionWidth;
               t = smoothstep(t);
               int deepFloorY = (Integer)EarthShapeServerConfig.COAST_SHELF_DEEP_FLOOR_Y.get();
               // Preserve the fractional floor throughout the transition. Rounding
               // each column made repeated one-block steps parallel to the bitmap.
               targetFloorY = (double)SHORE_FLOOR_Y + ((double)deepFloorY - (double)SHORE_FLOOR_Y) * t;
            }
         }
         cache.x = blockX;
         cache.z = blockZ;
         cache.targetFloorY = targetFloorY;
      }
      if (Double.isNaN(cache.targetFloorY)) return 0.0;

      double targetFloorY = cache.targetFloorY;
      double y = (double)context.blockY();
      if (y <= targetFloorY) {
         // Fill the seabed up to the selected floor. The upper water cap is handled
         // by ContinentHeightLimitDensity, keeping the shelf exactly one block deep.
         double supportDepth = Math.min(1.0, (targetFloorY - y) / 8.0);
         return 1.20 + 0.50 * smoothstep(supportDepth);
      }
      if (y < 62.0) {
         // Remove solid noise above a descending floor so the 6..22-block transition
         // is a smooth underwater slope instead of a vertical wall.
         double span = Math.max(1.0, 62.0 - targetFloorY);
         return -1.15 * smoothstep((y - targetFloorY) / span);
      }
      return 0.0;
   }

   public void fillArray(double[] values, ContextProvider provider) {
      provider.fillAllDirectly(values, this);
   }

   public DensityFunction mapAll(Visitor visitor) {
      return visitor.apply(this);
   }

   public double minValue() {
      return -1.15;
   }

   public double maxValue() {
      return 2.0;
   }

   public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      return CODEC;
   }

   private static double smoothstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }

   private static final class ColumnCache {
      private int x = Integer.MIN_VALUE;
      private int z = Integer.MIN_VALUE;
      private double targetFloorY = Double.NaN;
   }
}
