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
@Mixin(value = net.minecraft.world.level.chunk.ChunkGenerator.class, priority = 500)
public abstract class TerraBlenderChunkBiomeSourceMixin {
   @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
      method = "doCreateBiomes",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;)V")
   )
   private void earthshape$wrapFinalBiomeResolver(net.minecraft.world.level.chunk.ChunkAccess chunk, BiomeResolver externalResolver, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original, @com.llamalad7.mixinextras.sugar.Local net.minecraft.world.level.biome.Climate.Sampler sampler) {
      BiomeSource source = ((net.minecraft.world.level.chunk.ChunkGenerator)(Object)this).getBiomeSource();
      if (EarthShapeCompatibility.disablesWorldgen() || !(source instanceof EarthShapeFinalBiomeResolver finalResolver)) {
         original.call(chunk, externalResolver);
         return;
      }
      BiomeResolver wrapped = (quartX, quartY, quartZ) -> {
         Holder<Biome> discarded = ExternalBiomeCapture.run(
            () -> externalResolver.getNoiseBiome(quartX, quartY, quartZ)
         );
         return finalResolver.earthshape$resolveFinalBiome(
            quartX, quartY, quartZ, sampler, discarded
         );
      };
      original.call(chunk, wrapped);
   }
}
