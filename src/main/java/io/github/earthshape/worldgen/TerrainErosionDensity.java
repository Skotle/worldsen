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

/**
 * Guides vanilla's erosion axis without adding height directly. Vanilla's
 * TerrainProvider splines remain solely responsible for the resulting elevation.
 */
public record TerrainErosionDensity(DensityFunction argument) implements DensityFunction {
   private static final MapCodec<TerrainErosionDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
         DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(TerrainErosionDensity::argument)
      ).apply(instance, TerrainErosionDensity::new)
   );
   public static final KeyDispatchDataCodec<TerrainErosionDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   public double compute(FunctionContext context) {
      double vanilla = this.argument.compute(context);
      if (EarthShapeCompatibility.disablesWorldgen()
         || !(Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) {
         return vanilla;
      }

      boolean mappedLand = RiversMask.INSTANCE.sampleLand(context.blockX(), context.blockZ()) >= 0.5;
      double terrainGuided = vanilla;
      if (mappedLand) {
         // terrain.bmp is a land-relief layer. Never carry its mountain erosion
         // into the mapped ocean.
         double relief = ClimateLayers.INSTANCE.terrainRelief(context.blockX(), context.blockZ());
         double coverage = smoothstep(Math.min(1.0, relief * 2.0));
         double mountain = smoothstep(Math.max(0.0, (relief - 0.5) * 2.0));
         double target = -0.55 - 0.30 * mountain;
         terrainGuided = lerp(vanilla, Math.min(vanilla, target), coverage);

         // Blend the desert correction through the pre-smoothed class mask.
         // The old categorical E=0.30 assignment formed an abrupt step at every
         // desert pixel boundary.
         double desertInfluence = ClimateLayers.INSTANCE.desertInfluence(context.blockX(), context.blockZ());
         terrainGuided = lerp(terrainGuided, 0.30, desertInfluence);
      }
      terrainGuided = suppressCoastalHills(context.blockX(), context.blockZ(), terrainGuided);
      return mappedLand
         ? suppressRiverHills(context.blockX(), context.blockZ(), terrainGuided)
         : terrainGuided;
   }

   private static double suppressCoastalHills(int blockX, int blockZ, double terrainGuided) {
      // Use the same low/flat erosion field on both sides of the exact shoreline.
      // This removes the former land E=0.55 / ocean vanilla-E discontinuity while
      // still recovering completely in the deep ocean and continental interior.
      double coastalLandness = RiversMask.INSTANCE.sampleCoastalLandness(blockX, blockZ);
      double shoreDistance = Math.abs(coastalLandness * 2.0 - 1.0);
      double terrainRecovery = smoothstep(shoreDistance);
      // E=0.55 is vanilla's highly eroded low/flat range. Apply it after all
      // terrain-class guidance so mountains, hills, and deserts obey the same
      // waterfront rule.
      return lerp(0.55, terrainGuided, terrainRecovery);
   }

   private static double suppressRiverHills(int blockX, int blockZ, double terrainGuided) {
      if (!(Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
         || !RiversMask.INSTANCE.hasInlandRiverInfluence(blockX, blockZ)) {
         return terrainGuided;
      }

      int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(blockX, blockZ);
      if (width <= 0) return terrainGuided;
      double centreDistance = RiversMask.INSTANCE.riverCentrelineDistance(blockX, blockZ)
         * (double)RiversMask.INSTANCE.blocksPerPixel();
      double bankDistance = Math.max(0.0, centreDistance - (double)width * 0.5);
      double supportedDistance = Math.max(
         4.0,
         (double)RiversMask.INSTANCE.blocksPerPixel() * 3.0 - (double)width * 0.5
      );
      double transitionEnd = Math.max(
         4.0,
         Math.min((double)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get(), supportedDistance)
      );
      double flatEnd = Math.min(24.0, transitionEnd * 0.5);

      // E=0.55 is vanilla's highly eroded, low/flat domain. Keep it constant
      // beside the river, then recover smoothly so no new step is introduced at
      // the edge of the no-hill band.
      if (bankDistance <= flatEnd) return 0.55;
      if (bankDistance >= transitionEnd) return terrainGuided;
      double recovery = smoothstep((bankDistance - flatEnd) / Math.max(1.0, transitionEnd - flatEnd));
      return lerp(0.55, terrainGuided, recovery);
   }

   public void fillArray(double[] values, ContextProvider provider) {
      provider.fillAllDirectly(values, this);
   }

   public DensityFunction mapAll(Visitor visitor) {
      return visitor.apply(new TerrainErosionDensity(this.argument.mapAll(visitor)));
   }

   public double minValue() {
      return -1.0;
   }

   public double maxValue() {
      return this.argument.maxValue();
   }

   public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      return CODEC;
   }

   private static double smoothstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }

   private static double lerp(double a, double b, double amount) {
      return a + (b - a) * amount;
   }
}
