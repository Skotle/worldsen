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

public final class HeightmapOffsetDensity implements DensityFunction {
   private static final MapCodec<HeightmapOffsetDensity> DATA_CODEC = MapCodec.unit(new HeightmapOffsetDensity());
   public static final KeyDispatchDataCodec<HeightmapOffsetDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   public double compute(FunctionContext context) {
      if (!EarthShapeCompatibility.disablesWorldgen() && (Boolean)EarthShapeServerConfig.HEIGHTMAP_ENABLED.get()) {
         double elevation = HeightmapLayer.INSTANCE.sample(context.blockX(), context.blockZ());
         double inland = RiversMask.INSTANCE.sampleHeightmapInlandness(context.blockX(), context.blockZ());
         double riverRelief = RiversMask.INSTANCE.sampleRiverReliefFactor(context.blockX(), context.blockZ());
         double median = (Double)EarthShapeServerConfig.HEIGHTMAP_MEDIAN.get();
         int riverWidth = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ());
         double riverDistance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ())
            * (double)RiversMask.INSTANCE.blocksPerPixel();
         boolean channelCore = RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ())
            && riverWidth > 0
            && riverDistance <= (double)riverWidth / 2.0;
         if (channelCore) {
            // The waterline density owns the source channel. Do not restore mapped
            // mountain lift over its centre, or the terrain fills the river again.
            riverRelief = 0.0;
         } else {
            // Rivers may soften lowland terrain, but preserve the outer mountain bank.
            double highlandProtection = smoothstep((elevation - (median + 0.14)) / 0.20);
            riverRelief = riverRelief + (1.0 - riverRelief) * highlandProtection;
         }
         double deviation = elevation - median;
         double lowland = Math.max(0.0, deviation) * 0.16;
         double mountain = Math.max(0.0, Math.min(1.0, deviation / Math.max(0.01, 1.0 - median)));
         double highlandLift = mountain * mountain * 0.24 + mountain * mountain * mountain * mountain * 0.3;
         return inland * riverRelief * (lowland + highlandLift);
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
      return 0.0;
   }

   public double maxValue() {
      return 0.95;
   }

   public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      return CODEC;
   }

   private static double smoothstep(double value) {
      value = Math.max(0.0, Math.min(1.0, value));
      return value * value * (3.0 - 2.0 * value);
   }
}
