package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import io.github.earthshape.worldgen.BiomeLookupCache;
import io.github.earthshape.worldgen.FilteredParameterCache;
import io.github.earthshape.worldgen.AdditionalBiomeRegistry;
import io.github.earthshape.worldgen.AdditionalBiomeRegistry.Hydrology;
import io.github.earthshape.worldgen.AdditionalBiomeRegistry.LayerKey;
import io.github.earthshape.worldgen.EarthShapeFinalBiomeResolver;
import io.github.earthshape.worldgen.ExternalBiomeCapture;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.neoforged.neoforge.common.Tags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Apply after TerraBlender, Biolith, Lithostitched and other biome selectors.
// Their completed holder is intentionally discarded at every return path.
@Mixin(value = {MultiNoiseBiomeSource.class}, priority = 1)
public abstract class TerrainBiomeMixin implements EarthShapeFinalBiomeResolver {
   private static final AtomicBoolean FINAL_OVERRIDE_LOGGED = new AtomicBoolean();
   /** Bounds duplicated climate RTrees so long Chunky runs cannot exhaust the VM. */
   private static final int MAX_FILTERED_PARAMETER_LISTS = 12;

   // One RTree per layer family is built lazily. The old implementation evaluated
   // every vanilla parameter point for every quart biome sample.
   @Unique
   private static final ConcurrentHashMap<Climate.ParameterList<Holder<Biome>>, FilteredParameterCache> earthshape$filteredParameterLists = new ConcurrentHashMap<>();
   @Unique
   private static final ConcurrentHashMap<Climate.ParameterList<Holder<Biome>>, Climate.ParameterList<Holder<Biome>>> earthshape$baseVanillaParameterLists = new ConcurrentHashMap<>();
   @Unique
   private static final ConcurrentHashMap<Climate.ParameterList<Holder<Biome>>, Climate.ParameterList<Holder<Biome>>> earthshape$terralithCaveParameterLists = new ConcurrentHashMap<>();
   @Unique
   private static final ConcurrentHashMap<Climate.ParameterList<Holder<Biome>>, Boolean> earthshape$overworldParameterLists = new ConcurrentHashMap<>();
   @Unique
   private static final ConcurrentHashMap<Climate.ParameterList<Holder<Biome>>, Map<ResourceKey<Biome>, Holder<Biome>>> earthshape$exactBiomes = new ConcurrentHashMap<>();
   @Unique
   private static final Set<ResourceKey<Biome>> earthshape$baseVanillaBiomes = discoverBaseVanillaBiomes();
   @Unique
   private static final ResourceKey<Biome> earthshape$bopGlowingGrotto = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("biomesoplenty", "glowing_grotto")
   );
   @Unique
   private static final ResourceKey<Biome> earthshape$bopSpiderNest = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("biomesoplenty", "spider_nest")
   );
   @Unique
   private static final ThreadLocal<BiomeLookupCache> earthshape$lookupCache = ThreadLocal.withInitial(BiomeLookupCache::new);

   @Shadow(remap = false)
   public abstract Climate.ParameterList<Holder<Biome>> parameters();

   @Inject(
      method = {"getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false
   )
   private void earthshape$chooseTerrainBiome(int quartX, int quartY, int quartZ, Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
      if (ExternalBiomeCapture.active()) {
         return;
      }
      callback.setReturnValue(this.earthshape$resolveFinalBiome(
         quartX, quartY, quartZ, sampler, callback.getReturnValue()
      ));
   }

   @Override
   public Holder<Biome> earthshape$resolveFinalBiome(
      int quartX,
      int quartY,
      int quartZ,
      Sampler sampler,
      Holder<Biome> discardedExternalResult
   ) {
      int blockX = quartX << 2;
      int blockY = quartY << 2;
      int blockZ = quartZ << 2;
      if (EarthShapeCompatibility.disablesWorldgen()) {
         return discardedExternalResult;
      }
      if (!this.earthshape$isOverworldSource()) {
         return discardedExternalResult;
      }

      ClimateLayers layers = ClimateLayers.INSTANCE;
      Climate.TargetPoint source = sampler.sample(quartX, quartY, quartZ);
      long layerPoint = warpedLayerPoint(blockX, blockZ);
      int layerX = unpackX(layerPoint);
      int layerZ = unpackZ(layerPoint);
      if (AdditionalBiomeRegistry.hasTfcBiomes()) {
         Holder<Biome> tfcBiome = this.selectTfcMapBiome(layers, blockX, blockY, blockZ);
         if (tfcBiome != null) {
            if (FINAL_OVERRIDE_LOGGED.compareAndSet(false, true)) {
               io.github.earthshape.EarthShape.LOGGER.info("[EarthShape] TFC-only biome resolver active; map layers select all Overworld biomes.");
            }
            return tfcBiome;
         }
      }
      Holder<Biome> selected;
      if (blockY < 48) {
         Holder<Biome> baseVanilla = EarthShapeCompatibility.isTerralithLoaded()
            ? this.terralithCaveParameters().findValue(source)
            : this.baseVanillaParameters().findValue(source);
         selected = (Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()
            && layers.hasTerrainCoverage(layerX, layerZ)
            && isSnowBiome(baseVanilla)
            ? this.undergroundNonSnowBiome(layers, blockX, blockZ, baseVanilla)
            : baseVanilla;
         if (isVanillaBiome(selected)) {
            selected = this.biomesOPlentyUndergroundBiome(source, selected);
         }
      } else if ((Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) {
         boolean mappedWater = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) < 0.5;
         if (mappedWater || layers.hasTerrainCoverage(layerX, layerZ)
            || RiversMask.INSTANCE.isInlandRiverBiome(blockX, blockZ)) {
            Climate.TargetPoint point = this.guidedClimatePoint(layers, blockX, blockZ, source);
            selected = this.selectLayerCandidate(layers, blockX, blockY, blockZ, point);
         } else if (RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5
            && layers.hasLegacyTemperature(layerX, layerZ)) {
            Holder<Biome> baseVanilla = this.baseVanillaParameters().findValue(source);
            selected = RiversMask.INSTANCE.isPermanentSouthernSnowLand(blockX, blockZ)
               ? this.findBiome(Biomes.SNOWY_PLAINS, baseVanilla)
               : this.baseVanillaParameters().findValue(this.temperatureGuidedClimatePoint(layers, blockX, blockZ, source));
         } else {
            selected = this.baseVanillaParameters().findValue(source);
         }
      } else {
         selected = this.baseVanillaParameters().findValue(source);
      }
      if (FINAL_OVERRIDE_LOGGED.compareAndSet(false, true)) {
         io.github.earthshape.EarthShape.LOGGER.info("[EarthShape] outermost Overworld biome resolver active; discarded external holder and stored EarthShape's final result.");
      }
      return selected;
   }

   /** Uses EarthShape's masks as the sole source of TFC hydrology and terrain. */
   private Holder<Biome> selectTfcMapBiome(ClimateLayers layers, int blockX, int blockY, int blockZ) {
      ClimateLayers.TerrainKind terrain = this.surfaceTerrain(layers, blockX, blockZ);
      // ClimateLayers already performs its own boundary-safe terrain warp.
      // Passing an already warped point here made tree.bmp receive that warp a
      // second time, weakening and offsetting the source-layer boundary.
      ClimateLayers.TreeCover trees = layers.treeCover(blockX, blockZ);
      double temperature = layers.temperature(blockX, blockZ);
      boolean snowAllowed = allowsSnow(terrain, blockY, temperature);
      boolean frozenPeaksAllowed = terrain == ClimateLayers.TerrainKind.MOUNTAIN
         && layers.mountainRegionHeightScale(blockX, blockZ) >= 0.55
         && (layers.isUltraMountain(blockX, blockZ) || layers.isPolarTemperatureZone(blockX, blockZ));
      Hydrology hydrology;
      if ((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get() && RiversMask.INSTANCE.isInlandRiverBiome(blockX, blockZ)) {
         hydrology = Hydrology.RIVER;
      } else if (RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) < 0.5 || terrain == ClimateLayers.TerrainKind.WATER) {
         hydrology = Hydrology.OCEAN;
      } else if (isCoastalLand(blockX, blockZ)) {
         hydrology = Hydrology.COAST;
      } else {
         hydrology = Hydrology.LAND;
      }
      return AdditionalBiomeRegistry.selectTfc(
         LayerKey.of(terrain, trees, temperature, hydrology, snowAllowed, frozenPeaksAllowed,
            terrain == ClimateLayers.TerrainKind.DESERT && layers.isMesaRegion(blockX, blockZ)),
         regionalVariant(blockX, blockZ)
      );
   }

   /**
    * Converts map layers into the axes used by vanilla's ParameterList.  The layers
    * are guidance, not a post-generation biome paint: the final holder is still the
    * closest vanilla climate entry chosen from the allowed terrain family.
    */
   private Climate.TargetPoint guidedClimatePoint(ClimateLayers layers, int blockX, int blockZ, Climate.TargetPoint source) {
      long layerPoint = warpedLayerPoint(blockX, blockZ);
      int layerX = unpackX(layerPoint);
      int layerZ = unpackZ(layerPoint);
      boolean mappedOcean = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) < 0.5;
      // Ocean temperature must use the exact coastline-map coordinate. Layer
      // domain warp is intentionally land-only guidance; applying it to water
      // displaced isotherms away from the coast that defines the same map.
      ClimateLayers.TerrainKind terrain = mappedOcean
         ? ClimateLayers.TerrainKind.WATER
         : layers.terrainKind(layerX, layerZ);
      boolean sourceRiver = (Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get() && RiversMask.INSTANCE.isInlandRiverBiome(blockX, blockZ);
      if (!sourceRiver) {
         terrain = layers.terrainKindAtRiverbank(blockX, blockZ, terrain);
      }
      ClimateLayers.TreeCover trees = layers.treeCover(layerX, layerZ);
      double layerTemperature = layers.temperature(
         mappedOcean ? blockX : layerX,
         mappedOcean ? blockZ : layerZ
      );
      float temperature = (float)layerTemperature;
      float humidity = -0.08F;
      float continentalness = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5 ? 0.14F : -0.50F;
      float erosion = 0.48F;
      float depth = Climate.unquantizeCoord(source.depth()) * 0.20F;
      float sourceErosion = Climate.unquantizeCoord(source.erosion());
      float sourceWeirdness = Climate.unquantizeCoord(source.weirdness());
      float weirdness = sourceWeirdness * 0.20F;
      float relief = (float)layers.steepness(layerX, layerZ);

      // trees.bmp only refines already-vegetated terrain.  It cannot turn an explicit
      // plains or desert colour into a forest/jungle family.
      switch (terrain) {
         case WATER -> continentalness = -0.62F;
         case DESERT -> {
            temperature = Math.max(temperature, 0.78F);
            humidity = -0.82F;
            continentalness = 0.12F;
            erosion = 0.34F;
         }
         case WETLAND -> {
            humidity = 0.82F;
            erosion = 0.72F;
         }
         case FOREST -> {
            humidity = trees == ClimateLayers.TreeCover.TROPICAL ? 0.80F : 0.58F;
            erosion = 0.48F - relief * 0.14F;
         }
         case JUNGLE -> {
            temperature = Math.max(temperature, 0.72F);
            humidity = 0.92F;
            erosion = 0.46F;
         }
         case HILLS -> {
            continentalness = 0.24F;
            erosion = sourceErosion;
            weirdness = sourceWeirdness;
         }
         case MOUNTAIN -> {
            continentalness = 0.36F;
            erosion = sourceErosion;
            weirdness = sourceWeirdness;
         }
         case PLAINS, CITY, SURROUNDING -> {
            humidity = -0.06F;
            erosion = 0.64F - relief * 0.12F;
         }
      }

      boolean frozenRiver = sourceRiver && RiversMask.INSTANCE.isFrozenInlandRiverBiome(blockX, blockZ);
      earthshape$lookupCache.get().set(blockX, blockZ, terrain, sourceRiver, frozenRiver, trees, layerTemperature);
      if (sourceRiver) {
         // Exact valley values from OverworldBiomeBuilder's river range. Temperature
         // stays layer-driven, so frozen source rivers resolve to FROZEN_RIVER.
         continentalness = -0.05F;
         erosion = 0.30F;
         depth = 0.0F;
         weirdness = 0.0F;
      }
      return Climate.target(temperature, humidity, continentalness, erosion, depth, weirdness);
   }

   /**
    * Run the normal vanilla nearest-parameter calculation, constrained only to the
    * biome family represented by terrain.bmp at this point.  No fixed biome key or
    * hash-based variant is chosen here.
    */
   private Holder<Biome> selectLayerCandidate(ClimateLayers layers, int blockX, int blockY, int blockZ, Climate.TargetPoint point) {
      return this.selectLayerCandidate(layers, blockX, blockY, blockZ, point, true);
   }

   private Holder<Biome> selectLayerCandidate(
      ClimateLayers layers, int blockX, int blockY, int blockZ, Climate.TargetPoint point, boolean surfaceClimateAllowed
   ) {
      BiomeLookupCache lookup = earthshape$lookupCache.get();
      ClimateLayers.TerrainKind terrain;
      boolean sourceRiver;
      boolean frozenRiver;
      ClimateLayers.TreeCover trees;
      double layerTemperature;
      if (lookup.matches(blockX, blockZ)) {
         terrain = lookup.terrain();
         sourceRiver = lookup.sourceRiver();
         frozenRiver = lookup.frozenRiver();
         trees = lookup.trees();
         layerTemperature = lookup.temperature();
      } else {
         terrain = layers.terrainKind(blockX, blockZ);
         sourceRiver = (Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get() && RiversMask.INSTANCE.isInlandRiverBiome(blockX, blockZ);
         if (!sourceRiver) {
            terrain = layers.terrainKindAtRiverbank(blockX, blockZ, terrain);
         }
         frozenRiver = sourceRiver && RiversMask.INSTANCE.isFrozenInlandRiverBiome(blockX, blockZ);
         trees = layers.treeCover(blockX, blockZ);
         layerTemperature = layers.temperature(blockX, blockZ);
         lookup.set(blockX, blockZ, terrain, sourceRiver, frozenRiver, trees, layerTemperature);
      }
      // Do not send an authoritative mapped river through a climate RTree. If a
      // modded parameter list contains no exact frozen-river entry, the generic
      // empty-family fallback is the full list and can select DEEP_FROZEN_OCEAN.
      // An exact holder lookup keeps both ordinary and frozen inland channels out
      // of every ocean family and also avoids building an unnecessary RTree.
      if (sourceRiver) {
         Holder<Biome> river = this.findBiome(Biomes.RIVER, this.fallbackBiome());
         if (frozenRiver) {
            return this.findBiome(Biomes.FROZEN_RIVER, river);
         }
         Holder<Biome> additionalRiver = this.additionalLayerBiome(
            terrain, trees, layerTemperature, Hydrology.RIVER, false, false, false, blockX, blockZ
         );
         return additionalRiver != null
            && !additionalRiver.is(Tags.Biomes.IS_OCEAN)
            && !additionalRiver.is(Tags.Biomes.IS_SNOWY)
            ? additionalRiver
            : river;
      }
      // The southern polar cap is deliberately treeless: keep the mapped
      // topography, but use one plain snow-cover biome for every land family.
      // Avoid ICE_SPIKES, GROVE, TAIGA and peak biomes because each can add
      // vegetation or terrain features beyond a simple snow blanket.
      if (surfaceClimateAllowed && RiversMask.INSTANCE.isPermanentSouthernSnowLand(blockX, blockZ)) {
         return this.findBiome(Biomes.SNOWY_PLAINS, this.fallbackBiome());
      }
      // Temperate forest directly below a mapped mountain otherwise repeatedly
      // resolves to the same lush/cherry-like forest candidates.  Convert only a
      // stable subset of this foothill band to plains candidates, yielding broad
      // meadow clearings instead of scattered tree-free pixels.
      if (terrain == ClimateLayers.TerrainKind.FOREST
         && trees != ClimateLayers.TreeCover.TROPICAL
         && layers.isNearMountain(blockX, blockZ, 80)
         && regionalVariant(blockX, blockZ) % 3 == 0) {
         terrain = ClimateLayers.TerrainKind.PLAINS;
      }
      final ClimateLayers.TerrainKind selectedTerrain = terrain;
      boolean riverMouth = RiversMask.INSTANCE.isRiverMouth(blockX, blockZ);
      // Use broad deterministic patches, rather than a per-quart random roll, so
      // only parts of a suitable coastline become beach biomes without speckling
      // the shoreline. River mouths retain their ocean/river transition.
      boolean beachEligible = surfaceClimateAllowed
         && !sourceRiver
         && !riverMouth
         && (selectedTerrain == ClimateLayers.TerrainKind.DESERT
            || selectedTerrain == ClimateLayers.TerrainKind.HILLS
            || selectedTerrain == ClimateLayers.TerrainKind.MOUNTAIN)
         && isCoastalLand(blockX, blockZ)
         && regionalVariant(blockX, blockZ) % 5 == 0;
      // Cherry groves are included in several mountain-slope tag sets. Keep them
      // as an exceptional temperate mountain biome rather than letting each mapped
      // mountain resolve to a pink forest.
      boolean cherryGroveAllowed = surfaceClimateAllowed
         && selectedTerrain == ClimateLayers.TerrainKind.MOUNTAIN
         && trees == ClimateLayers.TreeCover.TEMPERATE
         && layerTemperature > -0.25
         && layerTemperature < 0.35
         && regionalVariant(blockX, blockZ) % 24 == 0;
      boolean borealAllowed = surfaceClimateAllowed
         && selectedTerrain == ClimateLayers.TerrainKind.FOREST
         && (Boolean)EarthShapeServerConfig.TUNDRA_TEMPERATURE_ENABLED.get()
         && layerTemperature <= (Double)EarthShapeServerConfig.TUNDRA_TEMPERATURE_THRESHOLD.get();
      boolean snowBiomeAllowed = surfaceClimateAllowed && allowsSnow(selectedTerrain, blockY, layerTemperature);
      // A cold/white pixel alone must not make every small ridge a snowy peak.
      // mountainRegionHeightScale is derived from the connected mapped mountain
      // area, so peak biomes are reserved for substantial mountain systems.
      boolean largeMountainSystem = selectedTerrain == ClimateLayers.TerrainKind.MOUNTAIN
         && layers.mountainRegionHeightScale(blockX, blockZ) >= 0.55;
      boolean frozenPeaksAllowed = surfaceClimateAllowed
         && selectedTerrain == ClimateLayers.TerrainKind.MOUNTAIN
         && largeMountainSystem
         && (layers.isUltraMountain(blockX, blockZ) || layers.isPolarTemperatureZone(blockX, blockZ));
      if (surfaceClimateAllowed) {
         Holder<Biome> additional = beachEligible
            ? this.additionalLayerBiome(selectedTerrain, trees, layerTemperature, Hydrology.COAST, snowBiomeAllowed, frozenPeaksAllowed,
               layers.isMesaRegion(blockX, blockZ), blockX, blockZ)
            : this.additionalTerrainBiome(
               selectedTerrain,
               trees,
               layerTemperature,
               snowBiomeAllowed,
               frozenPeaksAllowed,
               blockX,
               blockZ
            );
         if (additional != null) {
            return additional;
         }
      }
      int biomeFamily = sourceRiver
         ? (frozenRiver ? 2 : 1)
         : (riverMouth || selectedTerrain == ClimateLayers.TerrainKind.WATER ? 3 : selectedTerrain.ordinal() + 4);
      int group = biomeFamily * 2 + (frozenPeaksAllowed ? 1 : 0);
      group = group * 4 + (beachEligible ? 2 : 0) + (cherryGroveAllowed ? 1 : 0);
      group = group * 2 + (snowBiomeAllowed ? 1 : 0);
      group = group * 2 + (borealAllowed ? 1 : 0);
      // Keep one bounded RTree per active biome family. The constructor must run
      // only once for a group: publishing after an out-of-map build allowed every
      // chunk worker to allocate the same large tree concurrently, producing an
      // OS-level OOM kill with no JVM crash log.
      Climate.ParameterList<Holder<Biome>> parameters = this.parameters();
      FilteredParameterCache filtered = earthshape$filteredParameterLists.computeIfAbsent(parameters, ignored -> new FilteredParameterCache());
      final int cacheGroup = group;
      Climate.ParameterList<Holder<Biome>> candidates = lookup.candidates(parameters, cacheGroup);
      if (candidates == null) {
         candidates = filtered.values().computeIfAbsent(
            cacheGroup,
            ignored -> this.createFilteredParameterList(
               selectedTerrain, sourceRiver, frozenRiver, riverMouth, frozenPeaksAllowed, beachEligible, cherryGroveAllowed, snowBiomeAllowed, borealAllowed
            )
         );
         // terrain.bmp is authoritative immediately below the land/ocean and
         // river masks.  Never fall back to the unfiltered climate tree when a
         // datapack omits a requested family: a hot DESERT point would otherwise
         // be free to resolve to savanna or jungle. Use an explicit vanilla member
         // of the mapped family instead.
         if (candidates == null) {
            return this.strictTerrainFallback(
               layers, selectedTerrain, blockX, blockY, blockZ, point,
               riverMouth, frozenPeaksAllowed, beachEligible, snowBiomeAllowed
            );
         }
         filtered.touch(cacheGroup);
         lookup.cacheCandidates(parameters, cacheGroup, candidates);
         filtered.trim(cacheGroup, MAX_FILTERED_PARAMETER_LISTS);
      }
      return candidates.findValue(point);
   }

   /** Applies full-map temperature and coastline C without inventing a terrain class. */
   private Climate.TargetPoint temperatureGuidedClimatePoint(
      ClimateLayers layers, int blockX, int blockZ, Climate.TargetPoint source
   ) {
      long layerPoint = warpedLayerPoint(blockX, blockZ);
      int layerX = unpackX(layerPoint);
      int layerZ = unpackZ(layerPoint);
      double coast = RiversMask.INSTANCE.sampleCoastalLandness(blockX, blockZ);
      coast = coast * coast * (3.0 - 2.0 * coast);
      // This helper is used for mapped land outside terrain.bmp. Keep the biome
      // on vanilla's coast/near-inland side even when the original continental
      // noise happens to be ocean. Otherwise the density creates land while the
      // biome places frozen-ocean icebergs on it.
      float continentalness = (float)(-0.10 + 0.24 * coast);
      return Climate.target(
         (float)layers.temperature(layerX, layerZ),
         Climate.unquantizeCoord(source.humidity()),
         continentalness,
         Climate.unquantizeCoord(source.erosion()),
         Climate.unquantizeCoord(source.depth()),
         Climate.unquantizeCoord(source.weirdness())
      );
   }

   /**
    * Approximate lock-free eviction. A worker holding an evicted value can finish
    * normally, while the map itself never retains enough RTrees to push a small
    * server VM into swap thrashing during whole-world pregeneration.
    */
   private Climate.ParameterList<Holder<Biome>> createFilteredParameterList(
      ClimateLayers.TerrainKind terrain, boolean sourceRiver, boolean frozenRiver, boolean riverMouth, boolean frozenPeaksAllowed, boolean beachEligible,
      boolean cherryGroveAllowed, boolean snowBiomeAllowed, boolean borealAllowed
   ) {
      List<com.mojang.datafixers.util.Pair<Climate.ParameterPoint, Holder<Biome>>> allowed = new ArrayList<>();
      for (var entry : this.parameters().values()) {
         if (this.isAllowedTerrainCandidate(
            terrain, sourceRiver, frozenRiver, riverMouth, frozenPeaksAllowed, beachEligible, cherryGroveAllowed, snowBiomeAllowed, borealAllowed, entry.getSecond()
         )) {
            allowed.add(entry);
         }
      }
      // Returning null deliberately delegates to strictTerrainFallback. Returning
      // the complete list here would demote terrain.bmp below temperature and let
      // unrelated climate families enter an explicitly mapped terrain region.
      return allowed.isEmpty() ? null : new Climate.ParameterList<>(List.copyOf(allowed));
   }

   private boolean isAllowedTerrainCandidate(
      ClimateLayers.TerrainKind terrain, boolean sourceRiver, boolean frozenRiver, boolean riverMouth, boolean frozenPeaksAllowed, boolean beachEligible,
      boolean cherryGroveAllowed, boolean snowBiomeAllowed, boolean borealAllowed, Holder<Biome> biome
   ) {
      // All candidates injected into the multi-noise table by TerraBlender,
      // Biolith, Lithostitched, Climate Rivers or a backport are discarded.
      // Mod biomes can enter only through AdditionalBiomeRegistry after the
      // EarthShape layer family has explicitly admitted their published tag.
      if (!isVanillaBiome(biome)) return false;
      // A connected river has one precomputed frozen state. Resolve it before
      // local snow gates so downstream temperature cannot split the same river.
      if (sourceRiver) return frozenRiver ? biome.is(Biomes.FROZEN_RIVER) : biome.is(Biomes.RIVER);
      // Vanilla family tags overlap heavily: snowy taiga is also a forest and
      // snowy plains is also a plains biome. Reject cold candidates before the
      // terrain-family test unless the explicit temperature/altitude gate opens.
      if (isSnowBiome(biome) && !snowBiomeAllowed) return false;
      if (biome.is(Tags.Biomes.IS_TAIGA) && !borealAllowed) return false;
      if (biome.is(Biomes.FROZEN_PEAKS) && !frozenPeaksAllowed) return false;
      if (biome.is(Biomes.CHERRY_GROVE) && !cherryGroveAllowed) return false;
      if (riverMouth || terrain == ClimateLayers.TerrainKind.WATER) return biome.is(Tags.Biomes.IS_OCEAN);
      if (beachEligible) {
         // Mountain/hill coasts must never enter the generic beach tag: it
         // includes the yellow sand beach and exposed a directional-looking
         // strip on north/west-facing mapped shorelines.
         if (terrain == ClimateLayers.TerrainKind.HILLS || terrain == ClimateLayers.TerrainKind.MOUNTAIN) {
            return biome.is(Biomes.SNOWY_BEACH) || biome.is(Biomes.STONY_SHORE);
         }
         return biome.is(Tags.Biomes.IS_BEACH) || biome.is(Biomes.SNOWY_BEACH);
      }
      return switch (terrain) {
         case DESERT -> isStrictDesertCandidate(biome);
         case WETLAND -> biome.is(Tags.Biomes.IS_SWAMP);
         // Climate stages are exclusive. Previously all forest and taiga
         // candidates remained in one RTree, allowing humidity/erosion to pick
         // temperate forest, normal taiga, or snowy taiga at the same mapped
         // temperature. Keep vegetation variants inside one temperature family.
         case FOREST -> snowBiomeAllowed
            ? biome.is(Biomes.SNOWY_TAIGA)
            : (borealAllowed
               ? biome.is(Tags.Biomes.IS_TAIGA) && !isSnowBiome(biome)
               : biome.is(Tags.Biomes.IS_FOREST) && !biome.is(Tags.Biomes.IS_TAIGA) && !isSnowBiome(biome));
         case JUNGLE -> biome.is(Tags.Biomes.IS_JUNGLE);
         case HILLS -> snowBiomeAllowed
            ? biome.is(Biomes.GROVE) || biome.is(Biomes.SNOWY_SLOPES)
            : (biome.is(Tags.Biomes.IS_HILL) || biome.is(Tags.Biomes.IS_MOUNTAIN_SLOPE)) && !isSnowBiome(biome);
         // A normal terrain-layer mountain must not become a snowy peak merely because
         // vanilla's altitude noise picked a peak entry. Only white ultra-mountains or
         // the mapped polar temperature band are allowed to select Frozen Peaks.
         case MOUNTAIN -> biome.is(Tags.Biomes.IS_MOUNTAIN_SLOPE)
            || biome.is(Tags.Biomes.IS_HILL)
            || (frozenPeaksAllowed ? biome.is(Tags.Biomes.IS_MOUNTAIN_PEAK) : biome.is(Biomes.STONY_PEAKS));
         case PLAINS, CITY, SURROUNDING -> snowBiomeAllowed
            ? biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES)
            : (biome.is(Tags.Biomes.IS_PLAINS) || biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU))
               && !isSnowBiome(biome);
         case WATER -> false;
      };
   }

   private Holder<Biome> strictTerrainFallback(
      ClimateLayers layers,
      ClimateLayers.TerrainKind terrain,
      int blockX,
      int blockY,
      int blockZ,
      Climate.TargetPoint point,
      boolean riverMouth,
      boolean frozenPeaksAllowed,
      boolean beachEligible,
      boolean snowBiomeAllowed
   ) {
      Holder<Biome> fallback = this.baseVanillaParameters().findValue(point);
      if (riverMouth || terrain == ClimateLayers.TerrainKind.WATER) {
         return this.oceanBiome(layers.temperature(blockX, blockZ), blockX, blockZ, fallback);
      }
      if (beachEligible) {
         if (terrain == ClimateLayers.TerrainKind.HILLS || terrain == ClimateLayers.TerrainKind.MOUNTAIN) {
            return this.findBiome(snowBiomeAllowed ? Biomes.SNOWY_BEACH : Biomes.STONY_SHORE, fallback);
         }
         return this.findBiome(snowBiomeAllowed ? Biomes.SNOWY_BEACH : Biomes.BEACH, fallback);
      }

      double temperature = layers.temperature(blockX, blockZ);
      int region = regionalVariant(blockX, blockZ);
      return switch (terrain) {
         case DESERT -> layers.isMesaRegion(blockX, blockZ)
            ? this.findBiome(region % 10 == 0 ? Biomes.ERODED_BADLANDS : (region % 5 == 0 ? Biomes.WOODED_BADLANDS : Biomes.BADLANDS), fallback)
            : this.findBiome(Biomes.DESERT, fallback);
         case WETLAND -> this.findBiome(temperature > 0.3 ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP, fallback);
         case FOREST -> this.forestBiome(temperature, snowBiomeAllowed, region, fallback);
         case JUNGLE -> this.findBiome(region % 12 == 0 ? Biomes.BAMBOO_JUNGLE : (region % 6 == 0 ? Biomes.SPARSE_JUNGLE : Biomes.JUNGLE), fallback);
         case HILLS -> snowBiomeAllowed
            ? this.findBiome(temperature < -0.55 ? Biomes.SNOWY_SLOPES : Biomes.GROVE, fallback)
            : this.findBiome(temperature > 0.45 ? Biomes.WINDSWEPT_SAVANNA : (region % 5 == 0 ? Biomes.WINDSWEPT_FOREST : Biomes.WINDSWEPT_HILLS), fallback);
         case MOUNTAIN -> this.findBiome(frozenPeaksAllowed ? Biomes.FROZEN_PEAKS : Biomes.STONY_PEAKS, fallback);
         case PLAINS, CITY, SURROUNDING -> this.plainsBiome(temperature, snowBiomeAllowed, region, fallback);
         case WATER -> this.oceanBiome(temperature, blockX, blockZ, fallback);
      };
   }

   private static boolean isStrictDesertCandidate(Holder<Biome> biome) {
      if (!biome.is(Tags.Biomes.IS_DESERT) && !biome.is(Tags.Biomes.IS_BADLANDS)) {
         return false;
      }
      // Some biome packs publish broad, overlapping climate tags. Explicit
      // terrain DESERT accepts only the desert/badlands identity and rejects a
      // candidate that is simultaneously advertised as another hot land family.
      if (biome.is(Tags.Biomes.IS_JUNGLE)
         || biome.is(Tags.Biomes.IS_SAVANNA)
         || biome.is(Tags.Biomes.IS_SWAMP)
         || biome.is(Tags.Biomes.IS_PLAINS)) {
         return false;
      }
      if (biome.is(Tags.Biomes.IS_FOREST)
         || biome.is(Tags.Biomes.IS_TAIGA)
         || biome.is(Tags.Biomes.IS_MOUNTAIN)
         || biome.is(Tags.Biomes.IS_SNOWY)
         || biome.is(Tags.Biomes.IS_OCEAN)
         || biome.is(Tags.Biomes.IS_BEACH)) {
         return false;
      }

      return biome.unwrapKey().map(key -> {
         if (isVanillaBiome(biome)) {
            return true;
         }
         // Terralith's reference/desert_all and badlands_all sets are curated,
         // mutually exclusive climate families. Trust those tags even for names
         // such as ancient_sands which a generic name heuristic cannot classify.
         if ("terralith".equals(key.location().getNamespace())) {
            return true;
         }
         // Common tags alone are not reliable enough for modded packs: several
         // lush/outback biomes advertise IS_DESERT for structure spawning. Only
         // explicitly desert-shaped identities may occupy a terrain.bmp desert.
         String path = key.location().getPath();
         return path.contains("desert")
            || path.contains("badland")
            || path.contains("mesa")
            || path.contains("dune")
            || path.contains("sand")
            || path.contains("wasteland")
            || path.contains("red_rock");
      }).orElse(false);
   }

   private static boolean isSnowBiome(Holder<Biome> biome) {
      return biome.is(Biomes.SNOWY_PLAINS)
         || biome.is(Biomes.ICE_SPIKES)
         || biome.is(Biomes.SNOWY_TAIGA)
         || biome.is(Biomes.SNOWY_BEACH)
         || biome.is(Biomes.GROVE)
         || biome.is(Biomes.SNOWY_SLOPES)
         || biome.is(Biomes.JAGGED_PEAKS)
         || biome.is(Biomes.FROZEN_PEAKS)
         || biome.is(Biomes.FROZEN_RIVER);
   }

   /**
    * Replaces only an underground surface-snow biome. This deliberately avoids
    * selectLayerCandidate: constructing a new filtered climate RTree from a chunk
    * worker can make every other worker wait inside ConcurrentHashMap.computeIfAbsent,
    * which in turn leaves the server thread blocked on the chunk completion future.
    */
   private Holder<Biome> undergroundNonSnowBiome(
      ClimateLayers layers, int blockX, int blockZ, Holder<Biome> fallback
   ) {
      if ((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
         && RiversMask.INSTANCE.isInlandRiverBiome(blockX, blockZ)) {
         return this.findBiome(Biomes.RIVER, fallback);
      }

      ClimateLayers.TerrainKind terrain = this.surfaceTerrain(layers, blockX, blockZ);
      double temperature = layers.temperature(blockX, blockZ);
      return switch (terrain) {
         case WATER -> this.oceanBiome(temperature, blockX, blockZ, fallback);
         case DESERT -> this.findBiome(Biomes.DESERT, fallback);
         case WETLAND -> this.findBiome(temperature > 0.3 ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP, fallback);
         case FOREST -> this.findBiome(temperature < -0.25 ? Biomes.TAIGA : Biomes.FOREST, fallback);
         case JUNGLE -> this.findBiome(Biomes.JUNGLE, fallback);
         case HILLS -> this.findBiome(Biomes.WINDSWEPT_HILLS, fallback);
         case MOUNTAIN -> this.findBiome(Biomes.STONY_PEAKS, fallback);
         case PLAINS, CITY, SURROUNDING -> this.findBiome(Biomes.PLAINS, fallback);
      };
   }

   /**
    * Restores the two Overworld cave parameter points registered by BOP's
    * BOPOverworldBiomeBuilder. EarthShape supplies an inert TerraBlender API, so
    * BOP can load safely but its Region consumer is intentionally not forwarded
    * into the surface biome table. Recreate only its original underground points:
    * depth 0.2..0.9, with far-inland continentalness for spider_nest and high
    * humidity for glowing_grotto. A previously selected modded cave (notably a
    * Terralith cave) is never replaced by this method.
    */
   private Holder<Biome> biomesOPlentyUndergroundBiome(Climate.TargetPoint source, Holder<Biome> fallback) {
      if (!EarthShapeCompatibility.isBiomesOPlentyLoaded()) {
         return fallback;
      }

      float depth = Climate.unquantizeCoord(source.depth());
      if (depth < 0.2F || depth > 0.9F) {
         return fallback;
      }

      float continentalness = Climate.unquantizeCoord(source.continentalness());
      if (continentalness >= 0.8F) {
         return this.findBiome(earthshape$bopSpiderNest, fallback);
      }

      float humidity = Climate.unquantizeCoord(source.humidity());
      if (humidity >= 0.7F) {
         return this.findBiome(earthshape$bopGlowingGrotto, fallback);
      }
      return fallback;
   }

   private Holder<Biome> applyLayerBiome(ClimateLayers layers, int blockX, int blockY, int blockZ, Holder<Biome> current) {
      // Biome lookup occurs for all vertical noise cells. Do not let the river column
      // below Y=48 fall back to a vanilla ocean biome.
      boolean sourceRiver = RiversMask.INSTANCE.isInlandRiver(blockX, blockZ);
      boolean riverMouth = blockY >= 48 && RiversMask.INSTANCE.isRiverMouth(blockX, blockZ);
      if (riverMouth) return this.oceanBiome(layers.temperature(blockX, blockZ), blockX, blockZ, current);
      if (sourceRiver) return this.findBiome(Biomes.RIVER, current);
      if (blockY < 48) return current;
      if (RiversMask.INSTANCE.sampleLand(blockX, blockZ) >= 0.5 && isInlandWaterBiome(current)) {
         return this.mapTerrainBiome(layers, blockX, blockY, blockZ, current);
      }
      // TerraBlender may return a modded holder even when it is only acting as a
      // region library. In that mode no holder may bypass terrain.bmp selection.
      if (!isVanillaBiome(current) && !EarthShapeCompatibility.isTerraBlenderLoaded()) return current;
      if (isVanillaRiver(current)) return this.mapTerrainBiome(layers, blockX, blockY, blockZ, current);
      if ((Boolean)EarthShapeServerConfig.OCEAN_TEMPERATURE_ENABLED.get() && RiversMask.INSTANCE.sampleLand(blockX, blockZ) < 0.25) {
         long layerPoint = warpedLayerPoint(blockX, blockZ);
         return this.oceanBiome(layers.temperature(unpackX(layerPoint), unpackZ(layerPoint)), blockX, blockZ, current);
      }
      return ((Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get() || EarthShapeCompatibility.isTerraBlenderLoaded())
         ? this.mapTerrainBiome(layers, blockX, blockY, blockZ, current)
         : current;
   }

   private Holder<Biome> mapTerrainBiome(ClimateLayers layers, int blockX, int blockY, int blockZ, Holder<Biome> fallback) {
      long layerPoint = warpedLayerPoint(blockX, blockZ);
      int layerX = unpackX(layerPoint);
      int layerZ = unpackZ(layerPoint);
      ClimateLayers.TerrainKind terrain = this.surfaceTerrain(layers, blockX, blockZ);
      ClimateLayers.TreeCover trees = layers.treeCover(layerX, layerZ);
      double temperature = layers.temperature(layerX, layerZ);
      boolean snowAllowed = allowsSnow(terrain, blockY, temperature);
      boolean frozenPeaksAllowed = layers.mountainRegionHeightScale(blockX, blockZ) >= 0.55
         && (layers.isUltraMountain(blockX, blockZ) || layers.isPolarTemperatureZone(blockX, blockZ));
      int region = regionalVariant(blockX, blockZ);
      boolean nextToLayerRiver = RiversMask.INSTANCE.isNearInlandRiver(blockX, blockZ, 32);
      // Do not force a tagged beach biome along every raster coastline.  That
      // produced a bright, one-source-pixel-wide stair-step belt. Beaches stay
      // an occasional terrain-specific transition instead of exposing the mask.
      boolean beachPatch = regionalVariant(blockX, blockZ) % 5 == 0;
      if (!nextToLayerRiver && beachPatch && isCoastalLand(blockX, blockZ)) {
         Holder<Biome> additionalBeach = this.additionalLayerBiome(
            terrain, trees, temperature, Hydrology.COAST, snowAllowed, frozenPeaksAllowed,
            terrain == ClimateLayers.TerrainKind.DESERT && layers.isMesaRegion(blockX, blockZ), blockX, blockZ
         );
         if (additionalBeach != null) {
            return additionalBeach;
         }
         if (terrain == ClimateLayers.TerrainKind.HILLS || terrain == ClimateLayers.TerrainKind.MOUNTAIN) {
            return this.findBiome(Biomes.STONY_SHORE, fallback);
         }

         boolean sandyBeach = terrain == ClimateLayers.TerrainKind.DESERT;
         if (sandyBeach) {
            return this.findBiome(snowAllowed ? Biomes.SNOWY_BEACH : Biomes.BEACH, fallback);
         }
      }
      Holder<Biome> additionalBiome = this.additionalTerrainBiome(
         terrain, trees, temperature, snowAllowed, frozenPeaksAllowed, blockX, blockZ
      );
      if (additionalBiome != null) return additionalBiome;
      return switch (terrain) {
         case DESERT -> layers.isMesaRegion(blockX, blockZ)
         ? this.findBiome(region % 10 == 0 ? Biomes.ERODED_BADLANDS : (region % 5 == 0 ? Biomes.WOODED_BADLANDS : Biomes.BADLANDS), fallback)
         : this.findBiome(Biomes.DESERT, fallback);
         case WETLAND -> this.findBiome(temperature > 0.3 ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP, fallback);
         case FOREST -> this.forestBiome(temperature, snowAllowed, region, fallback);
         case JUNGLE -> this.findBiome(region % 12 == 0 ? Biomes.BAMBOO_JUNGLE : (region % 6 == 0 ? Biomes.SPARSE_JUNGLE : Biomes.JUNGLE), fallback);
         case HILLS -> snowAllowed
         ? this.findBiome(temperature < -0.55 ? Biomes.SNOWY_SLOPES : Biomes.GROVE, fallback)
         : this.findBiome(temperature > 0.45 ? Biomes.WINDSWEPT_SAVANNA : (region % 5 == 0 ? Biomes.WINDSWEPT_FOREST : Biomes.WINDSWEPT_HILLS), fallback);
         case MOUNTAIN -> this.findBiome(frozenPeaksAllowed ? Biomes.FROZEN_PEAKS : Biomes.STONY_PEAKS, fallback);
         case PLAINS, CITY, SURROUNDING -> this.plainsBiome(temperature, snowAllowed, region, fallback);
         case WATER -> this.oceanBiome(temperature, blockX, blockZ, fallback);
      };
   }

   private Holder<Biome> forestBiome(double temperature, boolean snowAllowed, int region, Holder<Biome> fallback) {
      if (snowAllowed && temperature < -0.55) {
         return this.findBiome(Biomes.SNOWY_TAIGA, fallback);
      } else {
         return snowAllowed && temperature < -0.25
            ? this.findBiome(region % 6 == 0 ? Biomes.OLD_GROWTH_SPRUCE_TAIGA : (region % 4 == 0 ? Biomes.OLD_GROWTH_PINE_TAIGA : Biomes.TAIGA), fallback)
            : this.findBiome(
               region % 14 == 0
                  ? Biomes.FLOWER_FOREST
                  : (
                     region % 9 == 0
                        ? Biomes.BIRCH_FOREST
                        : (region % 7 == 0 ? Biomes.DARK_FOREST : (region % 19 == 0 ? Biomes.OLD_GROWTH_BIRCH_FOREST : Biomes.FOREST))
                  ),
               fallback
            );
      }
   }

   private Holder<Biome> plainsBiome(double temperature, boolean snowAllowed, int region, Holder<Biome> fallback) {
      if (snowAllowed && temperature < -0.55) {
         return this.findBiome(region % 17 == 0 ? Biomes.ICE_SPIKES : Biomes.SNOWY_PLAINS, fallback);
      } else if (snowAllowed && temperature < -0.3) {
         return this.findBiome(Biomes.SNOWY_PLAINS, fallback);
      } else {
         return temperature > 0.45
            ? this.findBiome(region % 6 == 0 ? Biomes.SAVANNA_PLATEAU : Biomes.SAVANNA, fallback)
            : this.findBiome(region % 16 == 0 ? Biomes.SUNFLOWER_PLAINS : Biomes.PLAINS, fallback);
      }
   }

   private Holder<Biome> oceanBiome(double temperature, int blockX, int blockZ, Holder<Biome> fallback) {
      boolean deep = isOpenOcean(blockX, blockZ);
      Holder<Biome> additionalOcean = this.additionalLayerBiome(
         ClimateLayers.TerrainKind.WATER, ClimateLayers.TreeCover.NONE, temperature,
         Hydrology.OCEAN, temperature <= -0.5, false, false, blockX, blockZ
      );
      if (additionalOcean != null) return additionalOcean;
      if (temperature > 0.65) {
         return this.findBiome(Biomes.WARM_OCEAN, fallback);
      } else if (temperature > 0.15) {
         return this.findBiome(deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN, fallback);
      } else if (temperature > -0.15) {
         return this.findBiome(deep ? Biomes.DEEP_OCEAN : Biomes.OCEAN, fallback);
      } else {
         return temperature > -0.5
            ? this.findBiome(deep ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN, fallback)
            : this.findBiome(deep ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN, fallback);
      }
   }

   private static boolean isCoastalLand(int blockX, int blockZ) {
      if (RiversMask.INSTANCE.sampleLand(blockX, blockZ) < 0.5) {
         return false;
      } else {
         int distance = 28;
         return RiversMask.INSTANCE.sampleLand(blockX - distance, blockZ) < 0.5
            || RiversMask.INSTANCE.sampleLand(blockX + distance, blockZ) < 0.5
            || RiversMask.INSTANCE.sampleLand(blockX, blockZ - distance) < 0.5
            || RiversMask.INSTANCE.sampleLand(blockX, blockZ + distance) < 0.5;
      }
   }

   private static boolean isOpenOcean(int blockX, int blockZ) {
      int distance = 180;
      return RiversMask.INSTANCE.sampleLand(blockX - distance, blockZ) < 0.25
         && RiversMask.INSTANCE.sampleLand(blockX + distance, blockZ) < 0.25
         && RiversMask.INSTANCE.sampleLand(blockX, blockZ - distance) < 0.25
         && RiversMask.INSTANCE.sampleLand(blockX, blockZ + distance) < 0.25;
   }

   private int regionalVariant(int blockX, int blockZ) {
      BiomeLookupCache lookup = earthshape$lookupCache.get();
      if (lookup.matches(blockX, blockZ) && lookup.regionalVariant() != Integer.MIN_VALUE) {
         return lookup.regionalVariant();
      }
      // The old >>10 lookup changed on exact 1024-block X/Z lines, producing
      // rectangular biome mosaics. Domain-warp a larger regional grid so one
      // biome occupies a broad coherent patch and its boundary follows curves.
      int warpedX = blockX + (int)Math.round(smoothNoise(blockX, blockZ, 0x2545F4914F6CDD1DL) * 520.0);
      int warpedZ = blockZ + (int)Math.round(smoothNoise(blockX, blockZ, 0x369DEA0F31A53F85L) * 520.0);
      int cellX = Math.floorDiv(warpedX, 1536);
      int cellZ = Math.floorDiv(warpedZ, 1536);
      long value = (long)cellX * 341873128712L ^ (long)cellZ * 132897987541L ^ 42317861L;
      value ^= value >>> 33;
      value *= -49064778989728563L;
      value ^= value >>> 33;
      int variant = (int)(value ^ value >>> 32) & 2147483647;
      if (lookup.matches(blockX, blockZ)) {
         lookup.cacheRegionalVariant(variant);
      }
      return variant;
   }

   private static boolean allowsSnow(ClimateLayers.TerrainKind terrain, int blockY, double temperature) {
      boolean climateSnow = (Boolean)EarthShapeServerConfig.TUNDRA_TEMPERATURE_ENABLED.get()
         && temperature <= (Double)EarthShapeServerConfig.SNOW_TEMPERATURE_THRESHOLD.get();
      // Vertical biome sampling around an exposed cave can reach a high quart Y.
      // Altitude alone must not turn ordinary plains/forest cave mouths into a
      // snowy biome; the altitude snowline belongs only to mapped high relief.
      boolean highReliefSnow = (terrain == ClimateLayers.TerrainKind.HILLS || terrain == ClimateLayers.TerrainKind.MOUNTAIN)
         && blockY >= (Integer)EarthShapeServerConfig.SNOW_ALTITUDE_BLOCKS.get();
      return climateSnow || highReliefSnow;
   }

   private static long warpedLayerPoint(int blockX, int blockZ) {
      if (!(Boolean)EarthShapeServerConfig.BIOME_BOUNDARY_WARP_ENABLED.get()) {
         return packPoint(blockX, blockZ);
      } else {
         int strength = Math.min(
            (Integer)EarthShapeServerConfig.BIOME_BOUNDARY_WARP_BLOCKS.get(),
            Math.max(12, RiversMask.INSTANCE.blocksPerPixel() * 4)
         );
         if (strength == 0) return packPoint(blockX, blockZ);

         // Always domain-warp map-layer lookup coordinates. Previously this
         // was conditional on a four-way boundary probe; a long straight edge
         // could miss that probe and therefore bypass the only noise guide.
         // The land-side check keeps the coastline authoritative, while river
         // selection continues to use its independent unwarped mask.
         int warpedX = blockX + (int)Math.round(
            (smoothNoise(blockX, blockZ, 7640891576956012809L) * 0.72
               + smoothNoise(blockX, blockZ, 6983438078262162901L) * 0.28) * (double)strength
         );
         int warpedZ = blockZ + (int)Math.round(
            (smoothNoise(blockX, blockZ, -4942790177534073029L) * 0.72
               + smoothNoise(blockX, blockZ, -7581763189423831141L) * 0.28) * (double)strength
         );
         boolean originalLand = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5;
         boolean warpedLand = RiversMask.INSTANCE.sampleLayerLand(warpedX, warpedZ) >= 0.5;
         return originalLand == warpedLand ? packPoint(warpedX, warpedZ) : packPoint(blockX, blockZ);
      }
   }

   private static long packPoint(int x, int z) {
      return (long)x << 32 | (long)z & 4294967295L;
   }

   private static int unpackX(long point) {
      return (int)(point >> 32);
   }

   private static int unpackZ(long point) {
      return (int)point;
   }

   private static double smoothNoise(int blockX, int blockZ, long salt) {
      int cellSize = 512;
      int cellX = Math.floorDiv(blockX, 512);
      int cellZ = Math.floorDiv(blockZ, 512);
      double x = (double)Math.floorMod(blockX, 512) / 512.0;
      double z = (double)Math.floorMod(blockZ, 512) / 512.0;
      x = x * x * (3.0 - 2.0 * x);
      z = z * z * (3.0 - 2.0 * z);
      double north = lerp(noiseValue(cellX, cellZ, salt), noiseValue(cellX + 1, cellZ, salt), x);
      double south = lerp(noiseValue(cellX, cellZ + 1, salt), noiseValue(cellX + 1, cellZ + 1, salt), x);
      return lerp(north, south, z);
   }

   private static double noiseValue(int x, int z, long salt) {
      long value = (long)x * 341873128712L ^ (long)z * 132897987541L ^ salt;
      value ^= value >>> 33;
      value *= -49064778989728563L;
      value ^= value >>> 33;
      return (double)((int)(value >>> 40) & 16777215) / 8388607.5 - 1.0;
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private Holder<Biome> findBiome(ResourceKey<Biome> key, Holder<Biome> fallback) {
      Map<ResourceKey<Biome>, Holder<Biome>> exact = earthshape$exactBiomes.computeIfAbsent(this.parameters(), ignored -> {
         Map<ResourceKey<Biome>, Holder<Biome>> indexed = new java.util.HashMap<>();
         ((MultiNoiseBiomeSource)(Object)this).possibleBiomes().forEach(holder -> holder.unwrapKey().ifPresent(holderKey -> indexed.put(holderKey, holder)));
         return Map.copyOf(indexed);
      });
      return exact.getOrDefault(key, fallback);
   }

   private Holder<Biome> fallbackBiome() {
      List<com.mojang.datafixers.util.Pair<Climate.ParameterPoint, Holder<Biome>>> values = this.parameters().values();
      return values.get(0).getSecond();
   }

   /**
    * TerraBlender may append mod biome holders during startup, but it never gets to
    * select them. EarthShape selects only holders whose published biome tag matches
    * the terrain.bmp class at this exact map position.
    */
   private Holder<Biome> additionalTerrainBiome(
      ClimateLayers.TerrainKind terrain,
      ClimateLayers.TreeCover trees,
      double temperature,
      boolean snowAllowed,
      boolean frozenPeaksAllowed,
      int blockX,
      int blockZ
   ) {
      if (!AdditionalBiomeRegistry.hasAdditionalBiomes()) return null;
      Hydrology hydrology = terrain == ClimateLayers.TerrainKind.WATER ? Hydrology.OCEAN : Hydrology.LAND;
      Holder<Biome> candidate = this.additionalLayerBiome(
         terrain, trees, temperature, hydrology, snowAllowed, terrain == ClimateLayers.TerrainKind.MOUNTAIN,
         terrain == ClimateLayers.TerrainKind.DESERT && ClimateLayers.INSTANCE.isMesaRegion(blockX, blockZ), blockX, blockZ
      );
      return terrain == ClimateLayers.TerrainKind.DESERT && candidate != null && !isStrictDesertCandidate(candidate) ? null : candidate;
   }

   private Holder<Biome> additionalLayerBiome(
      ClimateLayers.TerrainKind terrain,
      ClimateLayers.TreeCover trees,
      double temperature,
      Hydrology hydrology,
      boolean snowAllowed,
      boolean fullMountainPeak,
      boolean mesa,
      int blockX,
      int blockZ
   ) {
      Holder<Biome> candidate = AdditionalBiomeRegistry.select(
         LayerKey.of(terrain, trees, temperature, hydrology, snowAllowed, fullMountainPeak, mesa),
         regionalVariant(blockX, blockZ)
      );
      if (candidate != null
         && hydrology == Hydrology.LAND
         && terrain != ClimateLayers.TerrainKind.HILLS
         && terrain != ClimateLayers.TerrainKind.MOUNTAIN
         && AdditionalBiomeRegistry.isRareCandidate(candidate)
         && !supportsRareBiomeArea(terrain, blockX, blockZ)) {
         return null;
      }
      return candidate;
   }

   /**
    * Rare variants are allowed only where their source terrain family has room
    * for the configured footprint. A four-direction support test is constant
    * time and prevents an isolated narrow remnant from becoming a rare biome
    * without doing a connected-component search during chunk generation.
    */
   private static boolean supportsRareBiomeArea(ClimateLayers.TerrainKind terrain, int blockX, int blockZ) {
      int chunks = (Integer)EarthShapeServerConfig.RARE_BIOME_MINIMUM_REGION_CHUNKS.get();
      if (chunks <= 0) return true;
      int radius = Math.max(16, (int)Math.ceil(Math.sqrt((double)chunks)) * 8);
      ClimateLayers layers = ClimateLayers.INSTANCE;
      return layers.terrainKind(blockX - radius, blockZ) == terrain
         && layers.terrainKind(blockX + radius, blockZ) == terrain
         && layers.terrainKind(blockX, blockZ - radius) == terrain
         && layers.terrainKind(blockX, blockZ + radius) == terrain;
   }

   @Unique
   private Climate.ParameterList<Holder<Biome>> baseVanillaParameters() {
      Climate.ParameterList<Holder<Biome>> parameters = this.parameters();
      return earthshape$baseVanillaParameterLists.computeIfAbsent(parameters, source -> {
         List<com.mojang.datafixers.util.Pair<Climate.ParameterPoint, Holder<Biome>>> vanilla = source.values().stream()
            .filter(entry -> isVanillaBiome(entry.getSecond()))
            .toList();
         // The stock Overworld list always contains base Minecraft entries. Keep
         // a defensive fallback for nonstandard biome sources without crashing.
         return vanilla.isEmpty() ? source : new Climate.ParameterList<>(vanilla);
      });
   }

   @Unique
   private boolean earthshape$isOverworldSource() {
      Climate.ParameterList<Holder<Biome>> parameters = this.parameters();
      return earthshape$overworldParameterLists.computeIfAbsent(parameters, source -> source.values().stream()
         .anyMatch(entry -> entry.getSecond().is(Biomes.PLAINS) || entry.getSecond().is(Biomes.OCEAN)));
   }

   @Unique
   private Climate.ParameterList<Holder<Biome>> terralithCaveParameters() {
      Climate.ParameterList<Holder<Biome>> parameters = this.parameters();
      return earthshape$terralithCaveParameterLists.computeIfAbsent(parameters, source -> {
         List<com.mojang.datafixers.util.Pair<Climate.ParameterPoint, Holder<Biome>>> compatible = source.values().stream()
            .filter(entry -> {
               Holder<Biome> biome = entry.getSecond();
               if (isVanillaBiome(biome)) {
                  return true;
               }
               return biome.unwrapKey().map(key -> "terralith".equals(key.location().getNamespace())).orElse(false)
                  && (biome.is(Tags.Biomes.IS_CAVE) || biome.is(Tags.Biomes.IS_UNDERGROUND));
            })
            .toList();
         // Surface Terralith biomes never enter this tree. Vanilla surface
         // entries remain so cave biomes still have to win their intended D/H/E
         // ranges instead of occupying every underground climate cell.
         return compatible.isEmpty() ? this.baseVanillaParameters() : new Climate.ParameterList<>(compatible);
      });
   }

   @Unique
   private static Set<ResourceKey<Biome>> discoverBaseVanillaBiomes() {
      Set<ResourceKey<Biome>> found = new HashSet<>();
      for (Field field : Biomes.class.getFields()) {
         if (!Modifier.isStatic(field.getModifiers()) || !ResourceKey.class.isAssignableFrom(field.getType())) {
            continue;
         }
         try {
            Object value = field.get(null);
            if (value instanceof ResourceKey<?> key) {
               @SuppressWarnings("unchecked")
               ResourceKey<Biome> biomeKey = (ResourceKey<Biome>)key;
               found.add(biomeKey);
            }
         } catch (IllegalAccessException exception) {
            throw new ExceptionInInitializerError(exception);
         }
      }
      return Collections.unmodifiableSet(found);
   }

   private ClimateLayers.TerrainKind surfaceTerrain(ClimateLayers layers, int blockX, int blockZ) {
      ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
      if (terrain != ClimateLayers.TerrainKind.PLAINS) {
         return terrain;
      } else {
         ClimateLayers.TreeCover trees = layers.treeCover(blockX, blockZ);
         return trees == ClimateLayers.TreeCover.TROPICAL
            ? ClimateLayers.TerrainKind.JUNGLE
            : (trees == ClimateLayers.TreeCover.TEMPERATE ? ClimateLayers.TerrainKind.FOREST : ClimateLayers.TerrainKind.PLAINS);
      }
   }

   private static boolean isVanillaBiome(Holder<Biome> biome) {
      // Namespace alone is insufficient: backport mods register new candidates
      // such as minecraft:pale_garden. Only keys declared by the Minecraft 1.21.1
      // Biomes class belong to the base climate tree.
      return biome.unwrapKey().map(earthshape$baseVanillaBiomes::contains).orElse(false);
   }

   private static boolean isVanillaRiver(Holder<Biome> biome) {
      return biome.is(Biomes.RIVER) || biome.is(Biomes.FROZEN_RIVER);
   }

   private static boolean isInlandWaterBiome(Holder<Biome> biome) {
      return biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_RIVER) ? true : biome.unwrapKey().map(key -> {
         String path = key.location().getPath();
         return path.contains("river") || path.contains("lake");
      }).orElse(false);
   }
}
