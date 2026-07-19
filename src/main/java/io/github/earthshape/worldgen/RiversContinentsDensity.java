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
        double continentalness = -0.65D + softenedLand * 0.85D;
        // A river biome alone does not force the density router below sea level.  Blend a
        // shallow pre-generation channel into the map's real river stroke so it receives
        // normal aquifer/surface water instead of becoming a disconnected biome-only line.
        if (EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get() && land > 0.5D
                && RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ())) {
            int widthBlocks = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ());
            if (widthBlocks > 0) {
                double floorRadius = widthBlocks / 2.0D;
                double distance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ())
                        * RiversMask.INSTANCE.blocksPerPixel();
                // Only the colour-defined stroke becomes a water channel.  Spreading
                // ocean continentalness across the whole graded bank made wide, straight
                // river corridors; the separate bank-grade function handles the slope.
                // Keep density water inside the colour-derived stroke.  A wide 8–16 block
                // fade is appropriate for terrain grading, but it made every thin raster
                // river appear as a several-pixel water corridor at 4 blocks/pixel.
                double channelRadius = floorRadius + Math.max(1, Math.min(3, EarthShapeServerConfig.RIVER_CHANNEL_EDGE_FADE_BLOCKS.get()));
                if (distance < channelRadius) {
                    double channel = Math.max(-0.16D, EarthShapeServerConfig.RIVER_CHANNEL_CONTINENTALNESS.get());
                    double blend = distance <= floorRadius ? 1.0D : 1.0D - (distance - floorRadius) / Math.max(0.001D, channelRadius - floorRadius);
                    blend = blend * blend * (3.0D - 2.0D * blend);
                    continentalness = continentalness + (channel - continentalness) * blend;
                }
            }
        }
        return continentalness;
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new RiversContinentsDensity(argument.mapAll(visitor))); }
    @Override public double minValue() { return -0.80D; }
    @Override public double maxValue() { return 0.20D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }

}
