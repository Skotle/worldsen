package io.github.earthshape.worldgen;

import io.github.earthshape.EarthShape;
import io.github.earthshape.mixin.BiomeSourceAccessor;
import io.github.earthshape.map.ClimateLayers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.server.MinecraftServer;

/**
 * Makes registered modded overworld biomes available to EarthShape without
 * activating TerraBlender's region selector. Candidate pools are immutable
 * after server start, so chunk workers only perform constant-time reads.
 */
public final class AdditionalBiomeRegistry {
   private static final TagKey<Biome> IS_CONIFEROUS = TagKey.create(
      Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_tree/coniferous")
   );
   private static final List<TagKey<Biome>> CLASSIFICATION_TAGS = List.of(
      Tags.Biomes.IS_DESERT,
      Tags.Biomes.IS_BADLANDS,
      Tags.Biomes.IS_SWAMP,
      Tags.Biomes.IS_JUNGLE,
      Tags.Biomes.IS_FOREST,
      Tags.Biomes.IS_TAIGA,
      Tags.Biomes.IS_MOUNTAIN,
      Tags.Biomes.IS_MOUNTAIN_PEAK,
      Tags.Biomes.IS_MOUNTAIN_SLOPE,
      Tags.Biomes.IS_PLAINS,
      Tags.Biomes.IS_SAVANNA,
      Tags.Biomes.IS_OCEAN,
      Tags.Biomes.IS_BEACH,
      Tags.Biomes.IS_RIVER,
      Tags.Biomes.IS_CAVE,
      Tags.Biomes.IS_UNDERGROUND
   );

   private static volatile Map<TagKey<Biome>, CandidatePool> pools = Map.of();
   private static volatile List<Holder<Biome>> allBiomes = List.of();
   private static final ConcurrentHashMap<LayerKey, LayerCandidatePool> layerPools = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<LayerKey, List<Holder<Biome>>> tfcLayerPools = new ConcurrentHashMap<>();

   public enum Hydrology {
      LAND,
      OCEAN,
      COAST,
      RIVER,
      CAVE
   }

   /** Complete layer state used to build one immutable eligible-biome pool. */
   public record LayerKey(
      ClimateLayers.TerrainKind terrain,
      ClimateLayers.TreeCover trees,
      int temperatureBand,
      Hydrology hydrology,
      boolean snowAllowed,
      boolean fullMountainPeak,
      boolean mesa
   ) {
      public static LayerKey of(
         ClimateLayers.TerrainKind terrain,
         ClimateLayers.TreeCover trees,
         double temperature,
         Hydrology hydrology,
         boolean snowAllowed,
         boolean fullMountainPeak,
         boolean mesa
      ) {
         return new LayerKey(terrain, trees, band(temperature), hydrology, snowAllowed, fullMountainPeak, mesa);
      }

      private static int band(double temperature) {
         if (temperature < -0.875) return 0;
         if (temperature < -0.625) return 1;
         if (temperature < -0.375) return 2;
         if (temperature < -0.125) return 3;
         if (temperature < 0.125) return 4;
         if (temperature < 0.375) return 5;
         if (temperature < 0.625) return 6;
         return temperature < 0.875 ? 7 : 8;
      }
   }

   private AdditionalBiomeRegistry() {
   }

   public static void register() {
      ServerLifecycleEvents.SERVER_STARTING.register(AdditionalBiomeRegistry::onServerStarting);
      ServerLifecycleEvents.SERVER_STOPPING.register(AdditionalBiomeRegistry::onServerStopping);
   }

