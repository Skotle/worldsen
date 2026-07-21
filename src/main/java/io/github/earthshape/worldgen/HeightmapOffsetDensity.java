package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.HeightmapLayer;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Heightmap relief only; it neither creates water nor modifies source river widths. */
public final class HeightmapOffsetDensity implements DensityFunction {
    private static final MapCodec<HeightmapOffsetDensity> DATA_CODEC = MapCodec.unit(new HeightmapOffsetDensity());
    public static final KeyDispatchDataCodec<HeightmapOffsetDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (EarthShapeCompatibility.disablesWorldgen() || !EarthShapeServerConfig.HEIGHTMAP_ENABLED.get()
                || RiversMask.INSTANCE.sampleLayerLand(context.blockX(), context.blockZ()) < 0.5D) return 0.0D;
        double elevation = HeightmapLayer.INSTANCE.sampleSmoothed(context.blockX(), context.blockZ());
        double factor = RiversMask.INSTANCE.sampleHeightmapInlandness(context.blockX(), context.blockZ());
        double aboveSea = Math.max(0.0D, elevation - 0.50D);
        ClimateLayers.TerrainKind kind = ClimateLayers.INSTANCE.terrainKind(context.blockX(), context.blockZ());
        double terrainScale = kind == ClimateLayers.TerrainKind.MOUNTAIN ? 0.35D
                : kind == ClimateLayers.TerrainKind.HILLS ? 0.18D : 0.06D;
        return factor * terrainScale * (aboveSea * 0.35D + aboveSea * aboveSea * 0.65D);
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
    @Override public double minValue() { return 0.0D; }
    @Override public double maxValue() { return 0.35D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
