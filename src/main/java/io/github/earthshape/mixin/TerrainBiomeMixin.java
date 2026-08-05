package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import io.github.earthshape.worldgen.BiomeLookupCache;
import io.github.earthshape.worldgen.FilteredParameterCache;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
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
import java.util.ArrayList;
import java.util.List;

// TerraBlender cancels getNoiseBiome at HEAD. This mixin must run first when that
// library is present, otherwise its RETURN hook would never be reached.
@Mixin(value = {MultiNoiseBiomeSource.class}, priority = 2000)
public abstract class TerrainBiomeMixin {
   private static final AtomicBoolean TERRABLENDER_INTERCEPT_LOGGED = new AtomicBoolean();
   /** Bounds duplicated climate RTrees so long Chunky runs cannot exhaust the VM. */
   private static final int MAX_FILTERED_PARAMETER_LISTS = 12;

   // One RTree per layer family is built lazily. The old implementation evaluated
   // every vanilla parameter point for every quart biome sample.
   @Unique
   private static final ConcurrentHashMap<Climate.ParameterList<Holder<Biome>>, FilteredParameterCache> earthshape$filteredParameterLists = new ConcurrentHashMap<>();
   @Unique
   private static final ThreadLocal<BiomeLookupCache> earthshape$lookupCache = ThreadLocal.withInitial(BiomeLookupCache::new);

   @Shadow(remap = false)
   public abstract Climate.ParameterList<Holder<Biome>> parameters();