   public static Holder<Biome> select(LayerKey key, int variant) {
      LayerCandidatePool pool = layerPools.computeIfAbsent(key, AdditionalBiomeRegistry::buildLayerPool);
      if (pool.all().isEmpty()) return null;
      // IS_RARE is a placement constraint, not merely descriptive metadata.
      // Keep the decision on the broad regional variant so rare BOP biomes form
      // coherent regions rather than quart-sized speckles.
      boolean rareSlot = Math.floorMod(variant, 11) == 0;
      List<Holder<Biome>> candidates;
      if (rareSlot && !pool.bopRare().isEmpty()) {
         candidates = pool.bopRare();
      } else if (!rareSlot && pool.regular().isEmpty()) {
         return null;
      } else if (!pool.regular().isEmpty()) {
         candidates = pool.regular();
      } else {
         candidates = pool.all();
      }
      int saltedVariant = variant ^ key.hashCode() * 0x45d9f3b;
      return candidates.get(Math.floorMod(saltedVariant, candidates.size()));
   }

   /** True for a non-vanilla candidate tagged as intentionally rare. */
   public static boolean isRareCandidate(Holder<Biome> biome) {
      return biome.unwrapKey()
         .map(key -> !"minecraft".equals(key.identifier().getNamespace()) && biome.is(Tags.Biomes.IS_RARE))
         .orElse(false);
   }

   /** Selects a TFC biome from the same map-controlled terrain and hydrology layers. */
   public static Holder<Biome> selectTfc(LayerKey key, int variant) {
      List<Holder<Biome>> candidates = tfcLayerPools.computeIfAbsent(key, AdditionalBiomeRegistry::buildTfcLayerPool);
      if (candidates.isEmpty()) return null;
      int saltedVariant = variant ^ key.hashCode() * 0x45d9f3b;
      return candidates.get(Math.floorMod(saltedVariant, candidates.size()));
   }

   private static List<Holder<Biome>> buildTfcLayerPool(LayerKey key) {
      List<Holder<Biome>> exact = allBiomes.stream()
         .filter(TfcBiomeLayers::isTfc)
         .filter(candidate -> TfcBiomeLayers.matches(key, candidate))
         .toList();
      return exact.isEmpty()
         ? allBiomes.stream().filter(TfcBiomeLayers::isTfc)
            .filter(candidate -> TfcBiomeLayers.matchesFallback(key, candidate)).toList()
         : exact;
   }

   public static boolean hasTfcBiomes() {
      return allBiomes.stream().anyMatch(TfcBiomeLayers::isTfc);
   }

   private static LayerCandidatePool buildLayerPool(LayerKey key) {
      List<Holder<Biome>> all = allBiomes.stream()
         .filter(candidate -> matchesLayerCombination(key, candidate))
         .toList();
      return new LayerCandidatePool(
         all,
         all.stream().filter(AdditionalBiomeRegistry::isBopRare).toList(),
         all.stream().filter(candidate -> !isBopRare(candidate)).toList()
      );
   }

   private static boolean matchesLayerCombination(LayerKey key, Holder<Biome> biome) {
      if (TfcBiomeLayers.isTfc(biome)) return TfcBiomeLayers.matches(key, biome);
      if (!matchesTemperature(key, biome)) return false;
      if (!matchesBopTagConstraints(key, biome)) return false;
      return switch (key.hydrology()) {
         case CAVE -> (biome.is(Tags.Biomes.IS_CAVE) || biome.is(Tags.Biomes.IS_UNDERGROUND))
            && !isAny(biome, Tags.Biomes.IS_OCEAN, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_RIVER);
         case RIVER -> biome.is(Tags.Biomes.IS_RIVER)
            && !isAny(biome, Tags.Biomes.IS_OCEAN, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_CAVE);
         case OCEAN -> biome.is(Tags.Biomes.IS_OCEAN)
            && !createsUnmappedOceanLand(biome)
            && !isAny(biome, Tags.Biomes.IS_RIVER, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_CAVE);
         case COAST -> (biome.is(Tags.Biomes.IS_BEACH) || bopFamily(biome) == BopFamily.COAST)
            && !isAny(biome, Tags.Biomes.IS_OCEAN, Tags.Biomes.IS_RIVER, Tags.Biomes.IS_CAVE);
         case LAND -> matchesLandFamily(key, biome)
            && !isAny(biome, Tags.Biomes.IS_OCEAN, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_RIVER, Tags.Biomes.IS_CAVE, Tags.Biomes.IS_UNDERGROUND);
      };
   }

