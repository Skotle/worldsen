package io.github.earthshape.worldgen;

import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

/** Creates an EarthShape wrapper around the router that a live Overworld RandomState will use. */
public final class EarthShapeNoiseRouter {
    private EarthShapeNoiseRouter() {}

    public static NoiseRouter wrap(NoiseRouter router) {
        if (router.continents() instanceof EarthContinentalnessDensity
                && router.finalDensity() instanceof EarthTerrainDensity) {
            return router;
        }
        return new NoiseRouter(
                router.barrierNoise(), router.fluidLevelFloodednessNoise(), router.fluidLevelSpreadNoise(), router.lavaNoise(),
                new EarthClimateDensity(router.temperature(), EarthClimateDensity.Channel.TEMPERATURE),
                new EarthClimateDensity(router.vegetation(), EarthClimateDensity.Channel.HUMIDITY),
                new EarthContinentalnessDensity(router.continents()),
                router.erosion(), router.depth(), router.ridges(), router.initialDensityWithoutJaggedness(),
                new EarthTerrainDensity(router.finalDensity()), router.veinToggle(), router.veinRidged(), router.veinGap()
        );
    }

    /** RandomState is shared by parallel chunk workers, so installation is synchronized and idempotent. */
    public static void install(RandomState state) {
        synchronized (state) {
            NoiseRouter current = state.router();
            NoiseRouter wrapped = wrap(current);
            if (wrapped != current) {
                ((io.github.earthshape.mixin.RandomStateAccessor) (Object) state).earthshape$setRouter(wrapped);
            }
        }
    }
}
