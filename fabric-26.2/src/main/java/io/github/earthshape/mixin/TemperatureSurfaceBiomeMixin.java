package io.github.earthshape.mixin;

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

@Mixin(
   value = {MultiNoiseBiomeSource.class},
   priority = 2000
)
public final class TemperatureSurfaceBiomeMixin {
   @Inject(
      method = {"getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void earthshape$applySurfaceTemperature(int quartX, int quartY, int quartZ, Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
      int x = quartX << 2;
      int y = quartY << 2;
      int z = quartZ << 2;
      if (y >= 48 && isVanilla((Holder<Biome>)callback.getReturnValue())) {
         ClimateLayers layers = ClimateLayers.INSTANCE;
         if (RiversMask.INSTANCE.sampleLand(x, z) < 0.5) {
            callback.setReturnValue(this.ocean(layers.temperature(x, z), x, z, (Holder<Biome>)callback.getReturnValue()));
         } else {
            double temperature = layers.temperature(x, z);
            if (!RiversMask.INSTANCE.isInlandRiver(x, z)) {
               callback.setReturnValue(this.land(layers, layers.terrainKind(x, z), temperature, x, z, (Holder<Biome>)callback.getReturnValue()));
            }
         }
      }
   }

   private Holder<Biome> land(ClimateLayers layers, ClimateLayers.TerrainKind terrain, double t, int x, int z, Holder<Biome> fallback) {
      int band = temperatureBand(t);
      boolean frozenPeaksAllowed = layers.isUltraMountain(x, z) || layers.isPolarTemperatureZone(x, z);

      return switch (terrain) {
         case DESERT -> this.biome(
         band <= 1
            ? Biomes.SNOWY_PLAINS
            : (band <= 3 ? Biomes.PLAINS : (band <= 5 ? Biomes.SAVANNA : (band == 6 ? Biomes.SAVANNA_PLATEAU : (band == 7 ? Biomes.BADLANDS : Biomes.DESERT)))),
         fallback
      );
         case WETLAND -> this.biome(band <= 1 ? Biomes.SNOWY_PLAINS : (band >= 5 ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP), fallback);
         case FOREST -> this.forest(band, fallback);
         case JUNGLE -> this.biome(
         band <= 1
            ? Biomes.SNOWY_TAIGA
            : (
               band <= 2
                  ? Biomes.TAIGA
                  : (
                     band <= 3
                        ? Biomes.BIRCH_FOREST
                        : (
                           band <= 4
                              ? Biomes.FOREST
                              : (band == 5 ? Biomes.JUNGLE : (band == 6 ? Biomes.SPARSE_JUNGLE : (band == 7 ? Biomes.WOODED_BADLANDS : Biomes.DESERT)))
                        )
                  )
            ),
         fallback
      );
         case HILLS -> this.biome(
         band == 0
            ? Biomes.JAGGED_PEAKS
            : (
               band == 1
                  ? Biomes.SNOWY_SLOPES
                  : (
                     band == 2
                        ? Biomes.GROVE
                        : (
                           band <= 4
                              ? Biomes.WINDSWEPT_HILLS
                              : (
                                 band == 5
                                    ? Biomes.SPARSE_JUNGLE
                                    : (band == 6 ? Biomes.WINDSWEPT_SAVANNA : (band == 7 ? Biomes.ERODED_BADLANDS : Biomes.DESERT))
                              )
                        )
                  )
            ),
         fallback
      );
         case MOUNTAIN -> this.biome(frozenPeaksAllowed ? Biomes.FROZEN_PEAKS : Biomes.STONY_PEAKS, fallback);
         case PLAINS, CITY, SURROUNDING -> this.plains(band, fallback);
         case WATER -> this.ocean(band, x, z, fallback);
      };
   }

   private Holder<Biome> forest(int band, Holder<Biome> fallback) {
      return this.biome(
         band == 0
            ? Biomes.ICE_SPIKES
            : (
               band == 1
                  ? Biomes.SNOWY_TAIGA
                  : (
                     band == 2
                        ? Biomes.OLD_GROWTH_SPRUCE_TAIGA
                        : (
                           band == 3
                              ? Biomes.OLD_GROWTH_PINE_TAIGA
                              : (
                                 band == 4
                                    ? Biomes.FOREST
                                    : (band == 5 ? Biomes.JUNGLE : (band == 6 ? Biomes.SPARSE_JUNGLE : (band == 7 ? Biomes.WOODED_BADLANDS : Biomes.DESERT)))
                              )
                        )
                  )
            ),
         fallback
      );
   }

   private Holder<Biome> plains(int band, Holder<Biome> fallback) {
      return this.biome(
         band == 0
            ? Biomes.ICE_SPIKES
            : (
               band == 1
                  ? Biomes.SNOWY_PLAINS
                  : (
                     band == 2
                        ? Biomes.TAIGA
                        : (
                           band == 3
                              ? Biomes.BIRCH_FOREST
                              : (
                                 band == 4
                                    ? Biomes.PLAINS
                                    : (band == 5 ? Biomes.SPARSE_JUNGLE : (band == 6 ? Biomes.SAVANNA : (band == 7 ? Biomes.BADLANDS : Biomes.DESERT)))
                              )
                        )
                  )
            ),
         fallback
      );
   }

   private Holder<Biome> ocean(double t, int x, int z, Holder<Biome> fallback) {
      return this.ocean(temperatureBand(t), x, z, fallback);
   }

   private Holder<Biome> ocean(int band, int x, int z, Holder<Biome> fallback) {
      boolean deep = isOpenOcean(x, z);
      ResourceKey<Biome> biome = switch (band) {
         // Warm Ocean has no vanilla deep counterpart. Every other temperature
         // band uses its deep variant only in genuinely open ocean.
         case 0, 1 -> deep ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN;
         case 2, 3 -> deep ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN;
         case 4 -> deep ? Biomes.DEEP_OCEAN : Biomes.OCEAN;
         case 5, 6 -> deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN;
         default -> Biomes.WARM_OCEAN;
      };
      return this.biome(biome, fallback);
   }

   private static int temperatureBand(double t) {
      if (t < -0.875) {
         return 0;
      } else if (t < -0.625) {
         return 1;
      } else if (t < -0.375) {
         return 2;
      } else if (t < -0.125) {
         return 3;
      } else if (t < 0.125) {
         return 4;
      } else if (t < 0.375) {
         return 5;
      } else if (t < 0.625) {
         return 6;
      } else {
         return t < 0.875 ? 7 : 8;
      }
   }

   private Holder<Biome> biome(ResourceKey<Biome> key, Holder<Biome> fallback) {
      return ((MultiNoiseBiomeSource)(Object)this).possibleBiomes().stream().filter(holder -> holder.is(key)).findFirst().orElse(fallback);
   }

   private static boolean isOpenOcean(int blockX, int blockZ) {
      int distance = 180;
      return RiversMask.INSTANCE.sampleLand(blockX - distance, blockZ) < 0.25
         && RiversMask.INSTANCE.sampleLand(blockX + distance, blockZ) < 0.25
         && RiversMask.INSTANCE.sampleLand(blockX, blockZ - distance) < 0.25
         && RiversMask.INSTANCE.sampleLand(blockX, blockZ + distance) < 0.25;
   }

   private static boolean isVanilla(Holder<Biome> biome) {
      return biome.unwrapKey().map(key -> "minecraft".equals(key.identifier().getNamespace())).orElse(false);
   }
}
