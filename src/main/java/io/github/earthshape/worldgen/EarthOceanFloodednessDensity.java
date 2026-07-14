package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.map.EarthMapService;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Forces the vanilla aquifer to select the global sea-level fluid in mapped ocean cells. */
public record EarthOceanFloodednessDensity(DensityFunction original) implements DensityFunction {
    private static final MapCodec<EarthOceanFloodednessDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(EarthOceanFloodednessDensity::original)
    ).apply(instance, EarthOceanFloodednessDensity::new));
    public static final KeyDispatchDataCodec<EarthOceanFloodednessDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override public double compute(FunctionContext context) {
        if (EarthShapeConfig.ENABLED.get()) {
            var signal = EarthMapService.INSTANCE.sample(0L, context.blockX(), context.blockZ());
            // Fill only the open-ocean column between its mapped floor and sea level.  Applying
            // this to coast/underground cells made every nearby cave an ocean aquifer.
            if (signal.signedDistanceBlocks() < 0.0D
                    && context.blockY() >= EarthShapeConfig.OCEAN_FLOOR_Y.get()
                    && context.blockY() <= 63) return 1.0D;
        }
        return original.compute(context);
    }
    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new EarthOceanFloodednessDensity(original.mapAll(visitor))); }
    @Override public double minValue() { return Math.min(original.minValue(), 1.0D); }
    @Override public double maxValue() { return Math.max(original.maxValue(), 1.0D); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
