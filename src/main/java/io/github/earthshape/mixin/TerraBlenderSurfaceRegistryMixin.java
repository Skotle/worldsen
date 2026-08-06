package io.github.earthshape.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent dependent mods from populating TerraBlender's surface-rule tables. */
@Pseudo
@Mixin(targets = "terrablender.api.SurfaceRuleManager", remap = false)
public abstract class TerraBlenderSurfaceRegistryMixin {
   @Inject(
      method = {
         "addSurfaceRules(Lterrablender/api/SurfaceRuleManager$RuleCategory;Ljava/lang/String;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V",
         "addToDefaultSurfaceRulesAtStage(Lterrablender/api/SurfaceRuleManager$RuleCategory;Lterrablender/api/SurfaceRuleManager$RuleStage;ILnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V",
         "setDefaultSurfaceRules(Lterrablender/api/SurfaceRuleManager$RuleCategory;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V"
      },
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private static void earthshape$discardSurfaceRuleRegistration(CallbackInfo callback) {
      callback.cancel();
   }
}
