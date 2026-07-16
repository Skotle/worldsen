package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.map.RiversMask;
import io.github.earthshape.map.HeightmapLayer;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.EarthShapeServerConfig;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Maps HOI4 rivers.bmp directly into vanilla continentalness for the Overworld noise router. */
public final class RiversContinentsDensity implements DensityFunction {
    private static final MapCodec<RiversContinentsDensity> DATA_CODEC = MapCodec.unit(new RiversContinentsDensity());
    public static final KeyDispatchDataCodec<RiversContinentsDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        // A disabled coastline map deliberately becomes a neutral continental input; it leaves
        // vanilla's terrain shaping active while removing the raster's land/ocean placement.
        if (!EarthShapeServerConfig.USE_RIVERS_CONTINENT_MASK.get()) return 0.0D;
        double land = RiversMask.INSTANCE.sampleCoastLand(context.blockX(), context.blockZ());
        // Vanilla's continentalness range: negative values form ocean, modest positive values
        // form ordinary inland terrain. The source bitmap controls only continent placement;
        // vanilla retains its own terrain, cave, biome and structure generation.
        double softenedLand = land * land * (3.0D - 2.0D * land);
        // Height/normal guide the native continental spline toward mountain terrain.  This is
        // intentionally an input bias, never a replacement of final density.
        double elevationGuide = EarthShapeServerConfig.USE_HEIGHTMAP_LAYER.get()
                ? Math.max(-0.06D, Math.min(0.16D, (HeightmapLayer.INSTANCE.sample(context.blockX(), context.blockZ()) - 0.40D) * EarthShapeServerConfig.HEIGHTMAP_GUIDE_STRENGTH.get()))
                : 0D;
        double reliefGuide = ClimateLayers.INSTANCE.sample(context.blockX(), context.blockZ()).relief() * EarthShapeServerConfig.NORMAL_GUIDE_STRENGTH.get();
        double ocean = EarthShapeServerConfig.OCEAN_CONTINENTALNESS.get();
        return Math.max(-1.20D, Math.min(0.60D, ocean + softenedLand * EarthShapeServerConfig.LAND_CONTINENTALNESS_RANGE.get() + land * (elevationGuide + reliefGuide)));
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
    @Override public double minValue() { return -0.65D; }
    @Override public double maxValue() { return 0.20D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
