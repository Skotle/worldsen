package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate.Sampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MultiNoiseBiomeSource.class})
public final class TerrainBiomeMixin {
   @Inject(
      method = {"getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void earthshape$chooseTerrainBiome(int quartX, int quartY, int quartZ, Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
      int blockX = quartX << 2;
      int blockY = quartY << 2;
      int blockZ = quartZ << 2;
      if (!EarthShapeCompatibility.disablesWorldgen()) {
         ClimateLayers layers = ClimateLayers.INSTANCE;
         boolean sourceRiver = blockY >= 48 && RiversMask.INSTANCE.isInlandRiver(blockX, blockZ);

         // Priority 1: a verified source-river centreline always wins over every terrain
         // and climate layer, including non-vanilla biome providers.
         if (sourceRiver) {
            callback.setReturnValue(this.findBiome(Biomes.RIVER, (Holder<Biome>)callback.getReturnValue()));
            return;
         }
         // Structures and underground generation commonly query the biome at low Y values.
         // Keep those queries owned by the original biome source so modpack structure biome
         // predicates continue to work; EarthShape controls only the visible surface layer.
         if (blockY < 48) return;
         if (RiversMask.INSTANCE.sampleLand(blockX, blockZ) >= 0.5 && !sourceRiver && isInlandWaterBiome((Holder<Biome>)callback.getReturnValue())) {
            callback.setReturnValue(this.mapTerrainBiome(layers, blockX, blockY, blockZ, (Holder<Biome>)callback.getReturnValue()));
         } else if (isVanillaBiome((Holder<Biome>)callback.getReturnValue())) {
            if (isVanillaRiver((Holder<Biome>)callback.getReturnValue())) {
               callback.setReturnValue(this.mapTerrainBiome(layers, blockX, blockY, blockZ, (Holder<Biome>)callback.getReturnValue()));
            } else if ((Boolean)EarthShapeServerConfig.OCEAN_TEMPERATURE_ENABLED.get() && RiversMask.INSTANCE.sampleLand(blockX, blockZ) < 0.25) {
               // The full temperature layer includes the ocean, so this must use its pixel
               // value rather than falling back to a latitude-only estimate.
               double temperature = layers.temperature(blockX, blockZ);
               callback.setReturnValue(this.oceanBiome(temperature, blockX, blockZ, (Holder<Biome>)callback.getReturnValue()));
            } else {
               if ((Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) {
                  callback.setReturnValue(this.mapTerrainBiome(layers, blockX, blockY, blockZ, (Holder<Biome>)callback.getReturnValue()));
               }
            }
         }
      }
   }

   private Holder<Biome> mapTerrainBiome(ClimateLayers layers, int blockX, int blockY, int blockZ, Holder<Biome> fallback) {
      ClimateLayers.TerrainKind terrain = this.surfaceTerrain(layers, blockX, blockZ);
      // Heightmap priority remains in density generation: it controls the actual terrain
      // height and relief without replacing surface biomes with stone mountain variants.
      // terrain.bmp supplies surface classes.  trees.bmp only fills a generic plains
      // category, so it cannot leak across a deliberate desert, wetland or mountain boundary.
      if (terrain == ClimateLayers.TerrainKind.PLAINS) {
         ClimateLayers.TreeCover trees = layers.treeCover(blockX, blockZ);
         if (trees == ClimateLayers.TreeCover.TROPICAL) {
            terrain = ClimateLayers.TerrainKind.JUNGLE;
         } else if (trees == ClimateLayers.TreeCover.TEMPERATE) {
            terrain = ClimateLayers.TerrainKind.FOREST;
         }
      }
      // Priority 5: temperature selects the climate variant within the winning land class.
      double temperature = layers.temperature(blockX, blockZ);
      int region = regionalVariant(blockX, blockZ);
      boolean nextToLayerRiver = RiversMask.INSTANCE.isNearInlandRiver(blockX, blockZ, 32);
      if (!nextToLayerRiver && isCoastalLand(blockX, blockZ)) {
         if (terrain == ClimateLayers.TerrainKind.HILLS || terrain == ClimateLayers.TerrainKind.MOUNTAIN) {
            return this.findBiome(Biomes.STONY_SHORE, fallback);
         }

         boolean sandyBeach = terrain == ClimateLayers.TerrainKind.DESERT
            || terrain == ClimateLayers.TerrainKind.PLAINS && temperature > 0.2 && region % 5 == 0;
         if (sandyBeach) {
            return this.findBiome(temperature < -0.25 ? Biomes.SNOWY_BEACH : Biomes.BEACH, fallback);
         }
      }
      return switch (terrain) {
         case DESERT -> layers.isMesaRegion(blockX, blockZ)
         ? this.findBiome(region % 10 == 0 ? Biomes.ERODED_BADLANDS : (region % 5 == 0 ? Biomes.WOODED_BADLANDS : Biomes.BADLANDS), fallback)
         : this.findBiome(Biomes.DESERT, fallback);
         case WETLAND -> this.findBiome(temperature > 0.3 ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP, fallback);
         case FOREST -> this.forestBiome(temperature, region, fallback);
         case JUNGLE -> this.findBiome(region % 12 == 0 ? Biomes.BAMBOO_JUNGLE : (region % 6 == 0 ? Biomes.SPARSE_JUNGLE : Biomes.JUNGLE), fallback);
         case HILLS -> temperature < -0.45
         ? this.findBiome(Biomes.SNOWY_SLOPES, fallback)
         : (
            temperature < -0.2
               ? this.findBiome(Biomes.GROVE, fallback)
               : (
                  temperature > 0.45
                     ? this.findBiome(Biomes.WINDSWEPT_SAVANNA, fallback)
                     : this.findBiome(region % 5 == 0 ? Biomes.WINDSWEPT_FOREST : Biomes.WINDSWEPT_HILLS, fallback)
               )
         );
         case MOUNTAIN -> temperature < -0.55
         ? this.findBiome(Biomes.FROZEN_PEAKS, fallback)
         : (temperature < -0.25 ? this.findBiome(Biomes.JAGGED_PEAKS, fallback) : this.findBiome(Biomes.STONY_PEAKS, fallback));
         case PLAINS, CITY, SURROUNDING -> this.plainsBiome(temperature, region, fallback);
         case WATER -> this.oceanBiome(temperature, blockX, blockZ, fallback);
      };
   }

   private Holder<Biome> forestBiome(double temperature, int region, Holder<Biome> fallback) {
      if (temperature < -0.55) {
         return this.findBiome(Biomes.SNOWY_TAIGA, fallback);
      } else {
         return temperature < -0.25
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

   private Holder<Biome> plainsBiome(double temperature, int region, Holder<Biome> fallback) {
      if (temperature < -0.55) {
         return this.findBiome(region % 17 == 0 ? Biomes.ICE_SPIKES : Biomes.SNOWY_PLAINS, fallback);
      } else if (temperature < -0.3) {
         return this.findBiome(Biomes.SNOWY_PLAINS, fallback);
      } else {
         return temperature > 0.45
            ? this.findBiome(region % 6 == 0 ? Biomes.SAVANNA_PLATEAU : Biomes.SAVANNA, fallback)
            : this.findBiome(region % 16 == 0 ? Biomes.SUNFLOWER_PLAINS : Biomes.PLAINS, fallback);
      }
   }

   private Holder<Biome> oceanBiome(double temperature, int blockX, int blockZ, Holder<Biome> fallback) {
      boolean deep = isOpenOcean(blockX, blockZ);
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

   private Holder<Biome> findBiome(ResourceKey<Biome> key, Holder<Biome> fallback) {
      return ((MultiNoiseBiomeSource)(Object)this).possibleBiomes().stream().filter(holder -> holder.is(key)).findFirst().orElse(fallback);
   }

   /** terrain.bmp chooses the land family, but forest/jungle requires actual trees.bmp cover. */
   private ClimateLayers.TerrainKind surfaceTerrain(ClimateLayers layers, int blockX, int blockZ) {
      ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
      ClimateLayers.TreeCover trees = layers.treeCover(blockX, blockZ);
      if (terrain == ClimateLayers.TerrainKind.FOREST || terrain == ClimateLayers.TerrainKind.JUNGLE || terrain == ClimateLayers.TerrainKind.PLAINS) {
         if (trees == ClimateLayers.TreeCover.TROPICAL) return ClimateLayers.TerrainKind.JUNGLE;
         if (trees == ClimateLayers.TreeCover.TEMPERATE) return ClimateLayers.TerrainKind.FOREST;
         if (terrain == ClimateLayers.TerrainKind.FOREST || terrain == ClimateLayers.TerrainKind.JUNGLE) return ClimateLayers.TerrainKind.PLAINS;
      }
      return terrain;
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
