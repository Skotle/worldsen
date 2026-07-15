package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.map.EarthEnvironmentSignal;
import io.github.earthshape.map.EarthMapService;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Blends real-world temperature or humidity maps into an existing biome-source climate signal. */
public record EarthClimateDensity(DensityFunction original, Channel channel) implements DensityFunction {
    public enum Channel { TEMPERATURE, HUMIDITY }

    private static final MapCodec<EarthClimateDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(EarthClimateDensity::original),
            com.mojang.serialization.Codec.STRING.fieldOf("channel").xmap(Channel::valueOf, Channel::name).forGetter(EarthClimateDensity::channel)
    ).apply(instance, EarthClimateDensity::new));
    public static final KeyDispatchDataCodec<EarthClimateDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        double base = original.compute(context);
        if (!EarthShapeConfig.ENABLED.get()) return base;
        EarthEnvironmentSignal environment = EarthMapService.INSTANCE.sampleEnvironment(context.blockX(), context.blockZ());
        if (!environment.climateActive()) return base;
        double mapValue = (channel == Channel.TEMPERATURE ? environment.temperature() : environment.humidity()) * 2.0D - 1.0D;
        double strength = EarthShapeConfig.REAL_CLIMATE_STRENGTH.get();
        return base + (mapValue - base) * strength;
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new EarthClimateDensity(original.mapAll(visitor), channel)); }
    @Override public double minValue() { return Math.min(original.minValue(), -1.0D); }
    @Override public double maxValue() { return Math.max(original.maxValue(), 1.0D); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
