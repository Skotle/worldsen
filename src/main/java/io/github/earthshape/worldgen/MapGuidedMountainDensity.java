package io.github.earthshape.worldgen;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.HeightmapLayer;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Drives vanilla ridge and erosion inputs from high areas of heightmap.bmp; final density stays vanilla-owned. */
public record MapGuidedMountainDensity(DensityFunction original, Channel channel) implements DensityFunction {
    public enum Channel { RIDGES, EROSION }

    @Override
    public double compute(FunctionContext context) {
        double base = original.compute(context);
        if (!EarthShapeServerConfig.ENABLE_TERRAIN_MOD_COMPATIBILITY.get() || !EarthShapeServerConfig.USE_HEIGHTMAP_LAYER.get()) return base;
        double threshold = EarthShapeServerConfig.MOUNTAIN_ELEVATION_THRESHOLD.get();
        double height = HeightmapLayer.INSTANCE.sample(context.blockX(), context.blockZ());
        double mountain = height <= threshold ? 0D : Math.min(1D, (height - threshold) / Math.max(0.001D, 1D - threshold));
        if (mountain == 0D) return base;
        double target = channel == Channel.RIDGES ? 0.90D : -0.85D;
        double strength = channel == Channel.RIDGES
                ? EarthShapeServerConfig.MOUNTAIN_RIDGE_GUIDE_STRENGTH.get()
                : EarthShapeServerConfig.MOUNTAIN_EROSION_GUIDE_STRENGTH.get();
        return base + (target - base) * mountain * strength;
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new MapGuidedMountainDensity(original.mapAll(visitor), channel)); }
    @Override public double minValue() { return -1D; }
    @Override public double maxValue() { return 1D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return original.codec(); }
}
