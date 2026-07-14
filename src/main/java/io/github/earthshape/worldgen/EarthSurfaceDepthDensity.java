package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.map.EarthEnvironmentSignal;
import io.github.earthshape.map.EarthMapService;
import io.github.earthshape.map.EarthSignal;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Keeps the multi-noise underground-biome selector out of the mapped terrain surface. */
public record EarthSurfaceDepthDensity(DensityFunction original) implements DensityFunction {
    private static final MapCodec<EarthSurfaceDepthDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(EarthSurfaceDepthDensity::original)
    ).apply(instance, EarthSurfaceDepthDensity::new));
    public static final KeyDispatchDataCodec<EarthSurfaceDepthDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override public double compute(FunctionContext context) {
        if (!EarthShapeConfig.ENABLED.get()) return original.compute(context);
        // Vanilla cave-only biomes require a positive depth climate value (0.2..0.9). This
        // world shape moves surfaces independently of vanilla depth, so retain the surface
        // value everywhere instead of allowing cave biomes to leak into the open air.
        return 0.0D;
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new EarthSurfaceDepthDensity(original.mapAll(visitor))); }
    @Override public double minValue() { return Math.min(original.minValue(), 0.0D); }
    @Override public double maxValue() { return Math.max(original.maxValue(), 0.0D); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
    private static double smootherstep(double min, double max, double value) { double t = Math.max(0.0D, Math.min(1.0D, (value - min) / (max - min))); return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D); }
}
