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
         double vanillaContinentalness = this.argument.compute(context);
         // The source image is only a hard land/water authority.  Do not replace the
         // vanilla continentalness field with coast-distance ramps: doing that makes
         // every inland area share one near-zero value and turns whole continents into
         // artificial flat tables.  Retain vanilla's full local variation, clamping
         // only enough to honour the land/ocean mask.
         double continentalness = land >= 0.5
            ? Math.max(0.0, vanillaContinentalness)
            : Math.min(-0.19, vanillaContinentalness);
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
               // Keep the density shoulder close to the water.  A continent-scale fade
               // flattens an entire corridor beside every river and looks artificially
               // excavated from above.
               double channelRadius = floorRadius
                  + (double)Math.max(24, Math.min(56, (Integer)EarthShapeServerConfig.RIVER_HEIGHT_FADE_BLOCKS.get()));
               if (distance < channelRadius) {
                  // A river needs to reach the vanilla water table, not merely select the
                  // RIVER biome.  Keep this independent from an old persisted config value:
                  // a value close to zero leaves only a coloured biome line with dry land.
                  // The channel stays in the normal density pipeline, so no elevated water
                  // is injected above the terrain.
                  // Drive the normal terrain spline into the vanilla valley range at
                  // the painted centreline. The configurable value is bounded to the
                  // shallow river band so an old extreme config cannot create an ocean
                  // trench from a one-pixel source line.
                  double centreChannel = Math.max(
                     -0.20,
                     Math.min(-0.12, (Double)EarthShapeServerConfig.RIVER_CHANNEL_CONTINENTALNESS.get())
                  );
                  double floorWeight = 1.0 - Math.min(1.0, distance / Math.max(1.0, floorRadius));
                  floorWeight = floorWeight * floorWeight * (3.0 - 2.0 * floorWeight);
                  double shoulderWeight = 1.0 - Math.min(1.0, distance / Math.max(1.0, channelRadius));
                  shoulderWeight = shoulderWeight * shoulderWeight * (3.0 - 2.0 * shoulderWeight);
                  // Never raise an already-low coastal value. Inland terrain eases into
                  // the shallow channel target across the full bank instead of flipping
                  // to a negative continentalness value in one source sample.
                  double target = Math.min(continentalness, centreChannel);
                  double influence = floorWeight + (1.0 - floorWeight) * shoulderWeight * 0.30;
                  continentalness += (target - continentalness) * influence;
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

   private static double smoothstep(double value) {
      value = Math.max(0.0, Math.min(1.0, value));
      return value * value * (3.0 - 2.0 * value);
   }

}
