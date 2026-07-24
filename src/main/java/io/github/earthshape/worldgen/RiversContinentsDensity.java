package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.HeightmapLayer;
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
         // Keep the land mask authoritative.  Averaging it on both sides of a shore can
         // raise a narrow strait or an inland sea to land level and close it entirely.
         double softenedLand = land * land * (3.0 - 2.0 * land);
         double continentalness = -0.65 + softenedLand * 0.85;
         if (land >= 0.5) {
            // Both sides of every mapped shore meet at the same water-safe coast value.
            // Keeping separate land/ocean anchor values here creates a density step at
            // every shoreline, which is visible as an artificial coastal wall.
            double inlandness = RiversMask.INSTANCE.sampleCoastInlandness(
               context.blockX(), context.blockZ(), Math.max(480, (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get())
            );
            // -0.20 stays on the ocean side of vanilla's land threshold. Land then
            // rises gradually to the normal near-inland value rather than receiving a
            // separate coast carve.
            continentalness = -0.20 + 0.23 * inlandness;
         } else {
            // Use the matching water-side distance field. At the shoreline this is
            // exactly the land-side -0.20; only farther offshore does it descend into
            // deep-ocean continentalness.
            double shore = RiversMask.INSTANCE.sampleWaterShoreProximity(
               context.blockX(), context.blockZ(), Math.max(480, (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get())
            );
            continentalness = -0.65 + 0.45 * shore;
         }
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
                  + (double)Math.max(96, Math.min(160, (Integer)EarthShapeServerConfig.RIVER_CHANNEL_EDGE_FADE_BLOCKS.get()));
               if (distance < channelRadius) {
                  // A river needs to reach the vanilla water table, not merely select the
                  // RIVER biome.  Keep this independent from an old persisted config value:
                  // a value close to zero leaves only a coloured biome line with dry land.
                  // The channel stays in the normal density pipeline, so no elevated water
                  // is injected above the terrain.
                  // Keep the channel in the shallow river/near-coast range. The old
                  // -0.18 target pushed ordinary inland terrain straight into an ocean
                  // value at the first river pixel, producing a deep trench.
                  double centreChannel = -0.12;
                  double floorWeight = 1.0 - Math.min(1.0, distance / Math.max(1.0, floorRadius));
                  floorWeight = floorWeight * floorWeight * (3.0 - 2.0 * floorWeight);
                  double shoulderWeight = 1.0 - Math.min(1.0, distance / Math.max(1.0, channelRadius));
                  shoulderWeight = shoulderWeight * shoulderWeight * (3.0 - 2.0 * shoulderWeight);
                  // Do not force a high mountain down to the river density target.  The
                  // suppression is gradual, so ordinary hills still receive a readable
                  // riverbed while genuine mountain terrain keeps its outer profile.
                  double median = (Double)EarthShapeServerConfig.HEIGHTMAP_MEDIAN.get();
                  double highlandProtection = smoothstep((HeightmapLayer.INSTANCE.sample(context.blockX(), context.blockZ()) - (median + 0.14)) / 0.20);
                  shoulderWeight *= 1.0 - highlandProtection;
                  // Never raise an already-low coastal value. Inland terrain eases into
                  // the shallow channel target across the full bank instead of flipping
                  // to a negative continentalness value in one source sample.
                  double target = Math.min(continentalness, centreChannel);
                  double influence = shoulderWeight * (0.30 + 0.70 * floorWeight);
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
