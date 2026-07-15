package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
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
        double land = RiversMask.INSTANCE.sampleLand(context.blockX(), context.blockZ());
        double elevation = HeightmapLayer.INSTANCE.sample(context.blockX(), context.blockZ());
        // Keep the layer conservative: vanilla still supplies local terrain shape, while the
        // source raster raises sustained highland and lowers broad lowland before density forms.
        return land * (elevation - 0.32D) * 0.22D;
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
    @Override public double minValue() { return -0.08D; }
    @Override public double maxValue() { return 0.15D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
