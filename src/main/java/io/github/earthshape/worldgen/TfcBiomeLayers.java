package io.github.earthshape.worldgen;

import io.github.earthshape.map.ClimateLayers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Maps TFC's public biome convention tags to EarthShape's finite layer model.
 *
 * <p>TFC has many landform-specific tags that do not exist in the common
 * convention set. They are deliberately grouped here instead of enumerating
 * biome ids, so a TFC data pack can add a biome to a published TFC tag and it
 * automatically receives the same EarthShape placement rule.</p>
 */
final class TfcBiomeLayers {
   private static final String TFC = "tfc";
   private static final String COMMON = "c";

   private static final TagKey<Biome> AQUATIC = common("is_aquatic");
   private static final TagKey<Biome> AQUATIC_ICY = common("is_aquatic_icy");
   private static final TagKey<Biome> BADLANDS = common("is_badlands");
   private static final TagKey<Biome> BEACH = common("is_beach");
   private static final TagKey<Biome> CAVE_LAKE = common("is_cave_lake");
   private static final TagKey<Biome> COLD = common("is_cold/overworld");
   private static final TagKey<Biome> DEEP_OCEAN = common("is_deep_ocean");
   private static final TagKey<Biome> DRY = common("is_dry");
   private static final TagKey<Biome> HILL = common("is_hill");
   private static final TagKey<Biome> HOT = common("is_hot/overworld");
   private static final TagKey<Biome> ICY = common("is_icy");
   private static final TagKey<Biome> LAKE = common("is_lake");
   private static final TagKey<Biome> MOUNTAIN = common("is_mountain");
   private static final TagKey<Biome> OCEAN = common("is_ocean");
   private static final TagKey<Biome> PLAINS = common("is_plains");
   private static final TagKey<Biome> PLATEAU = common("is_plateau");
   private static final TagKey<Biome> RIVER = common("is_river");
   private static final TagKey<Biome> SANDY = common("is_sandy");
   private static final TagKey<Biome> SHALLOW_OCEAN = common("is_shallow_ocean");
   private static final TagKey<Biome> SNOWY = common("is_snowy");
   private static final TagKey<Biome> SPARSE = common("is_sparse_vegetation/overworld");
   private static final TagKey<Biome> STONY_SHORE = common("is_stony_shores");
   private static final TagKey<Biome> SURFACE_LAKE = common("is_surface_lake");
   private static final TagKey<Biome> SWAMP = common("is_swamp");
   private static final TagKey<Biome> VOLCANIC = common("is_volcanic");
   private static final TagKey<Biome> WET = common("is_wet/overworld");
   private static final TagKey<Biome> WINDSWEPT = common("is_windswept");

   private static final TagKey<Biome> TFC_OCEAN = tfc("is_ocean");
   private static final TagKey<Biome> TFC_RIVER = tfc("is_river");
   private static final TagKey<Biome> TFC_BURREN = tfc("is_burren");
   private static final TagKey<Biome> TFC_CENOTE = tfc("is_cenote");
   private static final TagKey<Biome> TFC_COASTAL_CLIFFS = tfc("is_coastal_cliffs");
   private static final TagKey<Biome> TFC_CONTINENTAL_MOUNTAINS = tfc("is_continental_mountains");
   private static final TagKey<Biome> TFC_DOLINES = tfc("is_dolines");
   private static final TagKey<Biome> TFC_EXTREME_DOLINES = tfc("is_extreme_dolines");
   private static final TagKey<Biome> TFC_GLACIATED = tfc("is_glaciated");
   private static final TagKey<Biome> TFC_ICE_SHEET = tfc("is_ice_sheet");
   private static final TagKey<Biome> TFC_ISLAND = tfc("is_isolated_island");
   private static final TagKey<Biome> TFC_KARST = tfc("is_karst");
   private static final TagKey<Biome> TFC_NONVOLCANIC_MOUNTAINS = tfc("is_nonvolcanic_mountains");
   private static final TagKey<Biome> TFC_OCEANIC_MOUNTAINS = tfc("is_oceanic_mountains");
   private static final TagKey<Biome> TFC_RIFT = tfc("is_rift");
   private static final TagKey<Biome> TFC_SALT_MARSH = tfc("is_salt_marsh");
   private static final TagKey<Biome> TFC_SHIELD_VOLCANO = tfc("is_shield_volcano");
   private static final TagKey<Biome> TFC_SHILIN = tfc("is_shilin");
   private static final TagKey<Biome> TFC_TOWER_KARST = tfc("is_tower_karst");
   private static final TagKey<Biome> TFC_VOLCANIC_MOUNTAINS = tfc("is_volcanic_mountains");

   private TfcBiomeLayers() {
   }

   static boolean isTfc(Holder<Biome> biome) {
      return biome.unwrapKey().map(key -> TFC.equals(key.location().getNamespace())).orElse(false);
   }

   static boolean matches(AdditionalBiomeRegistry.LayerKey key, Holder<Biome> biome) {
      if (!matchesClimate(key, biome)) return false;

      return matchesHydrologyAndLandform(key, biome);
   }

