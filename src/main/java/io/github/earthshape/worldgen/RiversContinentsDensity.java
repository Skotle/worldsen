package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
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
   private static final ThreadLocal<ColumnCache> COLUMN_CACHE = ThreadLocal.withInitial(ColumnCache::new);

   public double compute(FunctionContext context) {
      if (!EarthShapeCompatibility.disablesWorldgen() && (Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
         int x = context.blockX();
         int z = context.blockZ();
         ColumnCache cache = COLUMN_CACHE.get();
         if (cache.argument == this.argument && cache.x == x && cache.z == z) return cache.value;
         double land = RiversMask.INSTANCE.sampleLayerLand(x, z);
         double vanillaContinentalness = this.argument.compute(context);
         int coastFadeBlocks = (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get();
         double inlandness = land >= 0.5
            ? RiversMask.INSTANCE.sampleCoastInlandness(x, z, coastFadeBlocks)
            : 0.0;
         double continentalness;
         if (land >= 0.5) {
            // Begin in vanilla's coast band and recover the complete seed-driven
            // inland signal over the configured land-side distance.
            double inland = Math.max(0.0, vanillaContinentalness);
            continentalness = lerp(-0.10, inland, inlandness);
         } else {
            // Raise only the submerged near-shore continentalness. Deep water keeps
            // its vanilla variation while the shelf approaches the same coast band.
            double shelf = RiversMask.INSTANCE.sampleWaterShoreProximity(x, z, coastFadeBlocks);
            double ocean = Math.min(-0.19, vanillaContinentalness);
            continentalness = lerp(ocean, -0.12, shelf);
         }
         if (land >= 0.5 && (Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) {
            // Only guide vanilla's terrain spline inputs. The source relief is
            // blurred in ClimateLayers so bitmap edges cannot become cliffs.
            double relief = ClimateLayers.INSTANCE.terrainRelief(x, z);
            double coverage = smoothstep(Math.min(1.0, relief * 2.0)) * inlandness;
            double mountain = smoothstep(Math.max(0.0, (relief - 0.5) * 2.0));
            double target = 0.24 + 0.12 * mountain;
            continentalness = lerp(continentalness, Math.max(continentalness, target), coverage);
         }
         if ((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
            && land > 0.5
            && RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ())) {
            int widthBlocks = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ());
            if (widthBlocks > 0) {
               double floorRadius = (double)widthBlocks / 2.0;
               double distance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ()) * (double)RiversMask.INSTANCE.blocksPerPixel();
               // The river layer guides only the normal terrain-noise inputs inside
               // its painted water core.  Do not flatten a broad artificial plain
               // beside every river; vanilla's own erosion noise shapes the banks.
               if (distance < floorRadius) {
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
                  // Never raise an already-low coastal value. The smooth core weight
                  // feeds the existing terrain spline rather than adding a separate
                  // height/flatness correction around the channel.
                  double target = Math.min(continentalness, centreChannel);
                  continentalness = lerp(continentalness, target, floorWeight);
               }
            }
         }

         cache.set(this.argument, x, z, continentalness);
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

   private static double lerp(double a, double b, double amount) {
      return a + (b - a) * amount;
   }

   private static final class ColumnCache {
      private DensityFunction argument;
      private int x = Integer.MIN_VALUE;
      private int z = Integer.MIN_VALUE;
      private double value;
      void set(DensityFunction argument, int x, int z, double value) {
         this.argument = argument;
         this.x = x;
         this.z = z;
         this.value = value;
      }
   }

}
