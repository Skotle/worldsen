package io.github.earthshape.worldgen;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.fml.ModList;

/** Optional integration for Terralith's data-pack replacement of minecraft:overworld. */
public final class TerralithIntegration {
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private TerralithIntegration() {}

    public static boolean isActive() {
        return EarthShapeConfig.TERRALITH_COMPATIBILITY.get() && ModList.get().isLoaded("terralith");
    }

    /**
     * Terralith 2.6.2 replaces the Overworld dimension's multi-noise biome source but keeps a
     * normal stone/water Overworld generator.  Accept that generator even if another Terralith
     * add-on changes its registry key, without ever matching Nether/End-like settings.
     */
    public static boolean supports(Holder<NoiseGeneratorSettings> settings) {
        if (!isActive()) return false;
        NoiseGeneratorSettings value = settings.value();
        return value.defaultBlock().is(Blocks.STONE)
                && value.defaultFluid().is(Blocks.WATER)
                && value.seaLevel() >= 60 && value.seaLevel() <= 64
                && value.noiseSettings().minY() <= -64
                && value.noiseSettings().height() >= 384
                && !value.disableMobGeneration();
    }

    public static void logApplied() {
        if (isActive() && LOGGED.compareAndSet(false, true)) {
            EarthShape.LOGGER.info("[EarthShape] Terralith detected: keeping Terralith's multi-noise biome source while applying EarthShape coastline, height and climate layers.");
        }
    }
}