   private static boolean matchesLandFamily(LayerKey key, Holder<Biome> biome) {
      // Meadows are open mid-elevation slopes even when a compatibility tag also
      // classifies them as plains. Keep vanilla and modded meadow variants out of
      // lowland plains/forest selection.
      if (isMeadowLike(biome)) return key.terrain() == ClimateLayers.TerrainKind.HILLS;
      BopFamily bop = bopFamily(biome);
      if (bop != BopFamily.NONE) {
         return switch (key.terrain()) {
            case DESERT -> bop == BopFamily.DESERT;
            case WETLAND -> bop == BopFamily.WETLAND;
            case JUNGLE -> bop == BopFamily.JUNGLE;
            case FOREST -> bop == BopFamily.FOREST;
            case HILLS -> bop == BopFamily.HILLS;
            case MOUNTAIN -> bop == BopFamily.MOUNTAIN;
            case PLAINS, CITY, SURROUNDING -> bop == BopFamily.PLAINS;
            case WATER -> false;
         };
      }
      return switch (key.terrain()) {
         case DESERT -> key.mesa()
            ? biome.is(Tags.Biomes.IS_BADLANDS) && !hasForeignPrimary(biome, Tags.Biomes.IS_BADLANDS)
            : biome.is(Tags.Biomes.IS_DESERT) && !hasForeignPrimary(biome, Tags.Biomes.IS_DESERT);
         case WETLAND -> biome.is(Tags.Biomes.IS_SWAMP) && !hasForeignPrimary(biome, Tags.Biomes.IS_SWAMP);
         case JUNGLE -> biome.is(Tags.Biomes.IS_JUNGLE) && !hasForeignPrimary(biome, Tags.Biomes.IS_JUNGLE);
         case FOREST -> {
            if (key.trees() == ClimateLayers.TreeCover.TROPICAL && key.temperatureBand() >= 5) {
               yield biome.is(Tags.Biomes.IS_JUNGLE) && !hasForeignPrimary(biome, Tags.Biomes.IS_JUNGLE);
            }
            boolean taiga = key.temperatureBand() <= 3;
            TagKey<Biome> family = taiga ? Tags.Biomes.IS_TAIGA : Tags.Biomes.IS_FOREST;
            yield biome.is(family) && !hasForeignPrimary(biome, family, taiga ? Tags.Biomes.IS_FOREST : null);
         }
         case HILLS -> biome.is(Tags.Biomes.IS_MOUNTAIN_SLOPE)
            && !hasForeignPrimary(biome, Tags.Biomes.IS_MOUNTAIN_SLOPE, Tags.Biomes.IS_MOUNTAIN);
         case MOUNTAIN -> key.fullMountainPeak()
            ? biome.is(Tags.Biomes.IS_MOUNTAIN_PEAK)
               && !hasForeignPrimary(biome, Tags.Biomes.IS_MOUNTAIN, Tags.Biomes.IS_MOUNTAIN_PEAK)
            : biome.is(Tags.Biomes.IS_MOUNTAIN)
               && !hasForeignPrimary(biome, Tags.Biomes.IS_MOUNTAIN);
         case PLAINS, CITY, SURROUNDING -> {
            boolean savanna = key.temperatureBand() >= 6;
            TagKey<Biome> family = savanna ? Tags.Biomes.IS_SAVANNA : Tags.Biomes.IS_PLAINS;
            yield biome.is(family) && !hasForeignPrimary(biome, family);
         }
         case WATER -> false;
      };
   }

