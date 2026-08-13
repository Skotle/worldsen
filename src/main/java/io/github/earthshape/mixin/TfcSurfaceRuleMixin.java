package io.github.earthshape.mixin;

import io.github.earthshape.compat.TfcSurfaceRules;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies TFC desert material to EarthShape's map-selected TFC desert biomes. */
@Mixin(NoiseGeneratorSettings.class)
public abstract class TfcSurfaceRuleMixin {
   @Inject(method = "surfaceRule", at = @At("RETURN"), cancellable = true, remap = false)
   private void earthshape$applyTfcLayerSurfaces(CallbackInfoReturnable<SurfaceRules.RuleSource> callback) {
      if (ModList.get().isLoaded("tfc")) {
         callback.setReturnValue(TfcSurfaceRules.apply(callback.getReturnValue()));
      }
   }
}
