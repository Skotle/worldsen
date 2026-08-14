package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Smoothly maps the coastline layer onto vanilla continentalness, never onto a fixed Y level. */
public record CoastalContinentalnessDensity(DensityFunction argument) implements DensityFunction {
   /** Fraction of the configured channel width occupied by the level bed core. */
   private static final double RIVER_BED_CORE_RATIO = 0.60;
   /** One vanilla continentalness step used to lift every dry mapped land column. */
   private static final double LAND_SURFACE_LIFT = 0.01;
   private static final MapCodec<CoastalContinentalnessDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(CoastalContinentalnessDensity::argument))
         .apply(instance, CoastalContinentalnessDensity::new)
   );
   public static final KeyDispatchDataCodec<CoastalContinentalnessDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   @Override
   public double compute(FunctionContext context) {
      double vanilla = this.argument.compute(context);
      if (EarthShapeCompatibility.disablesWorldgen() || !(Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) return vanilla;
      double coastalLandness = RiversMask.INSTANCE.sampleCoastalLandness(context.blockX(), context.blockZ());
      double t = coastalLandness;
      t = t * t * (3.0 - 2.0 * t);
      boolean mappedLand = RiversMask.INSTANCE.sampleLand(context.blockX(), context.blockZ()) >= 0.5;
      // Inland-river handling intentionally stops at a mouth so the biome can
      // become ocean. Keep that same mouth physically open in the C-noise field.
      double mouthOpening = RiversMask.INSTANCE.riverMouthOpening(context.blockX(), context.blockZ());
      t *= 1.0 - mouthOpening;
      // The mask establishes continental domain, while vanilla C keeps the
      // ordinary lowland variation. Keep a strong mask near the shore, then
      // progressively return control to vanilla inland instead of imposing a
      // separate EarthShape height-noise field.
      double maskTarget = -0.85 + 1.20 * t;
      double inlandVanillaWeight = mappedLand ? lerp(0.20, 0.55, smootherstep((t - 0.55) / 0.35)) : 0.20;
      double guided = vanilla * inlandVanillaWeight + maskTarget * (1.0 - inlandVanillaWeight);
      // Keep the exact source mask authoritative without the old 0.95/0.05
      // t-clamp. Those values jumped C by 0.864 in one column. A narrow pair of
      // side limits preserves land/ocean classification while limiting the C
      // seam to 0.10, which the vanilla splines can resolve as an ordinary bank.
      if (mappedLand && mouthOpening <= 0.001) {
         guided = Math.max(-0.10, guided);
      } else if (!mappedLand) {
         guided = oceanShelfContinentalness(context.blockX(), context.blockZ(), vanilla);
      }
      double riverInfluence = riverValleyInfluence(context.blockX(), context.blockZ());
      if (riverInfluence > 0.0) {
         guided = applyRiverCrossSection(context.blockX(), context.blockZ(), guided);
      }
      // Keep broad deserts near one inland elevation. A small remainder of the
      // original C field is retained so large W/PV hills still have a natural
      // base, while local continental bumps can no longer build mountains.
      double desertInfluence = ClimateLayers.INSTANCE.desertInfluence(context.blockX(), context.blockZ());
      double inlandFactor = smoothstep((t - 0.82) / 0.16);
      if (desertInfluence > 0.0 && riverInfluence <= 0.0) {
         // Desert remains a terrain family selection; do not flatten its whole
         // mapped area to one continentalness value.
         guided = lerp(guided, 0.10, 0.55 * desertInfluence * inlandFactor);
      }
      // Every mapped land column outside the physical riverbank starts at the
      // normal overworld Y=64 baseline. The channel, its direct bank and a
      // genuine mouth remain free to descend to their water profile.
      boolean riverWater = RiversMask.INSTANCE.isInlandRiverColumn(context.blockX(), context.blockZ());
      ClimateLayers.TerrainKind terrainKind = ClimateLayers.INSTANCE.terrainKind(context.blockX(), context.blockZ());
      boolean riverBank = RiversMask.INSTANCE.isInlandRiverBank(context.blockX(), context.blockZ());
      double surfaceBankDistance = RiversMask.INSTANCE.surfaceBankDistanceBlocks(context.blockX(), context.blockZ());
      int surfaceBankWidth = surfaceBankDistance > 0.0
         ? RiversMask.INSTANCE.surfaceBankWidthBlocks(context.blockX(), context.blockZ())
         : 0;
      boolean lowSurfaceBank = surfaceBankDistance > 0.0 && surfaceBankDistance <= (double)surfaceBankWidth;
      // Do not cut a three-block shelf through an existing hill or mountain.
      // Only already-low terrain receives the climbable water-level profile.
      boolean waterLevelBank = lowSurfaceBank && guided <= 0.16;
      boolean smallIsland = RiversMask.INSTANCE.isSmallIsland(context.blockX(), context.blockZ());
      // Hills and mountains start at least one terrain band above the ordinary
      // Y=64 floor. C=0.12 supplies roughly five blocks of vanilla spline lift
      // without writing a fixed surface Y or bypassing erosion/PV variation.
      double minimumLandContinentalness =
         !smallIsland && (terrainKind == ClimateLayers.TerrainKind.HILLS || terrainKind == ClimateLayers.TerrainKind.MOUNTAIN)
            ? 0.12
            : 0.06;
      if (mappedLand && !riverBank && !riverWater && !waterLevelBank && mouthOpening <= 0.001) {
         guided = Math.max(minimumLandContinentalness, guided);
      }
      if (mappedLand && !riverWater && waterLevelBank && mouthOpening <= 0.001) {
         // Begin with a water-level landing edge and recover over only three
         // blocks. The final value meets the ordinary land floor gradually,
         // avoiding both an un-climbable wall and a wide shoreline terrace.
         double recovery = smoothstep(
            (surfaceBankDistance - 1.0) / Math.max(1.0, (double)surfaceBankWidth - 1.0)
         );
         double bankTarget = lerp(-0.08, 0.02, recovery);
         guided = Math.min(guided, bankTarget);
      }
      if (mappedLand && mouthOpening <= 0.001) {
         guided = applyRiverBankHeightLimit(
            context.blockX(), context.blockZ(), guided,
            riverBank || riverWater || waterLevelBank ? -0.10 : minimumLandContinentalness
         );
      }
      // Land/ocean classification changes at one exact contour, but its height
      // signal must not recover at that same step. Start dry land at its normal
      // minimum and return the guided C field with a zero-slope curve across the
      // already blurred coast field. This prevents a narrow continentalness
      // ridge from following otherwise flat shorelines.
      if (mappedLand && !riverBank && !riverWater && !waterLevelBank && mouthOpening <= 0.001) {
         double inlandRecovery = smootherstep((coastalLandness - 0.48) / 0.44);
         guided = Math.max(
            minimumLandContinentalness,
            lerp(minimumLandContinentalness, guided, inlandRecovery)
         );
      }
      // Apply the requested one-block baseline lift after all terrain and bank
      // guidance so it affects plains, slopes and peaks uniformly. Actual river
      // water and mouths retain the sea-level water profile.
      if (mappedLand && !riverWater && !waterLevelBank && mouthOpening <= 0.001) {
         guided = Math.min(1.0, guided + LAND_SURFACE_LIFT);
      }
      // Small islands must not inherit a continent-scale C peak. Their dry
      // surface still obeys the raised Y=65 baseline, but additional elevation
      // is left to the reduced vanilla hill signal above.
      if (smallIsland && mappedLand && !riverWater && mouthOpening <= 0.001) {
         guided = Math.min(0.14, guided);
      }
      // Terralith's replacement offset/factor splines become amplified-world
      // terrain above the ordinary mid-inland C range (their values rise as
      // high as 1.5 and 20). Keep EarthShape's mapped relief, E and W control,
      // but prevent a harmless retained vanilla C lobe from entering that
      // extreme branch. Mountains receive a little more headroom than plains.
      if (EarthShapeCompatibility.isTerralithLoaded()
         && mappedLand && !riverWater && mouthOpening <= 0.001) {
         double relief = ClimateLayers.INSTANCE.terrainRelief(context.blockX(), context.blockZ());
         // Remain below Terralith's C=0.20 amplified factor branch even at the
         // centre of a mapped mountain. E/W still supply ordinary relief.
         double terralithCap = 0.10 + 0.06 * smoothstep(relief);
         guided = Math.min(terralithCap, guided);
      }
      return guided;
   }

   private static double oceanShelfContinentalness(int blockX, int blockZ, double vanilla) {
      double distance = RiversMask.INSTANCE.oceanDistanceBlocks(blockX, blockZ);
      double shallowWidth = (double)EarthShapeServerConfig.COAST_SHALLOW_SHELF_WIDTH_BLOCKS.get();
      double initialTransition = (double)EarthShapeServerConfig.COAST_SHELF_TRANSITION_BLOCKS.get();
      double fullTransition = Math.max(
         shallowWidth + initialTransition,
         (double)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get()
            * RiversMask.INSTANCE.coastShelfFadeScale(blockX, blockZ)
      );

      // C=-0.20 is already on the ocean side of vanilla's coast threshold.
      // Start just below it, then descend toward a configurable deep-ocean
      // target.  The zero-slope smoothstep removes the cut face at both ends.
      double progress = smoothstep((distance - shallowWidth) / Math.max(1.0, fullTransition - shallowWidth));
      int deepFloorY = (Integer)EarthShapeServerConfig.COAST_SHELF_DEEP_FLOOR_Y.get();
      double deepTarget = Math.max(-0.85, Math.min(-0.30, -0.30 - (61.0 - (double)deepFloorY) * 0.025));
      double shelfTarget = lerp(-0.21, deepTarget, progress);

      // Reintroduce only a small, gradually increasing share of vanilla noise
      // offshore.  It breaks up a perfectly level seabed, while the final cap
      // makes every mapped ocean column remain ocean and prevents continental
      // bumps from ever lifting it above sea level.
      double natural = lerp(shelfTarget, vanilla, 0.12 * progress);
      return Math.min(-0.20, natural);
   }

   private static double applyRiverCrossSection(int blockX, int blockZ, double terrain) {
      int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(blockX, blockZ);
      if (width <= 0) return terrain;
      double distance = RiversMask.INSTANCE.riverCentrelineDistance(blockX, blockZ)
         * (double)RiversMask.INSTANCE.blocksPerPixel();
      double waterRadius = Math.max(0.5, (double)width * 0.5);
      double bedRadius = Math.min(waterRadius, Math.max(0.5, waterRadius * RIVER_BED_CORE_RATIO));
      // Preserve part of the original C noise at the water edge. Joining the
      // channel to a fixed 0.06 bank erased complete hills before the gradual
      // recovery even began.
      double bankTarget = lerp(0.06, terrain, RiverTerrainTransition.bankNoiseRetention(blockX, blockZ));

      if (distance <= waterRadius) {
         double centre = (Double)EarthShapeServerConfig.RIVER_CHANNEL_CONTINENTALNESS.get();
         // Keep a width-proportional floor instead of letting C vary at every
         // block from the centreline. This makes wide rivers receive a wide,
         // stable bed while narrow rivers retain a correspondingly narrow one.
         if (distance <= bedRadius || waterRadius <= bedRadius) return centre;

         // Only the outer part of the water width becomes the submerged side
         // slope. Zero derivatives at both ends avoid a crease at the bed and
         // a vertical step where the water meets the bank.
         double acrossShoulder = smoothstep((distance - bedRadius) / (waterRadius - bedRadius));
         return lerp(centre, bankTarget, acrossShoulder);
      }

      // The channel cross-section owns water only. Land-side guidance is applied
      // below as a noise-amplitude blend, never as a flat bank plateau.
      return terrain;
   }

   private static double riverValleyInfluence(int blockX, int blockZ) {
      if (!(Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
         || !RiversMask.INSTANCE.hasInlandRiverInfluence(blockX, blockZ)) {
         return 0.0;
      }
      int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(blockX, blockZ);
      if (width <= 0) return 0.0;
      double distance = RiversMask.INSTANCE.riverCentrelineDistance(blockX, blockZ)
         * (double)RiversMask.INSTANCE.blocksPerPixel();
      double bankDistance = Math.max(0.0, distance - (double)width * 0.5);
      // End the low river guidance no later than three blocks beyond the water.
      // Once this reaches zero, the mapped-land C floor below reactivates and
      // restores the ordinary Y=64-or-higher surface baseline in every region.
      double fade = Math.max(
         1.0,
         Math.min(3.0, (double)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get())
      );
      if (bankDistance >= fade) return 0.0;
      double recovery = smoothstep(bankDistance / fade);
      return 1.0 - recovery;
   }

   /**
    * Retain mapped C noise directly from the water edge and progressively return
    * it to full strength. This lowers excessive waterfront relief without
    * replacing the riverbank with a broad Y=64 plane.
    */
   private static double applyRiverBankHeightLimit(
      int blockX, int blockZ, double terrain, double minimumContinentalness
   ) {
      if (!(Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
         || !RiversMask.INSTANCE.hasInlandRiverInfluence(blockX, blockZ)) {
         return terrain;
      }
      int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(blockX, blockZ);
      if (width <= 0) return terrain;
      double distance = RiversMask.INSTANCE.riverCentrelineDistance(blockX, blockZ)
         * (double)RiversMask.INSTANCE.blocksPerPixel();
      double bankDistance = distance - (double)width * 0.5;
      if (bankDistance < 0.0) return terrain;

      // Height recovery uses one automatic relief-aware span. Do not cap it by
      // bankFadeBlocks: that setting controls the immediate water edge, and
      // using it here made high terrain return within only 48 blocks.
      double transitionEnd = RiverTerrainTransition.distance(blockX, blockZ, width);
      if (bankDistance >= transitionEnd) return terrain;

      double recovery = RiverTerrainTransition.recovery(blockX, blockZ, bankDistance, transitionEnd);
      double limited = lerp(minimumContinentalness, terrain, recovery);
      return Math.max(minimumContinentalness, limited);
   }

   private static double smoothstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }

   private static double smootherstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * clamped * (clamped * (clamped * 6.0 - 15.0) + 10.0);
   }

   private static double lerp(double from, double to, double amount) {
      return from + (to - from) * amount;
   }


   @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
   @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new CoastalContinentalnessDensity(this.argument.mapAll(visitor))); }
   @Override public double minValue() { return -1.2; }
   @Override public double maxValue() { return 1.0; }
   @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
