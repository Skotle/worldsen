package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent TerraBlender's namespaced surface-rule wrapper from replacing the base rule. */
@Mixin(value = NoiseGeneratorSettings.class, priority = 2000)
public abstract class TerraBlenderSurfaceRuleMixin {
   @Shadow(remap = false)
   @Final
   private SurfaceRules.RuleSource surfaceRule;

   @Inject(method = "surfaceRule", at = @At("HEAD"), cancellable = true, remap = false)
   private void earthshape$keepBaseSurfaceRule(CallbackInfoReturnable<SurfaceRules.RuleSource> callback) {
      if (EarthShapeCompatibility.isTerraBlenderLoaded()) {
         callback.setReturnValue(this.surfaceRule);
      }
   }
}
