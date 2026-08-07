package io.github.earthshape.worldgen;

import io.github.earthshape.EarthShape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

   private AdditionalBiomeRegistry() {
   }

   public static void register() {
      NeoForge.EVENT_BUS.addListener(AdditionalBiomeRegistry::onServerAboutToStart);
      NeoForge.EVENT_BUS.addListener(AdditionalBiomeRegistry::onServerStopping);
   }

   public static Holder<Biome> select(
      TagKey<Biome> tag,
      int variant,
      boolean allowSnow,
      boolean preferSnow
   ) {
      CandidatePool pool = pools.get(tag);
      if (pool == null) {
         return null;
      }

      List<Holder<Biome>> candidates;
      if (preferSnow && !pool.snowy().isEmpty()) {
         candidates = pool.snowy();
      } else if (!allowSnow && !pool.nonSnowy().isEmpty()) {
         candidates = pool.nonSnowy();
      } else {
         candidates = pool.all();
      }

      int saltedVariant = variant ^ tag.location().hashCode() * 0x45d9f3b;
      return candidates.isEmpty() ? null : candidates.get(Math.floorMod(saltedVariant, candidates.size()));
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
   }

   private record CandidatePool(
      List<Holder<Biome>> all,
      List<Holder<Biome>> snowy,
      List<Holder<Biome>> nonSnowy
   ) {
   }
}
