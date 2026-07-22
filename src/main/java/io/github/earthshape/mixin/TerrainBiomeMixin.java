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
         // Biome lookup occurs for all vertical noise cells. Do not let the river column
         // below Y=48 fall back to a vanilla ocean biome.
         boolean sourceRiver = RiversMask.INSTANCE.isInlandRiver(blockX, blockZ);
         boolean riverMouth = blockY >= 48 && RiversMask.INSTANCE.isRiverMouth(blockX, blockZ);
         if (riverMouth) {
            callback.setReturnValue(this.oceanBiome(layers.temperature(blockX, blockZ), blockX, blockZ, (Holder<Biome>)callback.getReturnValue()));
            return;
         }

         if (sourceRiver) {
            callback.setReturnValue(this.findBiome(Biomes.RIVER, (Holder<Biome>)callback.getReturnValue()));
            return;
         }

         if (blockY < 48) {
            return;
         }

         if (RiversMask.INSTANCE.sampleLand(blockX, blockZ) >= 0.5 && !sourceRiver && isInlandWaterBiome((Holder<Biome>)callback.getReturnValue())) {
            callback.setReturnValue(this.mapTerrainBiome(layers, blockX, blockY, blockZ, (Holder<Biome>)callback.getReturnValue()));
         } else if (isVanillaBiome((Holder<Biome>)callback.getReturnValue())) {
            if (isVanillaRiver((Holder<Biome>)callback.getReturnValue())) {
               callback.setReturnValue(this.mapTerrainBiome(layers, blockX, blockY, blockZ, (Holder<Biome>)callback.getReturnValue()));
            } else if ((Boolean)EarthShapeServerConfig.OCEAN_TEMPERATURE_ENABLED.get() && RiversMask.INSTANCE.sampleLand(blockX, blockZ) < 0.25) {
               long layerPoint = warpedLayerPoint(blockX, blockZ);
               double temperature = layers.temperature(unpackX(layerPoint), unpackZ(layerPoint));
               callback.setReturnValue(this.oceanBiome(temperature, blockX, blockZ, (Holder<Biome>)callback.getReturnValue()));
            } else if ((Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) {
               callback.setReturnValue(this.mapTerrainBiome(layers, blockX, blockY, blockZ, (Holder<Biome>)callback.getReturnValue()));
            }
         }
      }
   }

   private Holder<Biome> mapTerrainBiome(ClimateLayers layers, int blockX, int blockY, int blockZ, Holder<Biome> fallback) {
      long layerPoint = warpedLayerPoint(blockX, blockZ);
      int layerX = unpackX(layerPoint);
      int layerZ = unpackZ(layerPoint);
      ClimateLayers.TerrainKind terrain = this.surfaceTerrain(layers, blockX, blockZ);
      double temperature = layers.temperature(layerX, layerZ);
      boolean snowAllowed = allowsSnow(blockY, temperature);
      int region = regionalVariant(blockX, blockZ);
      boolean nextToLayerRiver = RiversMask.INSTANCE.isNearInlandRiver(blockX, blockZ, 32);
      if (!nextToLayerRiver && isCoastalLand(blockX, blockZ)) {
         if (terrain == ClimateLayers.TerrainKind.HILLS || terrain == ClimateLayers.TerrainKind.MOUNTAIN) {
            return this.findBiome(Biomes.STONY_SHORE, fallback);
         }

         boolean sandyBeach = terrain == ClimateLayers.TerrainKind.DESERT
            || terrain == ClimateLayers.TerrainKind.PLAINS && temperature > 0.2 && region % 5 == 0;
         if (sandyBeach) {
            return this.findBiome(snowAllowed ? Biomes.SNOWY_BEACH : Biomes.BEACH, fallback);
         }
      }
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
         case MOUNTAIN -> snowAllowed
         ? this.findBiome(temperature < -0.55 ? Biomes.FROZEN_PEAKS : Biomes.JAGGED_PEAKS, fallback)
         : this.findBiome(Biomes.STONY_PEAKS, fallback);
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

   private static boolean allowsSnow(int blockY, double temperature) {
      return blockY >= (Integer)EarthShapeServerConfig.SNOW_ALTITUDE_BLOCKS.get()
         || (Boolean)EarthShapeServerConfig.TUNDRA_TEMPERATURE_ENABLED.get() && temperature <= (Double)EarthShapeServerConfig.SNOW_TEMPERATURE_THRESHOLD.get();
   }

   private static long warpedLayerPoint(int blockX, int blockZ) {
      if (!(Boolean)EarthShapeServerConfig.BIOME_BOUNDARY_WARP_ENABLED.get()) {
         return packPoint(blockX, blockZ);
      } else {
         int strength = Math.min((Integer)EarthShapeServerConfig.BIOME_BOUNDARY_WARP_BLOCKS.get(), Math.max(4, RiversMask.INSTANCE.blocksPerPixel() * 3 / 4));
         if (strength == 0) {
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
