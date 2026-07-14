package io.github.earthshape.map;

/** Normalized optional Earth layers. Height, temperature and humidity are all in the 0..1 range. */
public record EarthEnvironmentSignal(boolean active, double height, double temperature, double humidity) {
    public static final EarthEnvironmentSignal INACTIVE = new EarthEnvironmentSignal(false, 0.5D, 0.5D, 0.5D);
}
