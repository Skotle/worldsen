package io.github.earthshape.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/** Final authority used after every external BiomeResolver has completed. */
public interface EarthShapeFinalBiomeResolver {
   Holder<Biome> earthshape$resolveFinalBiome(
      int quartX,
      int quartY,
      int quartZ,
      Climate.Sampler sampler,
      Holder<Biome> discardedExternalResult
   );
}
