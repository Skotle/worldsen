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
               // Keep water within the source width, while easing the adjacent terrain over
               // a long shoulder so a river crossing relief cannot form a hard-walled gorge.
               double radius = floorRadius + (double)Math.max(48, Math.min(96, (Integer)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get()));
               if (distanceBlocks >= radius) {
                  return 0.0;
               } else {
                  // Keep the actual carved bed to a single additional block.  The
                  // continentalness shoulder handles the broad valley; applying a second
                  // deep density carve here is what made rivers turn into ravines.
                  double maximumDrop = (double)Math.min(1, (Integer)EarthShapeServerConfig.RIVER_MAXIMUM_DEPTH_BLOCKS.get()) / 64.0;
                  if (distanceBlocks <= floorRadius) {
                     // The shoreline starts at the surrounding terrain height and slopes
                     // into the centre of the watercourse instead of dropping vertically.
                     double centreWeight = 1.0 - distanceBlocks / Math.max(1.0, floorRadius);
                     centreWeight = centreWeight * centreWeight * (3.0 - 2.0 * centreWeight);
                     return -maximumDrop * centreWeight;
                  }

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
}
