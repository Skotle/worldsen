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

   public double compute(FunctionContext context) {
      if (!EarthShapeCompatibility.disablesWorldgen() && (Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()) {
         if (!RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ())) {
            return 0.0;
         } else {
            double distance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ());
            int widthBlocks = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ());
            if (widthBlocks == 0) {
               return 0.0;
            } else {
               double floorRadius = (double)widthBlocks / 2.0;
               double distanceBlocks = distance * (double)RiversMask.INSTANCE.blocksPerPixel();
               double radius = floorRadius + (double)Math.max(36, (Integer)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get());
               if (distanceBlocks >= radius) {
                  return 0.0;
               } else {
                  double maximumDrop = (double)Math.min(2, (Integer)EarthShapeServerConfig.RIVER_MAXIMUM_DEPTH_BLOCKS.get()) / 64.0;
                  if (distanceBlocks <= floorRadius) {
                     return -maximumDrop;
                  } else {
                     double t = 1.0 - (distanceBlocks - floorRadius) / (radius - floorRadius);
                     return -maximumDrop * t * t * (3.0 - 2.0 * t);
                  }
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
}
