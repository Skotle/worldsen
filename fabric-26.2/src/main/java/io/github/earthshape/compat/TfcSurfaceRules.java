package io.github.earthshape.compat;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.SurfaceRules;

/** Surface material bridge for TFC biomes selected by EarthShape map layers. */
public final class TfcSurfaceRules {
   private static final List<String> DESERT_BIOMES = List.of(
      "badlands", "burren_badlands", "burren_badlands_tall", "buttes", "dune_sea", "grassy_dunes",
      "hoodoos", "mesas", "rocky_plateau", "salt_flats", "stair_step_canyons", "whorled_canyons"
   );

   private TfcSurfaceRules() {}

   @SuppressWarnings("unchecked")
   public static SurfaceRules.RuleSource apply(SurfaceRules.RuleSource base, HolderGetter<Biome> biomes) {
      Block sand = BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("tfc", "sand/yellow"))
         .map(holder -> holder.value()).orElse(null);
      if (sand == null || sand.defaultBlockState().isAir()) {
         return base;
      }
      ResourceKey<Biome>[] desertBiomes = DESERT_BIOMES.stream()
         .map(path -> ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, Identifier.fromNamespaceAndPath("tfc", path)))
         .toArray(ResourceKey[]::new);
      SurfaceRules.RuleSource desertSand = SurfaceRules.ifTrue(
         SurfaceRules.isBiome(biomes, desertBiomes),
         SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.state(sand.defaultBlockState())),
            SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.state(sand.defaultBlockState()))
         )
      );
      return SurfaceRules.sequence(desertSand, base);
   }
}
