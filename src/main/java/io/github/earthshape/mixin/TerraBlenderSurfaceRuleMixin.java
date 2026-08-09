package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
   @Shadow(remap = false)
   @Final
   private BlockState defaultBlock;

   @Inject(method = "surfaceRule", at = @At("HEAD"), cancellable = true, remap = false)
   private void earthshape$keepBaseSurfaceRule(CallbackInfoReturnable<SurfaceRules.RuleSource> callback) {
      if (EarthShapeCompatibility.isTerraBlenderLoaded() || EarthShapeCompatibility.isBiomesOPlentyLoaded()) {
         String dimensionMethod = this.defaultBlock.is(Blocks.NETHERRACK)
            ? "nether"
            : (this.defaultBlock.is(Blocks.END_STONE) ? "end" : "overworld");
         callback.setReturnValue(EarthShapeCompatibility.compatibleSurfaceRules(this.surfaceRule, dimensionMethod));
      }
   }
}
