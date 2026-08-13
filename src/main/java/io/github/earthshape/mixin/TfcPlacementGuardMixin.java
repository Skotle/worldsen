package io.github.earthshape.mixin;

import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TFC's volcano/center placement modifiers require a TFC biome extension.
 * Features may query a neighbouring vanilla transport biome while the
 * EarthShape replacement generator is decorating a chunk, so do not invoke
 * those modifiers outside a real {@code tfc:*} biome.
 */
@Pseudo
@Mixin(
   targets = {
      "net.dries007.tfc.world.placement.CenterOrDistanceToPlacement",
      "net.dries007.tfc.world.placement.StratovolcanoPlacement"
   },
   remap = false
)
public abstract class TfcPlacementGuardMixin {
   @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true, remap = false)
   private void earthshape$skipOutsideTfcBiome(
      PlacementContext context,
      RandomSource random,
      BlockPos pos,
      CallbackInfoReturnable<Stream<BlockPos>> callback
   ) {
      boolean tfcBiome = context.getLevel().getBiome(pos).unwrapKey()
         .map(key -> "tfc".equals(key.location().getNamespace()))
         .orElse(false);
      if (!tfcBiome) {
         callback.setReturnValue(Stream.empty());
      }
   }
}
