/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.kinds.App
 *  com.mojang.datafixers.kinds.Applicative
 *  com.mojang.serialization.MapCodec
 *  com.mojang.serialization.codecs.RecordCodecBuilder
 *  net.minecraft.util.KeyDispatchDataCodec
 *  net.minecraft.world.level.levelgen.DensityFunction
 *  net.minecraft.world.level.levelgen.DensityFunction$ContextProvider
 *  net.minecraft.world.level.levelgen.DensityFunction$FunctionContext
 *  net.minecraft.world.level.levelgen.DensityFunction$Visitor
 */
package io.github.earthshape.worldgen;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record RiversContinentsDensity(DensityFunction argument) implements DensityFunction
{
    private static final MapCodec<RiversContinentsDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(RiversContinentsDensity::argument)).apply(i, RiversContinentsDensity::new));
    public static final KeyDispatchDataCodec<RiversContinentsDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    public double compute(DensityFunction.FunctionContext context) {
        if (!EarthShapeCompatibility.disablesWorldgen() && ((Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()).booleanValue()) {
            int widthBlocks;
            double continentalness;
            double land = RiversMask.INSTANCE.sampleLayerLand(context.blockX(), context.blockZ());
            double vanillaContinentalness = this.argument.compute(context);
            // Blend the mapped coast through the same continuous mask instead of
            // switching from ocean to land at one raster-cell edge.
            // -0.19 is the vanilla near-inland threshold, so leaving mapped ocean at
            // that value lets unrelated base-noise humps surface as small islands.
            // Keep source-water safely in the ocean band; the interpolated shore mask
            // below still produces a gradual coastal shelf rather than a hard edge.
            double waterContinentalness = Math.min(-0.46, vanillaContinentalness);
            double landContinentalness = Math.max(0.0, vanillaContinentalness);
            double shoreWeight = smoothstep(land);
            double d = continentalness = waterContinentalness + (landContinentalness - waterContinentalness) * shoreWeight;
            if (((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()).booleanValue() && land > 0.5 && RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ()) && (widthBlocks = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ())) > 0) {
                double channelRadius;
                double floorRadius = (double)Math.max(4, widthBlocks) / 2.0;
                double distance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ()) * (double)RiversMask.INSTANCE.blocksPerPixel();
                if (distance < (channelRadius = floorRadius + (double)Math.max(24, Math.min(56, (Integer)EarthShapeServerConfig.RIVER_HEIGHT_FADE_BLOCKS.get())))) {
                    // The configured channel target is the primary lowland signal for
                    // the vanilla spline. Do not clamp it back to a nearly-land value.
                    double centreChannel = Math.max(-0.8, Math.min(-0.05, (Double)EarthShapeServerConfig.RIVER_CHANNEL_CONTINENTALNESS.get()));
                    double floorWeight = 1.0 - Math.min(1.0, distance / Math.max(1.0, floorRadius));
                    floorWeight = floorWeight * floorWeight * (3.0 - 2.0 * floorWeight);
                    double shoulderWeight = 1.0 - Math.min(1.0, distance / Math.max(1.0, channelRadius));
                    shoulderWeight = shoulderWeight * shoulderWeight * (3.0 - 2.0 * shoulderWeight);
                    double target = Math.min(continentalness, centreChannel);
                    double influence = floorWeight + (1.0 - floorWeight) * shoulderWeight * 0.3;
                    continentalness += (target - continentalness) * influence;
                }
            }
            return continentalness;
        }
        return this.argument.compute(context);
    }

    public void fillArray(double[] values, DensityFunction.ContextProvider provider) {
        provider.fillAllDirectly(values, (DensityFunction)this);
    }

    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply((DensityFunction)new RiversContinentsDensity(this.argument.mapAll(visitor)));
    }

    public double minValue() {
        return -0.8;
    }

    public double maxValue() {
        return 0.2;
    }

    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    private static double smoothstep(double value) {
        value = Math.max(0.0, Math.min(1.0, value));
        return value * value * (3.0 - 2.0 * value);
    }
}
