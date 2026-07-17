package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Maps HOI4 rivers.bmp directly into vanilla continentalness for the Overworld noise router. */
public record RiversContinentsDensity(DensityFunction argument) implements DensityFunction {
    private static final MapCodec<RiversContinentsDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(RiversContinentsDensity::argument)
    ).apply(i, RiversContinentsDensity::new));
    public static final KeyDispatchDataCodec<RiversContinentsDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (EarthShapeCompatibility.disablesWorldgen() || !EarthShapeServerConfig.CONTINENTS_ENABLED.get()) return argument.compute(context);
        double land = RiversMask.INSTANCE.sampleCoastLand(context.blockX(), context.blockZ());
        // Vanilla's continentalness range: negative values form ocean, modest positive values
        // form ordinary inland terrain. The source bitmap controls only continent placement;
        // vanilla retains its own terrain, cave, biome and structure generation.
        double softenedLand = land * land * (3.0D - 2.0D * land);
        return -0.65D + softenedLand * 0.85D;
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new RiversContinentsDensity(argument.mapAll(visitor))); }
    @Override public double minValue() { return -0.65D; }
    @Override public double maxValue() { return 0.20D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
