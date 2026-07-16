package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Samples the supplied climate/terrain rasters without assigning blocks or replacing terrain. */
public final class ClimateLayers {
    public static final ClimateLayers INSTANCE = new ClimateLayers();
    private volatile Data data;
    private ClimateLayers() {}

    public Climate sample(int blockX, int blockZ) {
        Data d = data();
        double x = blockX / (double) RiversMask.INSTANCE.blocksPerPixel() / d.referenceWidth + 0.5D;
        double z = blockZ / (double) RiversMask.INSTANCE.blocksPerPixel() / d.referenceHeight + 0.5D;
        double temperateTrees = d.temperateTrees.sample(x, z);
        double tropicalTrees = d.tropicalTrees.sample(x, z);
        TerrainClimate terrain = d.terrain.climate(x, z, EarthShapeServerConfig.DESERT_CLUSTER_RADIUS_BLOCKS.get() / (double) RiversMask.INSTANCE.blocksPerPixel(), EarthShapeServerConfig.DESERT_CLUSTER_THRESHOLD.get());
        double temperature = EarthShapeServerConfig.USE_TEMPERATURE_LAYER.get() ? d.temperature.sample(x, z) : .5D;
        if (EarthShapeServerConfig.USE_TERRAIN_DESERT_LAYER.get()) temperature = terrain.temperature;
        if (EarthShapeServerConfig.USE_TREES_LAYER.get() && tropicalTrees > 0D) {
            temperature = Math.max(temperature, EarthShapeServerConfig.TROPICAL_TREE_TEMPERATURE_FLOOR.get() * tropicalTrees);
        }
        double humidity = EarthShapeServerConfig.USE_TERRAIN_DESERT_LAYER.get() ? terrain.humidity : EarthShapeServerConfig.HUMIDITY_BASE.get();
        if (EarthShapeServerConfig.USE_TREES_LAYER.get()) humidity += Math.max(temperateTrees, tropicalTrees) * EarthShapeServerConfig.TREE_HUMIDITY_WEIGHT.get();
        if (EarthShapeServerConfig.USE_RIVERS_HUMIDITY_LAYER.get()) humidity += d.rivers.sample(x, z) * EarthShapeServerConfig.RIVER_HUMIDITY_WEIGHT.get();
        if (EarthShapeServerConfig.USE_TERRAIN_DESERT_LAYER.get() && terrain.desert()) humidity -= EarthShapeServerConfig.DESERT_DRYNESS_WEIGHT.get();
        double relief = EarthShapeServerConfig.USE_NORMAL_LAYER.get() ? d.relief.sample(x, z) : 0D;
        if (EarthShapeServerConfig.USE_TREES_LAYER.get() && tropicalTrees > 0D) humidity = Math.max(humidity, EarthShapeServerConfig.TROPICAL_TREE_HUMIDITY_FLOOR.get() * tropicalTrees);
        return new Climate(temperature, clamp(humidity), relief);
    }

    /** True only inside the painted green or purple tree coverage of trees.bmp. */
    public boolean hasTreeCover(int blockX, int blockZ) {
        Data d = data();
        double x = blockX / (double) RiversMask.INSTANCE.blocksPerPixel() / d.referenceWidth + 0.5D;
        double z = blockZ / (double) RiversMask.INSTANCE.blocksPerPixel() / d.referenceHeight + 0.5D;
        return Math.max(d.temperateTrees.sample(x, z), d.tropicalTrees.sample(x, z)) >= .10D;
    }

    private Data data() {
        Data current = data;
        if (current != null) return current;
        synchronized (this) { if (data == null) data = load(); return data; }
    }

    private static Data load() {
        try {
            ImageLayer temperature = read("/earthshape/hoi4/earth_temperature.png", ClimateLayers::temperature);
            ImageLayer temperateTrees = read("/earthshape/hoi4/trees.bmp", (r, g, b) -> g > r * 1.15D && g > b * 1.15D ? 1D : 0D);
            ImageLayer tropicalTrees = read("/earthshape/hoi4/trees.bmp", (r, g, b) -> r >= 80 && b >= r * .50D && g <= r * .60D ? 1D : 0D);
            ImageLayer rivers = read("/earthshape/hoi4/rivers.bmp", (r, g, b) -> r == 122 && g == 122 && b == 122 || r == 255 && g == 255 && b == 255 ? 0D : 1D);
            TerrainLayer terrain = readTerrain();
            ImageLayer normal = read("/earthshape/hoi4/world_normal.bmp", (r, g, b) -> Math.min(1D, Math.hypot(r - 128D, g - 128D) / 96D));
            EarthShape.LOGGER.info("[EarthShape] Temperature, temperate/cold trees, tropical trees, rivers, terrain and normal climate layers loaded.");
            return new Data(5632, 2048, temperature, temperateTrees, tropicalTrees, rivers, terrain, normal);
        } catch (IOException exception) { throw new IllegalStateException("EarthShape could not load climate layers", exception); }
    }

    private static ImageLayer read(String path, PixelDecoder decoder) throws IOException {
        try (InputStream in = EarthShape.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing " + path);
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("unreadable " + path);
            return ImageLayer.read(image, decoder);
        }
    }

