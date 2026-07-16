package io.github.earthshape.map;

/** Real-world whitelist for the Badlands/Mesa biome family, using the equirectangular map coordinates. */
public final class BadlandsRegions {
    private static final int MAP_WIDTH = 5632;
    private static final int MAP_HEIGHT = 2048;

    private BadlandsRegions() {}

    /**
     * Allowed areas from the terrain specification: Arizona/Utah canyon country, Australia's
     * red interior, northern Atacama, and the two principal Chinese Danxia areas.
     */
    public static boolean containsBlock(int blockX, int blockZ) {
        double pixelX = blockX / (double) RiversMask.INSTANCE.blocksPerPixel() + MAP_WIDTH * .5D;
        double pixelZ = blockZ / (double) RiversMask.INSTANCE.blocksPerPixel() + MAP_HEIGHT * .5D;
        double longitude = pixelX / MAP_WIDTH * 360D - 180D;
        double latitude = 90D - pixelZ / MAP_HEIGHT * 180D;
        return in(latitude, longitude, 34D, 40.5D, -115.5D, -108D)       // Arizona / Utah canyon country
                || in(latitude, longitude, -31D, -20D, 121D, 140D)         // Australian red interior
                || in(latitude, longitude, -25.5D, -17D, -72.5D, -67D)     // northern Atacama
                || in(latitude, longitude, 23.5D, 27.5D, 111D, 117.5D)     // southern China Danxia
                || in(latitude, longitude, 37D, 41D, 98D, 103D);           // Zhangye Danxia
    }

    private static boolean in(double latitude, double longitude, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
        return latitude >= minLatitude && latitude <= maxLatitude && longitude >= minLongitude && longitude <= maxLongitude;
    }
}
