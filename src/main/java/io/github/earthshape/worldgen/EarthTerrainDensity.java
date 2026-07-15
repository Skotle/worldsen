package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.EarthShape;
import io.github.earthshape.map.EarthMapService;
import io.github.earthshape.map.EarthSignal;
import io.github.earthshape.map.EarthEnvironmentSignal;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Converts the Earth SDF into a target surface. This guarantees that map ocean cannot retain
 * vanilla mountains and that map land remains connected above sea level. The original final
 * density is retained only as bounded local detail, rather than being allowed to move a coast.
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

        EarthSignal mapSignal = EarthMapService.INSTANCE.sample(0L, context.blockX(), context.blockZ());
        // Biome continentalness may use a broad transition, but the physical shoreline must
        // follow the raster closely rather than becoming a hundreds-of-blocks-wide mush.
        // Keep physical terrain transitions much narrower than the biome transition.  A broad
        // density blend from both shores can otherwise raise the centre of a one-pixel strait
        // above sea level and close it.
        double terrainCoastWidth = EarthShapeConfig.TERRAIN_COAST_WIDTH_BLOCKS.get();
        double terrainDistance = mapSignal.signedDistanceBlocks() - EarthShapeConfig.OCEAN_INSET_BLOCKS.get();
        double land = EarthMapService.INSTANCE.isNarrowWaterPassage(context.blockX(), context.blockZ())
                ? 0.0D
                : smootherstep(-terrainCoastWidth, terrainCoastWidth, terrainDistance);
        if (FIRST_SAMPLE_LOGGED.compareAndSet(false, true)) {
            EarthShape.LOGGER.info("[EarthShape] Terrain density active; first sample at {}, {}, {}.", context.blockX(), context.blockY(), context.blockZ());
        }
        // 0.3.4's map-controlled surface: both land and ocean come from the SDF rather than
        // handing land back to vanilla continentalness.
        double oceanFloorY = EarthShapeConfig.OCEAN_FLOOR_Y.get();
        double landBaseY = EarthShapeConfig.LAND_BASE_Y.get();
        double shoreline = smootherstep(0.0D, 0.5D, land);
        double targetSurfaceY = oceanFloorY + (landBaseY - oceanFloorY) * shoreline;

        EarthEnvironmentSignal environment = EarthMapService.INSTANCE.sampleEnvironment(context.blockX(), context.blockZ());
        if (environment.heightActive()) {
            double mappedHeightY = EarthShapeConfig.HEIGHTMAP_MIN_Y.get()
                    + environment.height() * (EarthShapeConfig.HEIGHTMAP_MAX_Y.get() - EarthShapeConfig.HEIGHTMAP_MIN_Y.get());
            double inlandWeight = smootherstep(0.55D, 0.85D, land)
                    * smootherstep(160.0D, 640.0D, mapSignal.signedDistanceBlocks());
            targetSurfaceY += (mappedHeightY - landBaseY) * inlandWeight;
            targetSurfaceY += environment.normalSteepness() * 8.0D * inlandWeight;
        }

        double targetShape = (targetSurfaceY - context.blockY()) / EarthShapeConfig.SHAPE_VERTICAL_SCALE.get();
        double boundedDetail = Math.max(-0.4D, Math.min(0.4D, base));
        double detailStrength = Math.min(EarthShapeConfig.TERRAIN_DETAIL_STRENGTH.get(), 0.08D);
        double shaped = targetShape + boundedDetail * detailStrength;

        // 0.3.4 limited the retained vanilla density to less than half a block of vertical
        // movement, so all non-mountain map pixels became featureless plains.  Reuse the same
        // seeded vanilla density as a larger, strictly inland surface offset: it adds rolling
        // hills without allowing the detail to shift a coastline or raise mapped ocean.
        double hillWeight = smootherstep(0.60D, 0.90D, land)
                * smootherstep(96.0D, 384.0D, mapSignal.signedDistanceBlocks());
        double hillDetail = Math.max(-1.6D, Math.min(1.6D, base));
        shaped += hillDetail * 0.45D * hillWeight;
        if (land <= 0.5D) shaped = targetShape;

        if (EarthShapeConfig.STRICT_OCEAN_MASK.get()) {
            double oceanWeight = 1.0D - smootherstep(0.45D, 0.5D, land);
            double seaLevel = 63.0D;
            double seaLevelCeiling = (seaLevel - 2.0D - context.blockY()) / EarthShapeConfig.SHAPE_VERTICAL_SCALE.get();
            double capped = Math.min(shaped, seaLevelCeiling);
            shaped += (capped - shaped) * oceanWeight;
        }
        return shaped;
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
        return -1000000.0D;
    }

    @Override
    public double maxValue() {
        return 1000000.0D;
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
