/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.levelgen.NoiseGeneratorSettings
 *  net.minecraft.world.level.levelgen.SurfaceRules$RuleSource
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
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

@Mixin(value={NoiseGeneratorSettings.class}, priority=2000)
public abstract class TerraBlenderSurfaceRuleMixin {
    @Shadow(remap=false)
    @Final
    private SurfaceRules.RuleSource surfaceRule;

    @Inject(method={"surfaceRule"}, at={@At(value="HEAD")}, cancellable=true, remap=false)
    private void earthshape$keepBaseSurfaceRule(CallbackInfoReturnable<SurfaceRules.RuleSource> callback) {
        if (EarthShapeCompatibility.isTerraBlenderLoaded()) {
            callback.setReturnValue(this.surfaceRule);
        }
    }
}
