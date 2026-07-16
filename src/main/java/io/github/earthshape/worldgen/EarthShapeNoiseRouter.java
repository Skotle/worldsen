package io.github.earthshape.worldgen;

import io.github.earthshape.mixin.RandomStateAccessor;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

/** Installs climate and terrain-input wrappers; vanilla final density, caves, aquifers and structures remain intact. */
public final class EarthShapeNoiseRouter {
    private EarthShapeNoiseRouter() {}
    public static void install(RandomState state) {
        synchronized (state) {
            NoiseRouter router = state.router();
            if (router.temperature() instanceof MapGuidedClimateDensity) return;
            NoiseRouter guided = new NoiseRouter(router.barrierNoise(), router.fluidLevelFloodednessNoise(), router.fluidLevelSpreadNoise(), router.lavaNoise(),
                    new MapGuidedClimateDensity(router.temperature(), MapGuidedClimateDensity.Channel.TEMPERATURE),
                    new MapGuidedClimateDensity(router.vegetation(), MapGuidedClimateDensity.Channel.VEGETATION),
                    new MapGuidedContinentsDensity(router.continents()), new MapGuidedMountainDensity(router.erosion(), MapGuidedMountainDensity.Channel.EROSION), router.depth(),
                    new MapGuidedMountainDensity(router.ridges(), MapGuidedMountainDensity.Channel.RIDGES), router.initialDensityWithoutJaggedness(), router.finalDensity(),
                    router.veinToggle(), router.veinRidged(), router.veinGap());
            ((RandomStateAccessor) (Object) state).earthshape$setRouter(guided);
        }
    }
}
