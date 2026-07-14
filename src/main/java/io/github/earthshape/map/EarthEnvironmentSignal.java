package io.github.earthshape.map;

/** Normalized optional Earth layers. Height, temperature and humidity are all in the 0..1 range. */
public record EarthEnvironmentSignal(boolean heightActive, boolean climateActive, double height, double temperature, double humidity,
                                     double riverStrength, double normalSteepness) {
    public static final EarthEnvironmentSignal INACTIVE = new EarthEnvironmentSignal(false, false, 0.5D, 0.5D, 0.5D, 0.0D, 0.0D);
}
