package io.github.earthshape.worldgen;

import io.github.earthshape.EarthShape;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Makes registered modded overworld biomes available to EarthShape without
 * activating TerraBlender's region selector. Candidate pools are immutable
 * after server start, so chunk workers only perform constant-time reads.
 */
public final class AdditionalBiomeRegistry {
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
   private static final ConcurrentHashMap<LayerKey, List<Holder<Biome>>> layerPools = new ConcurrentHashMap<>();

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
      NeoForge.EVENT_BUS.addListener(AdditionalBiomeRegistry::onServerAboutToStart);
      NeoForge.EVENT_BUS.addListener(AdditionalBiomeRegistry::onServerStopping);
   }

   public static Holder<Biome> select(LayerKey key, int variant) {
      List<Holder<Biome>> candidates = layerPools.computeIfAbsent(key, AdditionalBiomeRegistry::buildLayerPool);
      if (candidates.isEmpty()) return null;
      int saltedVariant = variant ^ key.hashCode() * 0x45d9f3b;
      return candidates.get(Math.floorMod(saltedVariant, candidates.size()));
   }

   private static List<Holder<Biome>> buildLayerPool(LayerKey key) {
      return allBiomes.stream().filter(candidate -> matchesLayerCombination(key, candidate)).toList();
   }

   private static boolean matchesLayerCombination(LayerKey key, Holder<Biome> biome) {
      if (!matchesTemperature(key, biome)) return false;
      return switch (key.hydrology()) {
         case CAVE -> (biome.is(Tags.Biomes.IS_CAVE) || biome.is(Tags.Biomes.IS_UNDERGROUND))
            && !isAny(biome, Tags.Biomes.IS_OCEAN, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_RIVER);
         case RIVER -> biome.is(Tags.Biomes.IS_RIVER)
            && !isAny(biome, Tags.Biomes.IS_OCEAN, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_CAVE);
         case OCEAN -> biome.is(Tags.Biomes.IS_OCEAN)
            && !isAny(biome, Tags.Biomes.IS_RIVER, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_CAVE);
         case COAST -> biome.is(Tags.Biomes.IS_BEACH)
            && !isAny(biome, Tags.Biomes.IS_OCEAN, Tags.Biomes.IS_RIVER, Tags.Biomes.IS_CAVE);
         case LAND -> matchesLandFamily(key, biome)
            && !isAny(biome, Tags.Biomes.IS_OCEAN, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_RIVER, Tags.Biomes.IS_CAVE, Tags.Biomes.IS_UNDERGROUND);
      };
   }

   private static boolean matchesLandFamily(LayerKey key, Holder<Biome> biome) {
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
      if (band <= 2 && biome.is(Tags.Biomes.IS_HOT)) return false;
      if (band >= 6 && (biome.is(Tags.Biomes.IS_COLD) || biome.is(Tags.Biomes.IS_SNOWY))) return false;
      if (band <= 1 && key.snowAllowed() && key.hydrology() == Hydrology.LAND
         && !biome.is(Tags.Biomes.IS_SNOWY) && !biome.is(Tags.Biomes.IS_COLD)) return false;
      if ((key.terrain() == ClimateLayers.TerrainKind.DESERT || key.terrain() == ClimateLayers.TerrainKind.JUNGLE)
         && band < 4) return false;
      return true;
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

   private static void onServerAboutToStart(ServerAboutToStartEvent event) {
      Registry<Biome> registry = event.getServer().registryAccess().registryOrThrow(Registries.BIOME);
      List<Holder<Biome>> found = new ArrayList<>();
      registry.holders().forEach(holder -> {
         boolean modded = holder.unwrapKey()
            .map(key -> !"minecraft".equals(key.location().getNamespace()))
            .orElse(false);
         if (modded && holder.is(Tags.Biomes.IS_OVERWORLD)) {
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
      appendToOverworldBiomeSource(event, found);

      long uncategorized = found.stream()
         .filter(holder -> holder.unwrapKey().map(key -> !categorized.contains(key)).orElse(true))
         .count();
      EarthShape.LOGGER.info(
         "[EarthShape] indexed {} additional overworld biomes for layer selection; uncategorized={}",
         found.size(),
         uncategorized
      );
   }

   private static void appendToOverworldBiomeSource(ServerAboutToStartEvent event, List<Holder<Biome>> found) {
      if (found.isEmpty()) {
         return;
      }

      Registry<LevelStem> stems = event.getServer().registryAccess().registryOrThrow(Registries.LEVEL_STEM);
      for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : stems.entrySet()) {
         if (!entry.getKey().equals(LevelStem.OVERWORLD)) {
            continue;
         }

         BiomeSource source = entry.getValue().generator().getBiomeSource();
         LinkedHashSet<Holder<Biome>> merged = new LinkedHashSet<>(source.possibleBiomes());
         merged.addAll(found);
         Set<Holder<Biome>> immutable = Collections.unmodifiableSet(merged);
         source.possibleBiomes = () -> immutable;
      }
   }

   private static void onServerStopping(ServerStoppingEvent ignored) {
      pools = Map.of();
      allBiomes = List.of();
      layerPools.clear();
   }

   private record CandidatePool(
      List<Holder<Biome>> all,
      List<Holder<Biome>> snowy,
      List<Holder<Biome>> nonSnowy
   ) {
   }
}
