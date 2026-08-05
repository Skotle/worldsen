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
      // C and W already restore the Y=64 land baseline within four blocks. E is
      // kept flat farther out solely to stop a hill from starting beside the
      // channel and then being cut open by it. Scale that recovery by river width
      // but cap it so broad rivers do not erase an entire surrounding region.
      // Ordinary flat terrain needs a longer recovery than the former 12..24
      // blocks. A short E transition can cross the mountain bands and return to
      // flat E again, leaving an isolated ridge running along the riverbank.
      double desiredTransition = Math.min(48.0, Math.max(24.0, (double)width * 0.5 + 24.0));
      double mappedRelief = ClimateLayers.INSTANCE.terrainRelief(blockX, blockZ);
      double explicitMountain = smoothstep(Math.max(0.0, (mappedRelief - 0.35) / 0.65));
      // Preserve mountains explicitly painted beside water. They still begin at
      // the flat bank value, but regain their E field within 8..12 blocks rather
      // than being erased by the ordinary 12..24 block no-hill transition.
      double mountainTransition = Math.min(12.0, Math.max(8.0, (double)width * 0.5 + 6.0));
      desiredTransition = lerp(desiredTransition, mountainTransition, explicitMountain);
      double transitionEnd = Math.max(
         4.0,
         Math.min((double)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get(), desiredTransition)
      );
      double ordinaryFlatEnd = Math.min(3.0, transitionEnd / 3.0);
      double flatEnd = lerp(ordinaryFlatEnd, 0.0, explicitMountain);

      // E=0.55 is vanilla's highly eroded, low/flat domain. Keep it constant
      // through the immediate bank, then recover smoothly before nearby relief.
      // Explicit mapped mountains skip that hold and rise as a continuous slope.
      // This does not lower the land: the separate C floor remains authoritative.
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
