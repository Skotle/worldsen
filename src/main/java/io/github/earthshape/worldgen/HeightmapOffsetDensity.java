package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.map.HeightmapLayer;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Adds a conservative map-elevation term to vanilla's existing Overworld depth function. */
public final class HeightmapOffsetDensity implements DensityFunction {
    private static final MapCodec<HeightmapOffsetDensity> DATA_CODEC = MapCodec.unit(new HeightmapOffsetDensity());
    public static final KeyDispatchDataCodec<HeightmapOffsetDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (EarthShapeCompatibility.disablesWorldgen() || !EarthShapeServerConfig.HEIGHTMAP_ENABLED.get()) return 0.0D;
        double elevation = HeightmapLayer.INSTANCE.sample(context.blockX(), context.blockZ());
        // Map land must never be pushed beneath sea level by a derived coast/river-distance
        // value.  The continent mask is the sole authority for water; heightmap data can
        // only add relief above the vanilla land surface.
        double inland = RiversMask.INSTANCE.sampleHeightmapInlandness(context.blockX(), context.blockZ());
        double riverRelief = RiversMask.INSTANCE.sampleRiverReliefFactor(context.blockX(), context.blockZ());
        double median = EarthShapeServerConfig.HEIGHTMAP_MEDIAN.get();
        double deviation = elevation - median;
        double lowland = Math.max(0.0D, deviation) * 0.16D;
        double mountain = Math.max(0.0D, Math.min(1.0D, deviation / Math.max(0.01D, 1.0D - median)));
        double highlandLift = mountain * mountain * 0.24D + mountain * mountain * mountain * mountain * 0.30D;
        return inland * riverRelief * (lowland + highlandLift);
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
    @Override public double minValue() { return 0.0D; }
    @Override public double maxValue() { return 0.95D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
