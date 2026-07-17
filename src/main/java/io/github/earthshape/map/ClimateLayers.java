package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Climate rasters sampled in the same normalized world rectangle as rivers.bmp. */
public final class ClimateLayers {
    public static final ClimateLayers INSTANCE = new ClimateLayers();
    private volatile Data temperature, trees, terrain, normal;
    private ClimateLayers() {}

    public double temperature(int x, int z) {
        Data layer = temperature();
        // The added margins have no legacy climate pixels.  Use latitude-based polar climate
        // there instead of a constant value, which created a rectangular frozen-ocean seam at
        // the exact edge of the centred 5632x2048 source rectangle.
        if (!RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height)) return outerLatitudeTemperature(z);
        return sample(layer, x, z) * 2.0D - 1.0D;
    }
    /** True only where the original 5632x2048 temperature raster exists. */
    public boolean hasLegacyTemperature(int x, int z) {
        Data layer = temperature();
        return RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height);
    }
    public double vegetation(int x, int z) { return sample(trees(), x, z) * 2.0D - 1.0D; }
    public TerrainKind terrainKind(int x, int z) {
        Data layer = terrain();
        if (!RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height)) return TerrainKind.PLAINS;
        int imageX = sourceX(layer, x), imageZ = sourceZ(layer, z);
        TerrainKind kind = TerrainKind.byCode(layer.values[imageZ * layer.width + imageX] & 255);
        return kind == TerrainKind.CITY || kind == TerrainKind.SURROUNDING ? surroundingLandKind(layer, imageX, imageZ) : kind;
    }
    public double desert(int x, int z) { return terrainKind(x, z) == TerrainKind.DESERT ? 1.0D : 0.0D; }
    public double steepness(int x, int z) { return sample(normal(), x, z); }

    /** Geographic bands for the only two map regions allowed to retain badlands/mesa biomes. */
    public boolean isMesaRegion(int blockX, int blockZ) {
        double u = (blockX / (double) RiversMask.INSTANCE.blocksPerPixel() + RiversMask.INSTANCE.width() * 0.5D) / RiversMask.INSTANCE.width();
        double v = (blockZ / (double) RiversMask.INSTANCE.blocksPerPixel() + RiversMask.INSTANCE.height() * 0.5D) / RiversMask.INSTANCE.height();
        boolean americas = u > 0.05D && u < 0.43D && v > 0.08D && v < 0.88D;
        boolean oceania = u > 0.73D && u < 0.97D && v > 0.50D && v < 0.92D;
        return americas || oceania;
    }

    private Data temperature() { Data v = temperature; if (v != null) return v; synchronized (this) { return temperature = temperature == null ? load("earth_temperature.png", Kind.TEMPERATURE) : temperature; } }
    private Data trees() { Data v = trees; if (v != null) return v; synchronized (this) { return trees = trees == null ? load("trees.bmp", Kind.VEGETATION) : trees; } }
    private Data terrain() { Data v = terrain; if (v != null) return v; synchronized (this) { return terrain = terrain == null ? load("terrain.bmp", Kind.TERRAIN_CLASS) : terrain; } }
    private Data normal() { Data v = normal; if (v != null) return v; synchronized (this) { return normal = normal == null ? load("world_normal.bmp", Kind.NORMAL) : normal; } }

    private static double sample(Data layer, int blockX, int blockZ) {
        // Legacy climate rasters remain unscaled in the centred 5632x2048 rectangle.
        double imageX = RiversMask.INSTANCE.legacyImageX(blockX, layer.width);
        double imageZ = RiversMask.INSTANCE.legacyImageZ(blockZ, layer.height);
        if (imageX < 0 || imageZ < 0 || imageX >= layer.width - 1D || imageZ >= layer.height - 1D) return 0.5D;
        int x = (int) imageX, z = (int) imageZ;
        double tx = imageX - x, tz = imageZ - z;
        return lerp(lerp(layer.value(x, z), layer.value(x + 1, z), tx), lerp(layer.value(x, z + 1), layer.value(x + 1, z + 1), tx), tz);
    }

    private static int sourceX(Data layer, int blockX) {
        double imageX = RiversMask.INSTANCE.legacyImageX(blockX, layer.width);
        return Math.max(0, Math.min(layer.width - 1, (int) imageX));
    }
    private static int sourceZ(Data layer, int blockZ) {
        double imageZ = RiversMask.INSTANCE.legacyImageZ(blockZ, layer.height);
        return Math.max(0, Math.min(layer.height - 1, (int) imageZ));
    }
    /** HOI4 city pixels are not terrain: use the nearest surrounding natural terrain ring. */
    private static TerrainKind surroundingLandKind(Data layer, int centreX, int centreZ) {
        for (int radius = 1; radius <= 48; radius++) {
            int[] counts = new int[TerrainKind.values().length];
            for (int z = Math.max(0, centreZ - radius); z <= Math.min(layer.height - 1, centreZ + radius); z++) {
                for (int x = Math.max(0, centreX - radius); x <= Math.min(layer.width - 1, centreX + radius); x++) {
                    if (Math.max(Math.abs(x - centreX), Math.abs(z - centreZ)) != radius) continue;
                    TerrainKind kind = TerrainKind.byCode(layer.values[z * layer.width + x] & 255);
                    if (kind != TerrainKind.CITY && kind != TerrainKind.SURROUNDING
                            && kind != TerrainKind.WATER && kind != TerrainKind.DESERT) counts[kind.code]++;
                }
            }
            TerrainKind result = TerrainKind.PLAINS;
            for (TerrainKind kind : TerrainKind.values()) if (counts[kind.code] > counts[result.code]) result = kind;
            if (counts[result.code] > 0) return result;
        }
        return TerrainKind.PLAINS;
    }

    private static Data load(String name, Kind kind) {
        try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/" + name)) {
            if (input == null) throw new IOException("missing " + name);
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException(name + " is not readable");
            int width = image.getWidth(), height = image.getHeight();
            byte[] values = new byte[width * height];
            int[] row = new int[width];
            for (int z = 0; z < height; z++) { image.getRGB(0, z, width, 1, row, 0, width); for (int x = 0; x < width; x++) values[z * width + x] = (byte) kind.value(row[x]); }
            EarthShape.LOGGER.info("[EarthShape] {} climate layer loaded: {}x{}.", name, width, height);
            return new Data(width, height, values);
        } catch (IOException exception) { throw new IllegalStateException("EarthShape could not load " + name, exception); }
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double outerLatitudeTemperature(int blockZ) {
        double imageZ = blockZ / (double) RiversMask.INSTANCE.blocksPerPixel() + RiversMask.INSTANCE.height() * 0.5D;
        double latitude = Math.abs(imageZ / Math.max(1.0D, RiversMask.INSTANCE.height() - 1.0D) * 2.0D - 1.0D);
        // Mild around the equator, progressively polar toward both map edges.
        return -0.05D - 0.75D * latitude * latitude;
    }
    private enum Kind {
        LUMINANCE { int value(int c) { return (((c >>> 16) & 255) * 30 + ((c >>> 8) & 255) * 59 + (c & 255) * 11) / 100; } },
        // earth_temperature.png is a false-colour temperature map: red/yellow is hot and blue/purple is cold.
        TEMPERATURE { int value(int c) { return Math.max(0, Math.min(255, 127 + (((c >>> 16) & 255) - (c & 255)) / 2)); } },
        // trees.bmp is a tree-cover mask, not a rainfall map.  Black is ordinary open ground (neutral),
        // rather than desert; terrain.bmp alone supplies the forced dry desert classification.
        VEGETATION { int value(int c) { int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255; return r < 8 && g < 8 && b < 8 ? 128 : 218; } },
        TERRAIN_CLASS { int value(int c) { return TerrainKind.fromColor(c).code; } },
        // Tangent-space normal maps encode the flat-facing component in blue; slope is red/green.
        NORMAL { int value(int c) { double x = (((c >>> 16) & 255) - 127.5D) / 127.5D, z = (((c >>> 8) & 255) - 127.5D) / 127.5D; return (int) (255 * Math.min(1, Math.sqrt(x * x + z * z))); } };
        abstract int value(int color);
    }

    /** Broad HOI4 terrain palette categories. Province borders and city symbols do not exist in terrain.bmp. */
    public enum TerrainKind {
        WATER(0), DESERT(1), WETLAND(2), FOREST(3), JUNGLE(4), PLAINS(5), HILLS(6), MOUNTAIN(7), CITY(8), SURROUNDING(9);
        private final int code;
        TerrainKind(int code) { this.code = code; }
        static TerrainKind byCode(int code) { for (TerrainKind kind : values()) if (kind.code == code) return kind; return PLAINS; }
        static TerrainKind fromColor(int color) {
            return switch (color & 0xFFFFFF) {
                case 0x3A8352, 0x005606, 0x06C80B -> FOREST;
                case 0x005252 -> JUNGLE;
                case 0x704A1F, 0x86541E, 0x728969 -> HILLS;
                case 0x493B0F, 0x5C534C, 0xAE00FF -> MOUNTAIN;
                case 0x4B93AE -> WETLAND;
                case 0xCEA963, 0xFCFF00 -> DESERT;
                // This HOI4 marker colour is not terrain.  Replace every such tile with
                // the nearest surrounding natural terrain instead of turning it into desert.
                case 0xF0FF00 -> SURROUNDING;
                case 0xFF0018, 0xFF00F0, 0xFF007F, 0xFFFFFF -> CITY;
                case 0x081F82 -> WATER;
                case 0x567C1B, 0x84FF00 -> PLAINS;
                default -> PLAINS;
            };
        }
    }
    private record Data(int width, int height, byte[] values) { double value(int x, int z) { return (values[z * width + x] & 255) / 255.0D; } }
}