   private static boolean matchesTemperature(LayerKey key, Holder<Biome> biome) {
      int band = key.temperatureBand();
      if (!key.snowAllowed() && biome.is(Tags.Biomes.IS_SNOWY)) return false;
      if (biome.is(Tags.Biomes.IS_HOT) && band < 5) return false;
      if (biome.is(Tags.Biomes.IS_COLD) && band > 3) return false;
      if (biome.is(Tags.Biomes.IS_SNOWY) && band > 3 && !key.fullMountainPeak()) return false;
      if (band <= 1 && key.snowAllowed() && key.hydrology() == Hydrology.LAND
         && !biome.is(Tags.Biomes.IS_SNOWY) && !biome.is(Tags.Biomes.IS_COLD)) return false;
      boolean coldBopDesert = biome.unwrapKey().map(candidate ->
         "biomesoplenty".equals(candidate.identifier().getNamespace())
            && "cold_desert".equals(candidate.identifier().getPath())
      ).orElse(false);
      if ((key.terrain() == ClimateLayers.TerrainKind.DESERT || key.terrain() == ClimateLayers.TerrainKind.JUNGLE)
         && band < 4 && !coldBopDesert) return false;
      return true;
   }

   /** Applies every BOP 21.1 convention tag that maps to an EarthShape layer. */
   private static boolean matchesBopTagConstraints(LayerKey key, Holder<Biome> biome) {
      if (!isBop(biome)) return true;

      ClimateLayers.TerrainKind terrain = key.terrain();
      ClimateLayers.TreeCover trees = key.trees();
      boolean cave = key.hydrology() == Hydrology.CAVE;
      boolean coast = key.hydrology() == Hydrology.COAST;
      boolean wetTerrain = terrain == ClimateLayers.TerrainKind.WETLAND
         || terrain == ClimateLayers.TerrainKind.JUNGLE;
      boolean rugged = terrain == ClimateLayers.TerrainKind.HILLS
         || terrain == ClimateLayers.TerrainKind.MOUNTAIN;

      if (biome.is(Tags.Biomes.IS_SANDY)
         && terrain != ClimateLayers.TerrainKind.DESERT && !coast) return false;
      if (biome.is(Tags.Biomes.IS_AQUATIC)
         && !wetTerrain && key.hydrology() != Hydrology.RIVER
         && key.hydrology() != Hydrology.OCEAN && !coast) return false;
      if (biome.is(Tags.Biomes.IS_PLATEAU) && !rugged) return false;
      if (biome.is(Tags.Biomes.IS_WASTELAND)
         && terrain != ClimateLayers.TerrainKind.DESERT && !rugged
         && !(terrain == ClimateLayers.TerrainKind.PLAINS && trees == ClimateLayers.TreeCover.NONE)) return false;

      if (biome.is(Tags.Biomes.IS_DENSE_VEGETATION) && trees == ClimateLayers.TreeCover.NONE && !cave) return false;
      if (biome.is(Tags.Biomes.IS_SPARSE_VEGETATION) && trees == ClimateLayers.TreeCover.TROPICAL) return false;
      if (biome.is(Tags.Biomes.IS_WET) && !wetTerrain
         && trees == ClimateLayers.TreeCover.NONE && !cave) return false;
      if (biome.is(Tags.Biomes.IS_DRY) && terrain != ClimateLayers.TerrainKind.DESERT
         && !rugged && trees != ClimateLayers.TreeCover.NONE) return false;
      if (biome.is(Tags.Biomes.IS_LUSH) && !wetTerrain
         && terrain != ClimateLayers.TerrainKind.FOREST
         && trees == ClimateLayers.TreeCover.NONE && !coast && !cave) return false;
      if (biome.is(IS_CONIFEROUS) && trees == ClimateLayers.TreeCover.TROPICAL) return false;

      if (biome.is(Tags.Biomes.IS_MUSHROOM)
         && terrain != ClimateLayers.TerrainKind.JUNGLE && !cave) return false;
      if (biome.is(Tags.Biomes.IS_SPOOKY)
         && terrain != ClimateLayers.TerrainKind.FOREST && !cave) return false;
      if (biome.is(Tags.Biomes.IS_MAGICAL)
         && terrain != ClimateLayers.TerrainKind.FOREST
         && terrain != ClimateLayers.TerrainKind.JUNGLE && !rugged && !cave) return false;
      return true;
   }