   @Inject(
      method = {"getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void earthshape$beforeTerraBlender(int quartX, int quartY, int quartZ, Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
      if (!EarthShapeCompatibility.disablesWorldgen() && EarthShapeCompatibility.isTerraBlenderLoaded()) {
         int blockX = quartX << 2;
         int blockY = quartY << 2;
         int blockZ = quartZ << 2;
         // Cave biomes use their own multi-noise ranges. Never replace that
         // selection with a surface map-layer family merely to block TerraBlender.
         if (blockY < 48) return;
         // TerraBlender replaces this method at HEAD with a weighted region RTree.
         // EarthShape owns the final biome decision while its map worldgen is active,
         // so cancel every lookup here rather than allowing that RTree to run on
         // locations where the selected vanilla holder happened to be unchanged.
         ClimateLayers layers = ClimateLayers.INSTANCE;
         Climate.TargetPoint source = sampler.sample(quartX, quartY, quartZ);
         // The full coastline extends beyond legacy terrain.bmp. On uncovered
         // land, keep vanilla's multi-noise biome instead of fabricating a PLAINS
         // class and painting the high-latitude land-mask outline with snow.
         if (RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5
            && !layers.hasTerrainCoverage(blockX, blockZ)
            && !RiversMask.INSTANCE.isInlandRiverBiome(blockX, blockZ)) {
            if (RiversMask.INSTANCE.isPermanentSouthernSnowLand(blockX, blockZ)) {
               callback.setReturnValue(this.findBiome(Biomes.SNOWY_PLAINS, this.parameters().findValue(source)));
               return;
            }
            // terrain.bmp is the smaller legacy map, but earth_temperature.png
            // covers the full 6000x3400 land mask. Preserve vanilla humidity,
            // erosion, depth and weirdness while applying mapped temperature and
            // enough coastline continentalness to keep this land out of oceans.
            callback.setReturnValue(this.parameters().findValue(this.temperatureGuidedClimatePoint(layers, blockX, blockZ, source)));
            return;
         }
         Climate.TargetPoint point = this.guidedClimatePoint(layers, blockX, blockZ, source);
         Holder<Biome> mapped = this.selectLayerCandidate(layers, blockX, blockY, blockZ, point);
         if (TERRABLENDER_INTERCEPT_LOGGED.compareAndSet(false, true)) {
            io.github.earthshape.EarthShape.LOGGER.info("[EarthShape] TerraBlender biome lookup intercepted; applying EarthShape layer result before TerraBlender's region RTree.");
         }
         callback.setReturnValue(mapped);
      }
   }

   @Inject(
      method = {"getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false
   )
   private void earthshape$chooseTerrainBiome(int quartX, int quartY, int quartZ, Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
      int blockX = quartX << 2;
      int blockY = quartY << 2;
      int blockZ = quartZ << 2;
      if (!EarthShapeCompatibility.disablesWorldgen()) {
         // The HEAD injector above already owns and cancels every TerraBlender
         // surface lookup. Cancellation jumps through RETURN, so without this
         // guard the same climate sampling and RTree lookup runs a second time.
         if (blockY >= 48 && EarthShapeCompatibility.isTerraBlenderLoaded()) return;
         ClimateLayers layers = ClimateLayers.INSTANCE;
         if (blockY < 48) {
            // Preserve real cave biomes, but do not let vanilla's three-dimensional
            // climate lattice insert a surface snow biome below an otherwise normal
            // column. When exposed by a carver, that stray snowy_taiga/plains cell
            // placed snow specifically around the cave mouth.
            Holder<Biome> current = callback.getReturnValue();
            if ((Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()
               && layers.hasTerrainCoverage(blockX, blockZ)
               && isSnowBiome(current)) {
               callback.setReturnValue(this.undergroundNonSnowBiome(layers, blockX, blockZ, current));
            }
         } else if ((Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) {
            boolean mappedWater = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) < 0.5;
            if (mappedWater || layers.hasTerrainCoverage(blockX, blockZ)
               || RiversMask.INSTANCE.isInlandRiverBiome(blockX, blockZ)) {
               Climate.TargetPoint point = this.guidedClimatePoint(layers, blockX, blockZ, sampler.sample(quartX, quartY, quartZ));
               callback.setReturnValue(this.selectLayerCandidate(layers, blockX, blockY, blockZ, point));
            } else if (RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5
               && layers.hasLegacyTemperature(blockX, blockZ)) {
               Climate.TargetPoint source = sampler.sample(quartX, quartY, quartZ);
               callback.setReturnValue(RiversMask.INSTANCE.isPermanentSouthernSnowLand(blockX, blockZ)
                  ? this.findBiome(Biomes.SNOWY_PLAINS, callback.getReturnValue())
                  : this.parameters().findValue(this.temperatureGuidedClimatePoint(layers, blockX, blockZ, source)));
            }
         }
      }
   }

   /**
    * Converts map layers into the axes used by vanilla's ParameterList.  The layers
    * are guidance, not a post-generation biome paint: the final holder is still the
    * closest vanilla climate entry chosen from the allowed terrain family.
    */
   private Climate.TargetPoint guidedClimatePoint(ClimateLayers layers, int blockX, int blockZ, Climate.TargetPoint source) {
      ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
      boolean sourceRiver = (Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get() && RiversMask.INSTANCE.isInlandRiverBiome(blockX, blockZ);
      if (!sourceRiver) {
         terrain = layers.terrainKindAtRiverbank(blockX, blockZ, terrain);
      }
      ClimateLayers.TreeCover trees = layers.treeCover(blockX, blockZ);
      double layerTemperature = layers.temperature(blockX, blockZ);
      float temperature = (float)layerTemperature;
      float humidity = -0.08F;
      float continentalness = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5 ? 0.14F : -0.50F;
      float erosion = 0.48F;
      float depth = Climate.unquantizeCoord(source.depth()) * 0.20F;
      float sourceErosion = Climate.unquantizeCoord(source.erosion());
      float sourceWeirdness = Climate.unquantizeCoord(source.weirdness());
      float weirdness = sourceWeirdness * 0.20F;
      float relief = (float)layers.steepness(blockX, blockZ);

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
         Holder<Biome> river = this.findBiome(Biomes.RIVER, this.parameters().findValue(point));
         return frozenRiver ? this.findBiome(Biomes.FROZEN_RIVER, river) : river;
      }
      // The southern polar cap is deliberately treeless: keep the mapped
      // topography, but use one plain snow-cover biome for every land family.
      // Avoid ICE_SPIKES, GROVE, TAIGA and peak biomes because each can add
      // vegetation or terrain features beyond a simple snow blanket.
      if (surfaceClimateAllowed && RiversMask.INSTANCE.isPermanentSouthernSnowLand(blockX, blockZ)) {
         return this.findBiome(Biomes.SNOWY_PLAINS, this.parameters().findValue(point));
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
      double coast = RiversMask.INSTANCE.sampleCoastalLandness(blockX, blockZ);
      coast = coast * coast * (3.0 - 2.0 * coast);
      // This helper is used for mapped land outside terrain.bmp. Keep the biome
      // on vanilla's coast/near-inland side even when the original continental
      // noise happens to be ocean. Otherwise the density creates land while the
      // biome places frozen-ocean icebergs on it.
      float continentalness = (float)(-0.10 + 0.24 * coast);
      return Climate.target(
         (float)layers.temperature(blockX, blockZ),
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
      // A datapack can omit an entire vanilla tag family. Preserve vanilla's
      // behaviour in that case instead of crashing while constructing an empty RTree.
      return allowed.isEmpty() ? this.parameters() : new Climate.ParameterList<>(List.copyOf(allowed));
   }

   private boolean isAllowedTerrainCandidate(
      ClimateLayers.TerrainKind terrain, boolean sourceRiver, boolean frozenRiver, boolean riverMouth, boolean frozenPeaksAllowed, boolean beachEligible,
      boolean cherryGroveAllowed, boolean snowBiomeAllowed, boolean borealAllowed, Holder<Biome> biome
   ) {
      // Keep TerraBlender present as an API for dependent mods, but do not allow
      // its registered region biomes to enter EarthShape's final selector.
      if (EarthShapeCompatibility.isTerraBlenderLoaded() && !isVanillaBiome(biome)) return false;
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
         case DESERT -> biome.is(Tags.Biomes.IS_DESERT) || biome.is(Tags.Biomes.IS_BADLANDS);
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
         if (terrain == ClimateLayers.TerrainKind.HILLS || terrain == ClimateLayers.TerrainKind.MOUNTAIN) {
            return this.findBiome(Biomes.STONY_SHORE, fallback);
         }

         boolean sandyBeach = terrain == ClimateLayers.TerrainKind.DESERT;
         if (sandyBeach) {
            return this.findBiome(snowAllowed ? Biomes.SNOWY_BEACH : Biomes.BEACH, fallback);
         }
      }
      Holder<Biome> terraBiome = this.terraBlenderTerrainBiome(terrain, snowAllowed, frozenPeaksAllowed, blockX, blockZ);
      if (terraBiome != null) return terraBiome;
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
      Holder<Biome> terraOcean = this.terraBlenderTaggedBiome(Tags.Biomes.IS_OCEAN, blockX, blockZ);
      if (terraOcean != null) return terraOcean;
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

   private static int regionalVariant(int blockX, int blockZ) {
      long value = (long)(blockX >> 10) * 341873128712L ^ (long)(blockZ >> 10) * 132897987541L ^ 42317861L;
      value ^= value >>> 33;
      value *= -49064778989728563L;
      value ^= value >>> 33;
      return (int)(value ^ value >>> 32) & 2147483647;
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
         int strength = Math.min((Integer)EarthShapeServerConfig.BIOME_BOUNDARY_WARP_BLOCKS.get(), Math.max(4, RiversMask.INSTANCE.blocksPerPixel() * 3 / 4));
         if (strength == 0 || ClimateLayers.INSTANCE.isTerrainBoundary(blockX, blockZ, Math.max(8, strength * 2))) {
            return packPoint(blockX, blockZ);
         } else {
            int warpedX = blockX + (int)Math.round(smoothNoise(blockX, blockZ, 7640891576956012809L) * (double)strength);
            int warpedZ = blockZ + (int)Math.round(smoothNoise(blockX, blockZ, -4942790177534073029L) * (double)strength);
            return packPoint(warpedX, warpedZ);
         }
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
      return ((MultiNoiseBiomeSource)(Object)this).possibleBiomes().stream().filter(holder -> holder.is(key)).findFirst().orElse(fallback);
   }

   /**
    * TerraBlender may append mod biome holders during startup, but it never gets to
    * select them. EarthShape selects only holders whose published biome tag matches
    * the terrain.bmp class at this exact map position.
    */
   private Holder<Biome> terraBlenderTerrainBiome(ClimateLayers.TerrainKind terrain, boolean snowAllowed, boolean frozenPeaksAllowed, int blockX, int blockZ) {
      // TerraBlender remains loaded only to satisfy dependent mods. Its tagged
      // biome entries must not bypass the EarthShape layer selector.
      if (EarthShapeCompatibility.isTerraBlenderLoaded()) return null;
      return switch (terrain) {
         case DESERT -> this.terraBlenderTaggedBiome(ClimateLayers.INSTANCE.isMesaRegion(blockX, blockZ) ? Tags.Biomes.IS_BADLANDS : Tags.Biomes.IS_DESERT, blockX, blockZ);
         case WETLAND -> this.terraBlenderTaggedBiome(Tags.Biomes.IS_SWAMP, blockX, blockZ);
         case JUNGLE -> this.terraBlenderTaggedBiome(Tags.Biomes.IS_JUNGLE, blockX, blockZ);
         case FOREST -> this.terraBlenderTaggedBiome(snowAllowed ? Tags.Biomes.IS_TAIGA : Tags.Biomes.IS_FOREST, blockX, blockZ);
         case HILLS -> this.terraBlenderTaggedBiome(Tags.Biomes.IS_MOUNTAIN_SLOPE, blockX, blockZ);
         case MOUNTAIN -> frozenPeaksAllowed ? this.terraBlenderTaggedBiome(Tags.Biomes.IS_MOUNTAIN_PEAK, blockX, blockZ) : null;
         case WATER -> this.terraBlenderTaggedBiome(Tags.Biomes.IS_OCEAN, blockX, blockZ);
         case PLAINS, CITY, SURROUNDING -> null;
      };
   }

   private Holder<Biome> terraBlenderTaggedBiome(TagKey<Biome> tag, int blockX, int blockZ) {
      if (EarthShapeCompatibility.isTerraBlenderLoaded()) return null;
      List<Holder<Biome>> candidates = ((MultiNoiseBiomeSource)(Object)this).possibleBiomes().stream()
         .filter(holder -> !isVanillaBiome(holder) && holder.is(tag))
         .toList();
      return candidates.isEmpty() ? null : candidates.get(regionalVariant(blockX, blockZ) % candidates.size());
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
      return biome.unwrapKey().map(key -> "minecraft".equals(key.location().getNamespace())).orElse(false);
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
