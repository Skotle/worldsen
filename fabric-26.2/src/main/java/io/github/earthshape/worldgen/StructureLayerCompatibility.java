package io.github.earthshape.worldgen;

import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Layer-authoritative filter for climate-specific surface structures. */
public final class StructureLayerCompatibility {
   private StructureLayerCompatibility() {
   }

   public static boolean isAllowed(RegistryAccess registryAccess, Structure structure, ChunkPos chunkPos) {
      if (EarthShapeCompatibility.disablesWorldgen()
         || !(Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) {
         return true;
      }

      Identifier id = registryAccess.lookupOrThrow(Registries.STRUCTURE).getKey(structure);
      if (id == null) return true;
      StructureClimate climate = StructureClimate.fromPath(id.getPath());
      if (climate == StructureClimate.ANY) return true;

      int blockX = (chunkPos.x() << 4) + 8;
      int blockZ = (chunkPos.z() << 4) + 8;
      boolean land = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5;
      ClimateLayers layers = ClimateLayers.INSTANCE;
      ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
      ClimateLayers.TreeCover trees = layers.treeCover(blockX, blockZ);
      double temperature = layers.temperature(blockX, blockZ);

      return switch (climate) {
         case DESERT -> land && terrain == ClimateLayers.TerrainKind.DESERT;
         case BADLANDS -> land && terrain == ClimateLayers.TerrainKind.DESERT
            && layers.isMesaRegion(blockX, blockZ);
         case PLAINS -> land && (terrain == ClimateLayers.TerrainKind.PLAINS
            || terrain == ClimateLayers.TerrainKind.CITY
            || terrain == ClimateLayers.TerrainKind.SURROUNDING) && temperature < 0.45;
         case SAVANNA -> land && (terrain == ClimateLayers.TerrainKind.PLAINS
            || terrain == ClimateLayers.TerrainKind.HILLS) && temperature >= 0.375;
         case TAIGA -> land && terrain == ClimateLayers.TerrainKind.FOREST
            && trees != ClimateLayers.TreeCover.TROPICAL && temperature <= 0.15;
         case SNOWY -> land && (RiversMask.INSTANCE.isPermanentSouthernSnowLand(blockX, blockZ)
            || temperature <= (Double)EarthShapeServerConfig.SNOW_TEMPERATURE_THRESHOLD.get());
         case JUNGLE -> land && (terrain == ClimateLayers.TerrainKind.JUNGLE
            || terrain == ClimateLayers.TerrainKind.FOREST && trees == ClimateLayers.TreeCover.TROPICAL);
         case SWAMP -> land && terrain == ClimateLayers.TerrainKind.WETLAND;
         case FOREST -> land && terrain == ClimateLayers.TerrainKind.FOREST
            && trees != ClimateLayers.TreeCover.NONE;
         case OCEAN -> !land;
         case WARM_OCEAN -> !land && temperature > 0.15;
         case COLD_OCEAN -> !land && temperature <= 0.15;
         case ANY -> true;
      };
   }

   private enum StructureClimate {
      ANY, DESERT, BADLANDS, PLAINS, SAVANNA, TAIGA, SNOWY,
      JUNGLE, SWAMP, FOREST, OCEAN, WARM_OCEAN, COLD_OCEAN;

      private static StructureClimate fromPath(String path) {
         if (path.contains("warm_ocean") || path.contains("ocean_ruin_warm")) return WARM_OCEAN;
         if (path.contains("cold_ocean") || path.contains("frozen_ocean")
            || path.contains("ocean_ruin_cold")) return COLD_OCEAN;
         if (path.contains("badland") || path.contains("mesa")) return BADLANDS;
         if (path.contains("desert")) return DESERT;
         if (path.contains("savanna")) return SAVANNA;
         if (path.contains("taiga")) return TAIGA;
         if (path.contains("snowy") || path.contains("frozen") || path.contains("ice_")
            || path.startsWith("igloo")) return SNOWY;
         if (path.contains("jungle")) return JUNGLE;
         if (path.contains("swamp") || path.contains("mangrove")) return SWAMP;
         if (path.contains("plains")) return PLAINS;
         if (path.contains("forest")) return FOREST;
         if (path.contains("ocean") || path.contains("shipwreck")) return OCEAN;
         return ANY;
      }
   }
}
