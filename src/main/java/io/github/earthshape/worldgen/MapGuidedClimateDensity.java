package io.github.earthshape.worldgen;

import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.EarthShapeServerConfig;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Blends map climate into vanilla's existing router input; it never changes final density. */
public record MapGuidedClimateDensity(DensityFunction original, Channel channel) implements DensityFunction {
    public enum Channel { TEMPERATURE, VEGETATION }
    @Override public double compute(FunctionContext context) {
        double base = original.compute(context);
        if (!EarthShapeServerConfig.ENABLE_TERRAIN_MOD_COMPATIBILITY.get()) return base;
        ClimateLayers.Climate climate = ClimateLayers.INSTANCE.sample(context.blockX(), context.blockZ());
        double target = (channel == Channel.TEMPERATURE ? climate.temperature() : climate.humidity()) * 2D - 1D;
        double strength = channel == Channel.TEMPERATURE ? EarthShapeServerConfig.TEMPERATURE_GUIDE_STRENGTH.get() : EarthShapeServerConfig.VEGETATION_GUIDE_STRENGTH.get();
        return base + (target - base) * strength;
    }
    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new MapGuidedClimateDensity(original.mapAll(visitor), channel)); }
    @Override public double minValue() { return -1D; }
    @Override public double maxValue() { return 1D; }
    @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() { return original.codec(); }
}
