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

   public double compute(FunctionContext context) {
      if (EarthShapeCompatibility.disablesWorldgen()
         || !(Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
         return 0.0;
      }

      int blockX = context.blockX();
      int blockZ = context.blockZ();
      if (RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) < 0.5) {
         // seaLevel=63 places the top water block at Y=62. Keeping the highest
         // possible ocean solid at Y=61 preserves at least one full water block.
         return context.blockY() >= 62 ? ABOVE_LIMIT_PENALTY : 0.0;
      }

      if (!(Boolean)EarthShapeServerConfig.CONTINENT_HEIGHT_LIMIT_ENABLED.get()) return 0.0;

      int maximumSurfaceY = RiversMask.INSTANCE.continentMaximumSurfaceY(blockX, blockZ);
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
      return context.blockY() > maximumSurfaceY ? ABOVE_LIMIT_PENALTY : 0.0;
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
}
