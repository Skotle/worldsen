package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.worldgen.EarthShapeFinalBiomeResolver;
import io.github.earthshape.worldgen.ExternalBiomeCapture;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Wraps the resolver at the last call before the chunk biome palette is written.
 * Every external resolver is evaluated first; its holder is then discarded and
 * the generator's EarthShape resolver becomes the final authority.
 */
@Mixin(value = NoiseBasedChunkGenerator.class, priority = 500)
public abstract class TerraBlenderChunkBiomeSourceMixin {
   @ModifyArg(
      method = "doCreateBiomes",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"),
      index = 0
   )
   private BiomeResolver earthshape$wrapFinalBiomeResolver(BiomeResolver externalResolver) {
      BiomeSource source = ((NoiseBasedChunkGenerator)(Object)this).getBiomeSource();
      if (EarthShapeCompatibility.disablesWorldgen() || !(source instanceof EarthShapeFinalBiomeResolver finalResolver)) {
         return externalResolver;
      }
      return (quartX, quartY, quartZ, sampler) -> {
         Holder<Biome> discarded = ExternalBiomeCapture.run(
            () -> externalResolver.getNoiseBiome(quartX, quartY, quartZ, sampler)
         );
         return finalResolver.earthshape$resolveFinalBiome(
            quartX, quartY, quartZ, sampler, discarded
         );
      };
   }
}