   private static boolean isBop(Holder<Biome> biome) {
      return biome.unwrapKey().map(key -> "biomesoplenty".equals(key.identifier().getNamespace())).orElse(false);
   }

   private static boolean isBopRare(Holder<Biome> biome) {
      return isBop(biome) && biome.is(Tags.Biomes.IS_RARE);
   }

   /**
    * Some biomes are tagged as oceans but deliberately generate substantial
    * terrain above sea level. They cannot be selected in mapped ocean columns:
    * doing so bypasses the authoritative land mask through biome features and
    * repeats similarly shaped islands in every regional biome cell.
    */
   private static boolean createsUnmappedOceanLand(Holder<Biome> biome) {
      return biome.unwrapKey().map(key ->
         "biomeswevegone".equals(key.identifier().getNamespace())
            && "lush_stacks".equals(key.identifier().getPath())
      ).orElse(false);
   }

   /** BOP 21.1's primary EarthShape family, independent of incomplete common tags. */
   private static BopFamily bopFamily(Holder<Biome> biome) {
      return biome.unwrapKey().map(key -> {
         if (!"biomesoplenty".equals(key.identifier().getNamespace())) return BopFamily.NONE;
         return switch (key.identifier().getPath()) {
            case "cold_desert", "dryland", "lush_desert", "wasteland", "wasteland_steppe" -> BopFamily.DESERT;
            case "bayou", "bog", "floodplain", "marsh", "moor", "muskeg", "wetland" -> BopFamily.WETLAND;
            case "fungal_jungle", "rainforest", "tropics" -> BopFamily.JUNGLE;
            case "aspen_glade", "auroral_garden", "coniferous_forest", "dead_forest", "fir_clearing",
                 "forested_field", "jacaranda_glade", "maple_woods", "mediterranean_forest", "mystic_grove",
                 "old_growth_dead_forest", "old_growth_woodland", "ominous_woods", "pumpkin_patch",
                 "redwood_forest", "seasonal_forest", "snowblossom_grove", "snowy_coniferous_forest",
                 "snowy_fir_clearing", "snowy_maple_woods", "woodland" -> BopFamily.FOREST;
            case "highland", "hot_springs", "rocky_rainforest", "rocky_shrubland" -> BopFamily.HILLS;
            case "crag", "jade_cliffs", "volcano" -> BopFamily.MOUNTAIN;
            case "field", "grassland", "lavender_field", "lush_savanna", "orchard", "origin_valley",
                 "overgrown_greens", "pasture", "prairie", "scrubland", "shrubland", "tundra",
                 "volcanic_plains", "wintry_origin_valley" -> BopFamily.PLAINS;
            case "dune_beach", "gravel_beach" -> BopFamily.COAST;
            case "glowing_grotto", "spider_nest" -> BopFamily.CAVE;
            default -> BopFamily.NONE;
         };
      }).orElse(BopFamily.NONE);
   }

   private static boolean isMeadowLike(Holder<Biome> biome) {
      return biome.unwrapKey().map(key -> key.identifier().getPath().contains("meadow")).orElse(false);
   }

   private enum BopFamily {
      NONE, DESERT, WETLAND, JUNGLE, FOREST, HILLS, MOUNTAIN, PLAINS, COAST, CAVE
   }

   @SafeVarargs
   private static boolean isAny(Holder<Biome> biome, TagKey<Biome>... tags) {
      for (TagKey<Biome> tag : tags) if (tag != null && biome.is(tag)) return true;
      return false;
   }