   /**
    * Keeps the map's hydrology and terrain.bmp family mandatory while relaxing
    * only the temperature tag gate. This is used when a TFC data pack has no
    * climate-tagged member for an otherwise valid mapped landform; it must not
    * turn a mapped desert, mountain, river, or shore back into generic land.
    */
   static boolean matchesFallback(AdditionalBiomeRegistry.LayerKey key, Holder<Biome> biome) {
      return matchesHydrologyAndLandform(key, biome);
   }

   private static boolean matchesHydrologyAndLandform(AdditionalBiomeRegistry.LayerKey key, Holder<Biome> biome) {
      boolean cave = is(biome, CAVE_LAKE);
      boolean river = is(biome, RIVER, TFC_RIVER);
      boolean coast = is(biome, BEACH, STONY_SHORE, TFC_COASTAL_CLIFFS);
      boolean ocean = is(biome, OCEAN, TFC_OCEAN, DEEP_OCEAN, SHALLOW_OCEAN, TFC_ISLAND);

      return switch (key.hydrology()) {
         case CAVE -> cave;
         case RIVER -> river;
         case OCEAN -> ocean && !coast && !river && !cave;
         case COAST -> coast || (ocean && !is(biome, OCEAN, DEEP_OCEAN, SHALLOW_OCEAN) && !river);
         case LAND -> !cave && !river && !coast && !ocean && matchesLand(key, biome);
      };
   }

   private static boolean matchesClimate(AdditionalBiomeRegistry.LayerKey key, Holder<Biome> biome) {
      int band = key.temperatureBand();
      // terrain.bmp is authoritative for explicit landform layers. In
      // particular, the HOI4 map contains cold high-altitude deserts; rejecting
      // TFC's hot/dry tags there used to empty the desert pool and make it fall
      // back to a neutral plains biome.
      boolean mappedDryland = key.terrain() == ClimateLayers.TerrainKind.DESERT
         && is(biome, BADLANDS, DRY, SANDY, TFC_BURREN);
      if (mappedDryland) return true;
      boolean ice = is(biome, ICY, SNOWY, AQUATIC_ICY, TFC_GLACIATED, TFC_ICE_SHEET);
      if (ice && (!key.snowAllowed() || band > 3)) return false;
      if (is(biome, COLD) && band > 3) return false;
      if (is(biome, HOT) && band < 5) return false;
      return !is(biome, DRY) || key.terrain() == ClimateLayers.TerrainKind.DESERT
         || key.terrain() == ClimateLayers.TerrainKind.HILLS
         || key.terrain() == ClimateLayers.TerrainKind.MOUNTAIN
         || key.trees() == ClimateLayers.TreeCover.NONE;
   }

   private static boolean matchesLand(AdditionalBiomeRegistry.LayerKey key, Holder<Biome> biome) {
      boolean mountain = is(biome, MOUNTAIN, TFC_CONTINENTAL_MOUNTAINS, TFC_NONVOLCANIC_MOUNTAINS,
         TFC_OCEANIC_MOUNTAINS, TFC_VOLCANIC_MOUNTAINS);
      boolean hills = is(biome, HILL, PLATEAU, WINDSWEPT, TFC_CENOTE, TFC_DOLINES,
         TFC_EXTREME_DOLINES, TFC_KARST, TFC_SHILIN, TFC_TOWER_KARST, TFC_RIFT);
      boolean volcanic = is(biome, VOLCANIC, TFC_SHIELD_VOLCANO, TFC_VOLCANIC_MOUNTAINS);
      boolean lakeOrWetland = is(biome, SWAMP, WET, LAKE, SURFACE_LAKE, TFC_SALT_MARSH);
      boolean dryland = is(biome, BADLANDS, DRY, SANDY, TFC_BURREN);

      return switch (key.terrain()) {
         case DESERT -> dryland;
         case WETLAND -> lakeOrWetland;
         case HILLS -> hills || (volcanic && !mountain);
         case MOUNTAIN -> mountain || (volcanic && hills);
         case PLAINS, CITY, SURROUNDING -> is(biome, PLAINS) || (!mountain && !hills && !lakeOrWetland && !dryland);
         case FOREST -> !dryland && !lakeOrWetland && !mountain && !hills
            && key.trees() != ClimateLayers.TreeCover.NONE && !is(biome, SPARSE);
         case JUNGLE -> !dryland && !mountain && !hills
            && key.trees() == ClimateLayers.TreeCover.TROPICAL && key.temperatureBand() >= 5;
         case WATER -> false;
      };
   }

   @SafeVarargs
   private static boolean is(Holder<Biome> biome, TagKey<Biome>... tags) {
      for (TagKey<Biome> tag : tags) if (biome.is(tag)) return true;
      return false;
   }

   private static TagKey<Biome> common(String path) {
      return tag(COMMON, path);
   }

   private static TagKey<Biome> tfc(String path) {
      return tag(TFC, path);
   }

   private static TagKey<Biome> tag(String namespace, String path) {
      return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(namespace, path));
   }
}
