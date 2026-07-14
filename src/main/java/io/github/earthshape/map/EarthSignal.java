package io.github.earthshape.map;

/** Continuous, deterministic map result for one absolute world X/Z coordinate. */
public record EarthSignal(double signedDistanceBlocks, double landFactor, double continentalness) {
    public boolean isOcean() {
        return landFactor < 0.5D;
    }
}
