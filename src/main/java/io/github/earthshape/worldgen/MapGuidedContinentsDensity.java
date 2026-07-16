package io.github.earthshape.worldgen;

import io.github.earthshape.EarthShapeServerConfig;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Preserves a terrain mod's continental noise while using rivers.bmp for global land/ocean placement. */
public record MapGuidedContinentsDensity(DensityFunction original) implements DensityFunction {
    private static final RiversContinentsDensity MAP = new RiversContinentsDensity();

    @Override
    public double compute(FunctionContext context) {
        if (!EarthShapeServerConfig.ENABLE_TERRAIN_MOD_COMPATIBILITY.get() || !EarthShapeServerConfig.USE_RIVERS_CONTINENT_MASK.get()) return original.compute(context);
        // Map layers establish the large-scale coastline. The original router contributes only
        // local variation, keeping compatible terrain mods' splines and final-density logic intact.
        double mapped = MAP.compute(context);
        return Math.max(-1.20D, Math.min(.60D, mapped + original.compute(context) * .12D));
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new MapGuidedContinentsDensity(original.mapAll(visitor))); }
    @Override public double minValue() { return -1.20D; }
    @Override public double maxValue() { return .60D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return original.codec(); }
}
