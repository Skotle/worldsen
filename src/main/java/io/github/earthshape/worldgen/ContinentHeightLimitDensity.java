package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.ContextProvider;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;

/**
 * Leaves all terrain elevation to vanilla, prevents mapped ocean shelves from
 * becoming land, and rejects land above its connected-continent ceiling.
 */
public final class ContinentHeightLimitDensity implements DensityFunction {
   private static final double ABOVE_LIMIT_PENALTY = -8.0;
   private static final MapCodec<ContinentHeightLimitDensity> DATA_CODEC = MapCodec.unit(new ContinentHeightLimitDensity());
   public static final KeyDispatchDataCodec<ContinentHeightLimitDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
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
         boolean ocean = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) < 0.5;
         int maximumSurfaceY;
         if (ocean) {
            // seaLevel=63 places the top water block at Y=62. Keeping the highest
            // possible ocean solid at Y=61 preserves at least one full water block.
            maximumSurfaceY = 61;
         } else if (!(Boolean)EarthShapeServerConfig.CONTINENT_HEIGHT_LIMIT_ENABLED.get()) {
            maximumSurfaceY = Integer.MAX_VALUE;
         } else {
            maximumSurfaceY = RiversMask.INSTANCE.continentMaximumSurfaceY(blockX, blockZ);
            double mountainScale = ClimateLayers.INSTANCE.mountainRegionHeightScale(blockX, blockZ);
            if (mountainScale >= 0.0) {
               int minimumMountainY = Math.min(
                  maximumSurfaceY,
                  (Integer)EarthShapeServerConfig.MOUNTAIN_REGION_MINIMUM_SURFACE_Y.get()
               );
               int mountainMaximumY = (int)Math.round(
                  (double)minimumMountainY + (double)(maximumSurfaceY - minimumMountainY) * mountainScale
               );
               maximumSurfaceY = Math.min(maximumSurfaceY, mountainMaximumY);
            }
         }
         cache.set(blockX, blockZ, maximumSurfaceY, ocean);
      }
      int y = context.blockY();
      if (cache.ocean) {
         // Keep the mapped sea below the aquifer waterline. This is deliberately
         // separate from the land cap so a land-height fade cannot hollow shelves.
         return y > cache.maximumSurfaceY ? ABOVE_LIMIT_PENALTY : 0.0;
      }
      if (cache.maximumSurfaceY == Integer.MAX_VALUE) return 0.0;

      // Do not turn a single map-cell height limit into a perfectly flat, vertical
      // cut. Start easing the density before the ceiling and reach the full guard
      // above it, allowing vanilla's erosion spline to form a rounded summit.
      double fade = smoothstep(((double)y - ((double)cache.maximumSurfaceY - 8.0)) / 16.0);
      return ABOVE_LIMIT_PENALTY * fade;
   }

   public void fillArray(double[] values, ContextProvider provider) {
      provider.fillAllDirectly(values, this);
   }

   public DensityFunction mapAll(Visitor visitor) {
      return visitor.apply(this);
   }

   public double minValue() {
      return ABOVE_LIMIT_PENALTY;
   }

   public double maxValue() {
      return 0.0;
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
      private int maximumSurfaceY;
      private boolean ocean;

      void set(int x, int z, int maximumSurfaceY, boolean ocean) {
         this.x = x;
         this.z = z;
         this.maximumSurfaceY = maximumSurfaceY;
         this.ocean = ocean;
      }
   }
}
