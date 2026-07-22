package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.ContextProvider;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;

public record RiversContinentsDensity(DensityFunction argument) implements DensityFunction {
   private static final MapCodec<RiversContinentsDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(RiversContinentsDensity::argument)).apply(i, RiversContinentsDensity::new)
   );
   public static final KeyDispatchDataCodec<RiversContinentsDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   public double compute(FunctionContext context) {
      if (!EarthShapeCompatibility.disablesWorldgen() && (Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
         double land = RiversMask.INSTANCE.sampleLayerLand(context.blockX(), context.blockZ());
         double softenedLand = land * land * (3.0 - 2.0 * land);
         double continentalness = -0.65 + softenedLand * 0.85;
         if ((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
            && land > 0.5
            && RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ())) {
            int widthBlocks = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ());
            if (widthBlocks > 0) {
               double floorRadius = (double)Math.max(4, widthBlocks) / 2.0;
               double distance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ()) * (double)RiversMask.INSTANCE.blocksPerPixel();
               // Water remains limited to the painted width; this controls only the terrain
               // grade beside it.  A broad continuous transition prevents the source mask
               // boundary from becoming a vertical canyon wall.
               double channelRadius = floorRadius
                  + (double)Math.max(24, Math.min(48, (Integer)EarthShapeServerConfig.RIVER_CHANNEL_EDGE_FADE_BLOCKS.get()));
               if (distance < channelRadius) {
                  // A river needs to reach the vanilla water table, not merely select the
                  // RIVER biome.  Keep this independent from an old persisted config value:
                  // a value close to zero leaves only a coloured biome line with dry land.
                  // The channel stays in the normal density pipeline, so no elevated water
                  // is injected above the terrain.
                  double centreChannel = -0.42;
                  double floorWeight = 1.0 - Math.min(1.0, distance / Math.max(1.0, floorRadius));
                  floorWeight = floorWeight * floorWeight * (3.0 - 2.0 * floorWeight);
                  double shoulderWeight = 1.0 - Math.min(1.0, distance / Math.max(1.0, channelRadius));
                  shoulderWeight = shoulderWeight * shoulderWeight * (3.0 - 2.0 * shoulderWeight);
                  // At the painted edge the target is ordinary lowland, then the shoulder
                  // blends back into the surrounding continentalness without a hard wall.
                  double target = 0.02 + (centreChannel - 0.02) * floorWeight;
                  continentalness += (target - continentalness) * shoulderWeight;
               }
            }
         }

         return continentalness;
      } else {
         return this.argument.compute(context);
      }
   }

   public void fillArray(double[] values, ContextProvider provider) {
      provider.fillAllDirectly(values, this);
   }

   public DensityFunction mapAll(Visitor visitor) {
      return visitor.apply(new RiversContinentsDensity(this.argument.mapAll(visitor)));
   }

   public double minValue() {
      return -0.8;
   }

   public double maxValue() {
      return 0.2;
   }

   public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      return CODEC;
   }
}
