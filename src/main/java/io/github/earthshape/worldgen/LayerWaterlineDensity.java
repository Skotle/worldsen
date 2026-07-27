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

public final class LayerWaterlineDensity
implements DensityFunction {
    private static final MapCodec<LayerWaterlineDensity> DATA_CODEC = MapCodec.unit(new LayerWaterlineDensity());
    public static final KeyDispatchDataCodec<LayerWaterlineDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    public double compute(DensityFunction.FunctionContext context) {
        if (EarthShapeCompatibility.disablesWorldgen() || !((Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()).booleanValue()) {
            return 0.0;
        }
        if (!((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()).booleanValue()) {
            return 0.0;
        }
        int x = context.blockX();
        int z = context.blockZ();
        if (!RiversMask.INSTANCE.hasInlandRiverInfluence(x, z)) {
            return 0.0;
        }
        int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(x, z);
        if (width <= 0) {
            return 0.0;
        }
        double distance = RiversMask.INSTANCE.riverCentrelineDistance(x, z) * (double)RiversMask.INSTANCE.blocksPerPixel();
        double floorRadius = (double)width / 2.0;
        double bankRadius = floorRadius + (double)Math.max(24, Math.min(64, (Integer)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get()));
        if (distance >= bankRadius) {
            return 0.0;
        }
        double bankWeight = LayerWaterlineDensity.smoothstep(1.0 - distance / bankRadius);
        double coreWeight = LayerWaterlineDensity.smoothstep(1.0 - Math.min(1.0, distance / Math.max(1.0, floorRadius)));
        // The source stroke must decisively steer the noise at its centre.  Most of
        // the broad bank remains gentle, while the core retains a continuous valley
        // signal even one block above sea level.
        double valleyWeight = bankWeight * 0.20 + coreWeight * 0.80;
        int y = context.blockY();
        // This remains a density-field adjustment: it biases the noise router toward
        // a sea-level valley rather than replacing terrain blocks at a fixed Y.
        if (y > 63) {
            double aboveSea = Math.min(1.0, ((double)y - 63.0) / 40.0);
            // Keep a substantial negative density even immediately above sea level;
            // otherwise a shallow base-noise hump can bridge over the entire channel.
            return -1.45 * valleyWeight * (0.50 + 0.50 * aboveSea);
        }
        if (y < 61) {
            return 0.82 * coreWeight;
        }
        return 0.0;
    }

    private static double smoothstep(double value) {
        value = Math.max(0.0, Math.min(1.0, value));
        return value * value * (3.0 - 2.0 * value);
    }

    public void fillArray(double[] values, DensityFunction.ContextProvider provider) {
        provider.fillAllDirectly(values, (DensityFunction)this);
    }

    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply((DensityFunction)this);
    }

    public double minValue() {
        return -3.0;
    }

    public double maxValue() {
        return 2.0;
    }

    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
