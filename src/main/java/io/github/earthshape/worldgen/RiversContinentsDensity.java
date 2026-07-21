package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Continental ownership plus a channel restricted to the exact source-river width. */
public record RiversContinentsDensity(DensityFunction argument) implements DensityFunction {
    private static final MapCodec<RiversContinentsDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(RiversContinentsDensity::argument)
    ).apply(instance, RiversContinentsDensity::new));
    public static final KeyDispatchDataCodec<RiversContinentsDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (EarthShapeCompatibility.disablesWorldgen() || !EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
            return argument.compute(context);
        }
        double land = RiversMask.INSTANCE.sampleLand(context.blockX(), context.blockZ());
        double continentalness = -0.65D + smooth(land) * 0.85D;

        // This test is bounded by the configured width in RiversMask.  There is
        // deliberately no bank-radius or source-pixel gap expansion here.
        if (EarthShapeServerConfig.RIVERS_ENABLED.get() && RiversMask.INSTANCE.isInlandRiver(context.blockX(), context.blockZ())) {
            double channel = EarthShapeServerConfig.RIVER_CHANNEL_CONTINENTALNESS.get();
            continentalness += (channel - continentalness) * 0.72D;
        }
        return continentalness;
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new RiversContinentsDensity(argument.mapAll(visitor))); }
    @Override public double minValue() { return -0.65D; }
    @Override public double maxValue() { return 0.20D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }

    private static double smooth(double value) { return value * value * (3.0D - 2.0D * value); }
}
