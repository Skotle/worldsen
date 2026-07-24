package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.HeightmapLayer;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.ContextProvider;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;

/**
 * Gives source-layer water a shallow floor and grades only the immediate land bank.
 * It never injects water above vanilla sea level; the normal aquifer remains responsible
 * for water placement.
 */
public final class LayerWaterlineDensity implements DensityFunction {
   private static final MapCodec<LayerWaterlineDensity> DATA_CODEC = MapCodec.unit(new LayerWaterlineDensity());
   public static final KeyDispatchDataCodec<LayerWaterlineDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   public double compute(FunctionContext context) {
      if (EarthShapeCompatibility.disablesWorldgen() || !(Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
         return 0.0;
      }

      int x = context.blockX();
      int y = context.blockY();
      int z = context.blockZ();

      if ((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get() && RiversMask.INSTANCE.isInlandRiver(x, z)) {
         return riverGrade(x, y, z);
      }

      // Coastlines are shaped solely by the smooth continentalness field.  Directly
      // clearing density here creates the artificial vertical cut visible at a coast.
      return 0.0;
   }

   private static double riverGrade(int x, int y, int z) {
      int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(x, z);
      if (width <= 0) return 0.0;

      // A painted river crossing a mountain must not turn the whole mountain into a
      // sea-level trench.  Above the highland threshold, retain progressively more of
      // the height-map relief and leave the local vanilla terrain to form the valley.
      double highlandProtection = highlandProtection(x, z);
      if (highlandProtection >= 0.98) return 0.0;
      double carveStrength = 1.0 - highlandProtection;

      double distance = RiversMask.INSTANCE.riverCentrelineDistance(x, z) * (double)RiversMask.INSTANCE.blocksPerPixel();
      double floorRadius = (double)width / 2.0;
      double bankRadius = floorRadius + 96.0;
      double bankWeight = 1.0 - Math.min(1.0, distance / bankRadius);
      bankWeight = bankWeight * bankWeight * (3.0 - 2.0 * bankWeight);
      if (bankWeight <= 0.0) return 0.0;

      // Keep the watercourse shallow. A large vertical cap carve made the density
      // change from normal land to deep water in one block at the source stroke.
      double capY = 63.0 + (1.0 - bankWeight) * 16.0;
      if ((double)y > capY) {
         return -0.35 * carveStrength * bankWeight * Math.min(1.0, ((double)y - capY) / 16.0);
      }
      if (distance <= floorRadius) {
         // A real river cross-section: one block at the shores, then a smooth fall to
         // three blocks at the centre.  A constant deep floor makes the edge drop in one
         // block and reads as an artificial trench.
         double floorWeight = 1.0 - distance / Math.max(1.0, floorRadius);
         floorWeight = floorWeight * floorWeight * (3.0 - 2.0 * floorWeight);
         int bedY = 62 - (int)Math.round(2.0 * floorWeight);
         return y < bedY ? 2.0 * carveStrength : 0.0;
      }
      return 0.0;
   }

   private static double highlandProtection(int x, int z) {
      double median = (Double)EarthShapeServerConfig.HEIGHTMAP_MEDIAN.get();
      double value = (HeightmapLayer.INSTANCE.sample(x, z) - (median + 0.14)) / 0.20;
      value = Math.max(0.0, Math.min(1.0, value));
      return value * value * (3.0 - 2.0 * value);
   }

   public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
   public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
   public double minValue() { return -3.0; }
   public double maxValue() { return 2.0; }
   public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
