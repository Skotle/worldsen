package io.github.earthshape.worldgen;

import net.minecraft.world.level.levelgen.NoiseRouter;

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
                router.temperature(), router.vegetation(), new EarthContinentalnessDensity(router.continents()),
                router.erosion(), router.depth(), router.ridges(), router.initialDensityWithoutJaggedness(),
                new EarthTerrainDensity(router.finalDensity()), router.veinToggle(), router.veinRidged(), router.veinGap()
        );
    }
}
