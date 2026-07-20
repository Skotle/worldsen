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
               double channelRadius = floorRadius + (double)Math.max(1, Math.min(3, (Integer)EarthShapeServerConfig.RIVER_CHANNEL_EDGE_FADE_BLOCKS.get()));
               if (distance < channelRadius) {
                  double channel = Math.max(-0.16, (Double)EarthShapeServerConfig.RIVER_CHANNEL_CONTINENTALNESS.get());
                  double blend = distance <= floorRadius ? 1.0 : 1.0 - (distance - floorRadius) / Math.max(0.001, channelRadius - floorRadius);
                  blend = blend * blend * (3.0 - 2.0 * blend);
                  continentalness += (channel - continentalness) * blend;
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
