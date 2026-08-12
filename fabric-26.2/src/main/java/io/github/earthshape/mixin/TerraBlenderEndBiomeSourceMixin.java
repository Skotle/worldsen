package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bypasses TerraBlender's End-biome selector with Minecraft's original algorithm. */
@Mixin(value = TheEndBiomeSource.class, priority = 3000)
public abstract class TerraBlenderEndBiomeSourceMixin {
   @Shadow @Final private Holder<Biome> end;
   @Shadow @Final private Holder<Biome> highlands;
   @Shadow @Final private Holder<Biome> midlands;
   @Shadow @Final private Holder<Biome> islands;
   @Shadow @Final private Holder<Biome> barrens;

   @Inject(
      method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
      at = @At("HEAD"),
      cancellable = true
   )
   private void earthshape$useVanillaEndBiome(
      int quartX, int quartY, int quartZ, Climate.Sampler sampler,
      CallbackInfoReturnable<Holder<Biome>> callback
   ) {
      if (!EarthShapeCompatibility.isTerraBlenderLoaded()) return;

      int blockX = QuartPos.toBlock(quartX);
      int blockY = QuartPos.toBlock(quartY);
      int blockZ = QuartPos.toBlock(quartZ);
      int sectionX = SectionPos.blockToSectionCoord(blockX);
      int sectionZ = SectionPos.blockToSectionCoord(blockZ);
      if ((long)sectionX * sectionX + (long)sectionZ * sectionZ <= 4096L) {
         callback.setReturnValue(this.end);
         return;
      }

      int sampleX = (SectionPos.blockToSectionCoord(blockX) * 2 + 1) * 8;
      int sampleZ = (SectionPos.blockToSectionCoord(blockZ) * 2 + 1) * 8;
      double erosion = sampler.erosion().compute(new DensityFunction.SinglePointContext(sampleX, blockY, sampleZ));
      callback.setReturnValue(
         erosion > 0.25 ? this.highlands
            : (erosion >= -0.0625 ? this.midlands : (erosion < -0.21875 ? this.islands : this.barrens))
      );
   }
}
