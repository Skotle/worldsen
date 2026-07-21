package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Retained datapack type.  River banks are no longer independently carved. */
public final class RiverBankGradeDensity implements DensityFunction {
    private static final MapCodec<RiverBankGradeDensity> DATA_CODEC = MapCodec.unit(new RiverBankGradeDensity());
    public static final KeyDispatchDataCodec<RiverBankGradeDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    @Override public double compute(FunctionContext context) { return 0.0D; }
    @Override public void fillArray(double[] values, ContextProvider provider) { java.util.Arrays.fill(values, 0.0D); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
    @Override public double minValue() { return 0.0D; }
    @Override public double maxValue() { return 0.0D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
