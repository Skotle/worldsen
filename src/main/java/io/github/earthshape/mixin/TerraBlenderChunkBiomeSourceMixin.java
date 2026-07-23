package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * TerraBlender clones the biome source per chunk and regenerates its region-noise
 * selector before fillBiomesFromNoise. The clone is unnecessary once EarthShape
 * owns the final lookup and would otherwise keep the TerraBlender path alive.
 */
@Mixin(value = NoiseBasedChunkGenerator.class, priority = 500)
public abstract class TerraBlenderChunkBiomeSourceMixin {
   @ModifyArg(
      method = "doCreateBiomes",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"),
      index = 0
   )
   private BiomeResolver earthshape$restoreBiomeSource(BiomeResolver ignored) {
      return EarthShapeCompatibility.isTerraBlenderLoaded()
         ? ((NoiseBasedChunkGenerator)(Object)this).getBiomeSource()
         : ignored;
   }
}
