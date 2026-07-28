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

public final class RiverBankGradeDensity implements DensityFunction {
   private static final MapCodec<RiverBankGradeDensity> DATA_CODEC = MapCodec.unit(new RiverBankGradeDensity());
   public static final KeyDispatchDataCodec<RiverBankGradeDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
   private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

   public double compute(FunctionContext context) {
      if (!EarthShapeCompatibility.disablesWorldgen() && (Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()) {
         int blockX = context.blockX();
         int blockZ = context.blockZ();
         ColumnCache cache = this.columnCache.get();
         if (cache.x == blockX && cache.z == blockZ) return cache.value;
         if (!RiversMask.INSTANCE.hasInlandRiverInfluence(blockX, blockZ)) {
            cache.set(blockX, blockZ, 0.0);
            return 0.0;
         } else {
            double distance = RiversMask.INSTANCE.riverCentrelineDistance(blockX, blockZ);
            int widthBlocks = RiversMask.INSTANCE.effectiveRiverWidthBlocks(blockX, blockZ);
            if (widthBlocks == 0) {
               cache.set(blockX, blockZ, 0.0);
               return 0.0;
            } else {
               double floorRadius = (double)widthBlocks / 2.0;
               double distanceBlocks = distance * (double)RiversMask.INSTANCE.blocksPerPixel();
               // Keep water within the source width, while easing the adjacent terrain over
               // a long shoulder so a river crossing relief cannot form a hard-walled gorge.
               double radius = floorRadius + (double)Math.max(48, Math.min(96, (Integer)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get()));
               if (distanceBlocks >= radius) {
                  cache.set(blockX, blockZ, 0.0);
                  return 0.0;
               } else {
                  // Keep the bed shallow, but honour the configured depth.  The old
                  // min(1, ...) reduced every setting to a near-zero nudge, which left
                  // dry channel lines wherever hot terrain had little natural erosion.
                  double maximumDrop = (double)Math.min(7, (Integer)EarthShapeServerConfig.RIVER_MAXIMUM_DEPTH_BLOCKS.get()) / 40.0;
                  if (distanceBlocks <= floorRadius) {
                     // The shoreline starts at the surrounding terrain height and slopes
                     // into the centre of the watercourse instead of dropping vertically.
                     double centreWeight = 1.0 - distanceBlocks / Math.max(1.0, floorRadius);
                     centreWeight = centreWeight * centreWeight * (3.0 - 2.0 * centreWeight);
                     double result = -maximumDrop * centreWeight;
                     cache.set(blockX, blockZ, result);
                     return result;
                  }

                  cache.set(blockX, blockZ, 0.0);
                  return 0.0;
               }
            }
         }
      } else {
         return 0.0;
      }
   }

   public void fillArray(double[] values, ContextProvider provider) {
      provider.fillAllDirectly(values, this);
   }

   public DensityFunction mapAll(Visitor visitor) {
      return visitor.apply(this);
   }

   public double minValue() {
      return -0.5;
   }

   public double maxValue() {
      return 0.0;
   }

   public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      return CODEC;
   }

   private static final class ColumnCache {
      private int x = Integer.MIN_VALUE;
      private int z = Integer.MIN_VALUE;
      private double value;
      void set(int x, int z, double value) {
         this.x = x;
         this.z = z;
         this.value = value;
      }
   }
}
