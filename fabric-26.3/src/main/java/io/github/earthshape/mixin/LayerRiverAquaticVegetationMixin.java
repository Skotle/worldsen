package io.github.earthshape.mixin;

import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent ocean vegetation decorators from treating mapped rivers as seabed. */
@Mixin(net.minecraft.world.level.levelgen.placement.PlacedFeature.class)
public abstract class LayerRiverAquaticVegetationMixin {
   @org.spongepowered.asm.mixin.Shadow public abstract net.minecraft.core.Holder<net.minecraft.world.level.levelgen.feature.Feature> feature();

   @Inject(
      method = {"place"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void earthshape$skipOceanPlantsInLayerRiver(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator generator, net.minecraft.util.RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> callback) {
      boolean oceanPlant = feature().unwrapKey().map(key -> key.identifier().getNamespace().equals("minecraft")
         && (key.identifier().getPath().equals("kelp") || key.identifier().getPath().startsWith("seagrass"))).orElse(false);
      if (oceanPlant && RiversMask.INSTANCE.isInlandRiver(origin.getX(), origin.getZ())) {
         callback.setReturnValue(false);
      }
   }
}
