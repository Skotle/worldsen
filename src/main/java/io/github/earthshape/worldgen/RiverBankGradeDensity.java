/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.MapCodec
 *  net.minecraft.util.KeyDispatchDataCodec
 *  net.minecraft.world.level.levelgen.DensityFunction
 *  net.minecraft.world.level.levelgen.DensityFunction$ContextProvider
 *  net.minecraft.world.level.levelgen.DensityFunction$FunctionContext
 *  net.minecraft.world.level.levelgen.DensityFunction$Visitor
 */
package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class RiverBankGradeDensity
implements DensityFunction {
    private static final MapCodec<RiverBankGradeDensity> DATA_CODEC = MapCodec.unit(new RiverBankGradeDensity());
    public static final KeyDispatchDataCodec<RiverBankGradeDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    public double compute(DensityFunction.FunctionContext context) {
        if (!EarthShapeCompatibility.disablesWorldgen() && ((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()).booleanValue()) {
            double radius;
            if (!RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ())) {
                return 0.0;
            }
            double distance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ());
            int widthBlocks = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ());
            if (widthBlocks == 0) {
                return 0.0;
            }
            double floorRadius = (double)widthBlocks / 2.0;
            double distanceBlocks = distance * (double)RiversMask.INSTANCE.blocksPerPixel();
            if (distanceBlocks >= (radius = floorRadius + (double)Math.max(48, Math.min(96, (Integer)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get())))) {
                return 0.0;
            }
            // Use the configured density depth.  The old min(1, ...) made every
            // setting a one-block nudge, too weak for the noise router to form a bed.
            double maximumDrop = (double)Math.min(32, (Integer)EarthShapeServerConfig.RIVER_MAXIMUM_DEPTH_BLOCKS.get()) / 40.0;
            if (distanceBlocks <= floorRadius) {
                double centreWeight = 1.0 - distanceBlocks / Math.max(1.0, floorRadius);
                centreWeight = centreWeight * centreWeight * (3.0 - 2.0 * centreWeight);
                return -maximumDrop * centreWeight;
            }
            return 0.0;
        }
        return 0.0;
    }

    public void fillArray(double[] values, DensityFunction.ContextProvider provider) {
        provider.fillAllDirectly(values, (DensityFunction)this);
    }

    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply((DensityFunction)this);
    }

    public double minValue() {
        return -0.5;
    }

    public double maxValue() {
        return 0.0;
    }

    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