   @SafeVarargs
   private static boolean hasForeignPrimary(Holder<Biome> biome, TagKey<Biome>... allowed) {
      for (TagKey<Biome> primary : List.of(
         Tags.Biomes.IS_DESERT, Tags.Biomes.IS_BADLANDS, Tags.Biomes.IS_SWAMP,
         Tags.Biomes.IS_JUNGLE, Tags.Biomes.IS_FOREST, Tags.Biomes.IS_TAIGA,
         Tags.Biomes.IS_PLAINS, Tags.Biomes.IS_SAVANNA, Tags.Biomes.IS_MOUNTAIN,
         Tags.Biomes.IS_MOUNTAIN_PEAK, Tags.Biomes.IS_MOUNTAIN_SLOPE
      )) {
         if (!biome.is(primary)) continue;
         boolean accepted = false;
         for (TagKey<Biome> tag : allowed) if (tag != null && primary.equals(tag)) accepted = true;
         if (!accepted) return true;
      }
      return false;
   }

   public static boolean hasAdditionalBiomes() {
      return !allBiomes.isEmpty();
   }

   private static void onServerStarting(MinecraftServer server) {
      Registry<Biome> registry = server.registryAccess().lookupOrThrow(Registries.BIOME);
      List<Holder<Biome>> found = new ArrayList<>();
      registry.listElements().forEach(holder -> {
         boolean modded = holder.unwrapKey()
            .map(key -> !"minecraft".equals(key.identifier().getNamespace()))
            .orElse(false);
         if (modded && (holder.is(Tags.Biomes.IS_OVERWORLD) || TfcBiomeLayers.isTfc(holder))) {
            found.add(holder);
         }
      });

      Map<TagKey<Biome>, CandidatePool> built = new HashMap<>();
      Set<ResourceKey<Biome>> categorized = new HashSet<>();
      for (TagKey<Biome> tag : CLASSIFICATION_TAGS) {
         List<Holder<Biome>> tagged = found.stream().filter(holder -> holder.is(tag)).toList();
         List<Holder<Biome>> snowy = tagged.stream().filter(holder -> holder.is(Tags.Biomes.IS_SNOWY)).toList();
         List<Holder<Biome>> nonSnowy = tagged.stream().filter(holder -> !holder.is(Tags.Biomes.IS_SNOWY)).toList();
         built.put(tag, new CandidatePool(tagged, snowy, nonSnowy));
         tagged.forEach(holder -> holder.unwrapKey().ifPresent(categorized::add));
      }

      pools = Map.copyOf(built);
      allBiomes = List.copyOf(found);
      layerPools.clear();
      tfcLayerPools.clear();
      appendToOverworldBiomeSource(server, found);

      long uncategorized = found.stream()
         .filter(holder -> holder.unwrapKey().map(key -> !categorized.contains(key)).orElse(true))
         .count();
      long tfcCount = found.stream().filter(TfcBiomeLayers::isTfc).count();
      EarthShape.LOGGER.info("[EarthShape] indexed {} additional overworld biomes for layer selection; tfc={}, uncategorized={}",
         found.size(), tfcCount, uncategorized);
   }

   private static void appendToOverworldBiomeSource(MinecraftServer server, List<Holder<Biome>> found) {
      if (found.isEmpty()) {
         return;
      }

      Registry<LevelStem> stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
      for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : stems.entrySet()) {
         if (!entry.getKey().equals(LevelStem.OVERWORLD)) {
            continue;
         }

         BiomeSource source = entry.getValue().generator().getBiomeSource();
         LinkedHashSet<Holder<Biome>> merged = new LinkedHashSet<>(source.possibleBiomes());
         merged.addAll(found);
         Set<Holder<Biome>> immutable = Collections.unmodifiableSet(merged);
         ((BiomeSourceAccessor) source).earthshape$setPossibleBiomes(() -> immutable);
      }
   }

   private static void onServerStopping(MinecraftServer ignored) {
      pools = Map.of();
      allBiomes = List.of();
      layerPools.clear();
      tfcLayerPools.clear();
   }

   private record CandidatePool(
      List<Holder<Biome>> all,
      List<Holder<Biome>> snowy,
      List<Holder<Biome>> nonSnowy
   ) {
   }

   private record LayerCandidatePool(
      List<Holder<Biome>> all,
      List<Holder<Biome>> bopRare,
      List<Holder<Biome>> regular
   ) {
   }
}
