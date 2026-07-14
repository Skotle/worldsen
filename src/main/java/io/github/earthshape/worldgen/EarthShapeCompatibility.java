package io.github.earthshape.worldgen;

import io.github.earthshape.EarthShapeConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/** Conservative opt-in for biome packs that supply their own standard-Overworld noise settings. */
public final class EarthShapeCompatibility {
    private EarthShapeCompatibility() {}

    public static boolean supports(Holder<NoiseGeneratorSettings> settings) {
        if (settings.is(NoiseGeneratorSettings.OVERWORLD)
                || settings.is(NoiseGeneratorSettings.LARGE_BIOMES)
                || settings.is(NoiseGeneratorSettings.AMPLIFIED)) {
            return true;
        }
        if (!EarthShapeConfig.APPLY_TO_COMPATIBLE_OVERWORLD_SETTINGS.get()) return false;

        NoiseGeneratorSettings value = settings.value();
        return value.defaultBlock().is(Blocks.STONE)
                && value.defaultFluid().is(Blocks.WATER)
                && value.seaLevel() == 63
                && value.noiseSettings().minY() <= -64
                && value.noiseSettings().height() >= 384
                && !value.disableMobGeneration();
    }
}
