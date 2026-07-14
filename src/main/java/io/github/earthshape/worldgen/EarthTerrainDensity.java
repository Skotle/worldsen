package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.EarthShape;
import io.github.earthshape.map.EarthMapService;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds a smooth, Y-limited land/ocean bias to the original final density.
 * The original density is evaluated first, so caves, aquifers and every pack-provided detail
 * remain part of the final terrain rather than being replaced by a heightmap.
 */
public record EarthTerrainDensity(DensityFunction original) implements DensityFunction {
    private static final AtomicBoolean FIRST_COMPUTE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FIRST_MAP_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FIRST_SAMPLE_LOGGED = new AtomicBoolean();
    private static final MapCodec<EarthTerrainDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(EarthTerrainDensity::original)
    ).apply(instance, EarthTerrainDensity::new));
    public static final KeyDispatchDataCodec<EarthTerrainDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (FIRST_COMPUTE_LOGGED.compareAndSet(false, true)) {
            EarthShape.LOGGER.info("[EarthShape] Terrain wrapper reached; enabled={}", EarthShapeConfig.ENABLED.get());
        }
        double base = original.compute(context);
        if (!EarthShapeConfig.ENABLED.get()) return base;

        double land = EarthMapService.INSTANCE.sample(0L, context.blockX(), context.blockZ()).landFactor();
        if (FIRST_SAMPLE_LOGGED.compareAndSet(false, true)) {
            EarthShape.LOGGER.info("[EarthShape] Terrain density active; first sample at {}, {}, {}.", context.blockX(), context.blockY(), context.blockZ());
        }
        // Below the seabed and far above normal terrain, leave vanilla density alone.
        double lower = smootherstep(18.0D, 54.0D, context.blockY());
        double upper = 1.0D - smootherstep(128.0D, 192.0D, context.blockY());
        double verticalWindow = lower * upper;
        // Ocean: remove marginal terrain above the seafloor. Land: lift it above sea level.
        double shapeBias = -1.15D + land * 1.90D;
        return base + shapeBias * verticalWindow * EarthShapeConfig.CONTROL_STRENGTH.get();
    }

    @Override
    public void fillArray(double[] values, ContextProvider provider) {
        provider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        if (FIRST_MAP_LOGGED.compareAndSet(false, true)) {
            EarthShape.LOGGER.info("[EarthShape] Terrain wrapper wired into the live RandomState.");
        }
        return visitor.apply(new EarthTerrainDensity(original.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return original.minValue() - 1.2D;
    }

    @Override
    public double maxValue() {
        return original.maxValue() + 1.2D;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    private static double smootherstep(double min, double max, double value) {
        double t = Math.max(0.0D, Math.min(1.0D, (value - min) / (max - min)));
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }
}