    /** Reads terrain.bmp only as climate categories; its colors never participate in the land mask. */
    private static TerrainLayer readTerrain() throws IOException {
        try (InputStream in = EarthShape.class.getResourceAsStream("/earthshape/hoi4/terrain.bmp")) {
            if (in == null) throw new IOException("missing /earthshape/hoi4/terrain.bmp");
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("unreadable /earthshape/hoi4/terrain.bmp");
            int width = image.getWidth(), height = image.getHeight();
            byte[] temperature = new byte[width * height], humidity = new byte[width * height];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y); TerrainClimate climate = terrainClimate(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255);
                int index = y * width + x; temperature[index] = (byte) Math.round(climate.temperature * 255D); humidity[index] = (byte) Math.round(climate.humidity * 255D);
            }
            return new TerrainLayer(width, height, temperature, humidity);
        }
    }

    private static TerrainClimate terrainClimate(int r, int g, int b) {
        if (r == 252 && g == 255 && b == 0) return new TerrainClimate(.88D, .04D);       // desert
        if (r == 6 && g == 200 && b == 11) return new TerrainClimate(.88D, .92D);         // jungle
        if (r == 0 && g == 86 && b == 6) return new TerrainClimate(.48D, .86D);           // forest
        if (r == 86 && g == 124 && b == 27) return new TerrainClimate(.52D, .42D);        // plains
        if (r == 132 && g == 255 && b == 0 || r == 240 && g == 255 && b == 0) return new TerrainClimate(.78D, .30D); // savanna
        if (r == 92 && g == 83 && b == 76) return new TerrainClimate(.28D, .30D);         // mountains
        if (r == 206 && g == 169 && b == 99) return new TerrainClimate(.50D, .36D);       // hills
        if (r == 0 && g == 82 && b == 82 || r == 58 && g == 131 && b == 82) return new TerrainClimate(.58D, .82D); // wetland
        if (r == 112 && g == 74 && b == 31 || r == 73 && g == 59 && b == 15) return new TerrainClimate(.44D, .34D); // rough highland
        if (r == 255 && g == 0 && (b == 24 || b == 127 || b == 240)) return new TerrainClimate(.50D, .45D); // city/special tiles
        if (r == 8 && g == 31 && b == 130 || r == 75 && g == 147 && b == 174) return new TerrainClimate(.50D, .50D); // water
        return new TerrainClimate(.50D, .45D);
    }

    private static double temperature(int r, int g, int b) {
        if (b >= r && b >= g) return 0.08D + 0.12D * g / 255D;
        float hue = Color.RGBtoHSB(r, g, b, null)[0];
        if (hue >= .25F && hue < .50F) return .42D;
        if (hue >= .12F) return .62D;
        if (hue >= .045F) return .80D;
        return .95D;
    }
    private static double clamp(double value) { return Math.max(0D, Math.min(1D, value)); }
    public record Climate(double temperature, double humidity, double relief) {}
    @FunctionalInterface private interface PixelDecoder { double decode(int r, int g, int b); }

    private record Data(int referenceWidth, int referenceHeight, ImageLayer temperature, ImageLayer temperateTrees, ImageLayer tropicalTrees, ImageLayer rivers, TerrainLayer terrain, ImageLayer relief) {}
    private record TerrainClimate(double temperature, double humidity) {
        boolean desert() { return temperature > .80D && humidity < .10D; }
    }
    private record TerrainLayer(int width, int height, byte[] temperatures, byte[] humidities) {
        TerrainClimate climate(double u, double v, double radiusPixels, double threshold) {
            TerrainClimate direct = new TerrainClimate(sample(temperatures, u, v), sample(humidities, u, v));
            if (radiusPixels <= 0D) return direct;
            int desertSamples = 0;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                double sampleU = u + dx * radiusPixels / width;
                double sampleV = v + dz * radiusPixels / height;
                if (sample(temperatures, sampleU, sampleV) > .80D && sample(humidities, sampleU, sampleV) < .10D) desertSamples++;
            }
            return desertSamples / 9D >= threshold ? new TerrainClimate(.88D, .04D) : direct;
        }
        private double sample(byte[] values, double u, double v) {
            double x = u * width, y = v * height; if (x < 0 || y < 0 || x >= width - 1 || y >= height - 1) return .5D;
            int x0 = (int) x, y0 = (int) y; double tx = x - x0, ty = y - y0;
            double a = value(values, x0, y0) * (1D - tx) + value(values, x0 + 1, y0) * tx;
            double b = value(values, x0, y0 + 1) * (1D - tx) + value(values, x0 + 1, y0 + 1) * tx;
            return a * (1D - ty) + b * ty;
        }
        private double value(byte[] values, int x, int y) { return (values[y * width + x] & 255) / 255D; }
    }
    private record ImageLayer(int width, int height, float[] values) {
        static ImageLayer read(BufferedImage image, PixelDecoder decoder) {
            int width = image.getWidth(), height = image.getHeight(); float[] values = new float[width * height];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) { int rgb = image.getRGB(x, y); values[y * width + x] = (float) decoder.decode(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255); }
            return new ImageLayer(width, height, values);
        }
        double sample(double u, double v) {
            double x = u * width, y = v * height; if (x < 0 || y < 0 || x >= width - 1 || y >= height - 1) return .5D;
            int x0 = (int)x, y0 = (int)y; double tx = x - x0, ty = y - y0;
            return (values[y0 * width + x0] * (1 - tx) + values[y0 * width + x0 + 1] * tx) * (1 - ty) + (values[(y0 + 1) * width + x0] * (1 - tx) + values[(y0 + 1) * width + x0 + 1] * tx) * ty;
        }
    }
}
