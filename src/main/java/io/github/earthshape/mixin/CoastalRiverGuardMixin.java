/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package io.github.earthshape.mixin;

import io.github.earthshape.map.RiversMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={RiversMask.class})
public final class CoastalRiverGuardMixin {
    @Inject(method={"hasInlandRiverInfluence"}, at={@At(value="RETURN")}, cancellable=true)
    private void earthshape$rejectCoastalStroke(int blockX, int blockZ, CallbackInfoReturnable<Boolean> callback) {
        if (((Boolean)callback.getReturnValue()).booleanValue() && RiversMask.INSTANCE.isRiverMouth(blockX, blockZ)) {
            callback.setReturnValue(false);
        }
    }
}
