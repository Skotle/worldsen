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
         DensityFunction.CODEC.fieldOf("argument").forGetter(TerrainErosionDensity::argument)
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
         // Terralith has several large offset/factor steps between E=-0.55 and
         // E=-0.85. Letting the distance-based mountain relief cross all of
         // them produces concentric plateaus whose centre can approach Y=200.
         // Stay inside one mountain band when Terralith owns the final density;
         // vanilla keeps the full erosion range.
         double mountainErosionSpan = EarthShapeCompatibility.isTerralithLoaded() ? 0.03 : 0.30;
         double target = -0.55 - mountainErosionSpan * mountain;
         terrainGuided = lerp(vanilla, Math.min(vanilla, target), coverage);
         if (EarthShapeCompatibility.isTerralithLoaded()) {
            // Math.min above deliberately preserves a more mountainous vanilla
            // sample. Under Terralith that can still fall through to E=-1 and
            // re-enter the extreme spline, so raise the lower bound gradually
            // with mapped mountain coverage instead of clipping its boundary.
            double safeLowerBound = lerp(-1.0, -0.60, coverage);
            terrainGuided = Math.max(safeLowerBound, terrainGuided);
         }

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
      // Hold the high-erosion waterfront field longer before recovering. The
      // former single smoothstep was already close to the full mountain E value
      // halfway through the coastal blend, leaving a steep inner cut face.
      double terrainRecovery = smoothstep(shoreDistance);
      terrainRecovery *= terrainRecovery;
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
      // Match C's complete height-recovery span. Ending E at bankFadeBlocks
      // restored hill and mountain erosion long before the continental base had
      // finished rising, which concentrated the remaining rise into a wall.
      double transitionEnd = RiverTerrainTransition.distance(blockX, blockZ, width);
      if (bankDistance >= transitionEnd) return terrainGuided;
      // Never replace the bank with constant E=0.55. Keep 55..85% of its
      // original erosion noise at the water edge, then return it to 100%.
      double recovery = RiverTerrainTransition.recovery(blockX, blockZ, bankDistance, transitionEnd);
      return lerp(0.55, terrainGuided, recovery);
   }

   public void fillArray(double[] values, ContextProvider provider) {
      provider.fillAllDirectly(values, this);
   }

   public DensityFunction mapChildren(Visitor visitor) {
      return new TerrainErosionDensity(visitor.apply(this.argument));
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
