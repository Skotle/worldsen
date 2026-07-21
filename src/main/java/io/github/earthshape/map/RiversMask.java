package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import javax.imageio.ImageIO;

/**
 * Ownership and river data read from one source only: worldmap_river.png.
 * White (#FFFFFF) is land, gray (#7A7A7A) is water, and blue pixels are the
 * only candidates for rivers.  No neighbouring coast or generic biome is ever
 * converted into a river here.
 */
public final class RiversMask {
    public static final RiversMask INSTANCE = new RiversMask();

    private volatile Data data;

    private RiversMask() {}

    public int blocksPerPixel() {
        return EarthShapeServerConfig.BLOCKS_PER_PIXEL.get();
    }

    public int width() {
        return data().width;
    }

    public int height() {
        return data().height;
    }

    /** Exact map ownership for biome selection. */
    public double sampleLayerLand(int blockX, int blockZ) {
        Data map = data();
        int x = pixelX(blockX, map.width);
        int z = pixelZ(blockZ, map.height);
        return inside(x, z, map.width, map.height) && map.land.get(z * map.width + x) ? 1.0D : 0.0D;
    }

    /** Bilinear ownership only for density interpolation at the coastline. */
    public double sampleLand(int blockX, int blockZ) {
        Data map = data();
        double x = imageX(blockX, map.width);
        double z = imageZ(blockZ, map.height);
        if (x < 0.0D || z < 0.0D || x >= map.width - 1.0D || z >= map.height - 1.0D) return 0.0D;
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double tx = x - ix;
        double tz = z - iz;
        double north = lerp(map.land(ix, iz), map.land(ix + 1, iz), tx);
        double south = lerp(map.land(ix, iz + 1), map.land(ix + 1, iz + 1), tx);
        return lerp(north, south, tz);
    }

    /** Coast-only heightmap damping; it never changes land/ocean ownership. */
    public double sampleHeightmapInlandness(int blockX, int blockZ) {
        if (sampleLayerLand(blockX, blockZ) < 0.5D) return 0.0D;
        int fade = EarthShapeServerConfig.COAST_RELIEF_FADE_BLOCKS.get();
        int step = blocksPerPixel();
        int rings = Math.max(1, (int) Math.ceil(fade / (double) step));
        for (int ring = 1; ring <= rings; ring++) {
            int offset = ring * step;
            if (sampleLayerLand(blockX - offset, blockZ) < 0.5D
                    || sampleLayerLand(blockX + offset, blockZ) < 0.5D
                    || sampleLayerLand(blockX, blockZ - offset) < 0.5D
                    || sampleLayerLand(blockX, blockZ + offset) < 0.5D) {
                double t = (ring - 1.0D) / rings;
                return smooth(t);
            }
        }
        return 1.0D;
    }

    /** River relief is intentionally not separately carved.  The exact channel density owns it. */
    public double sampleRiverReliefFactor(int blockX, int blockZ) {
        return 1.0D;
    }

    public boolean isInlandRiver(int blockX, int blockZ) {
        if (!EarthShapeServerConfig.RIVERS_ENABLED.get()
                || sampleLayerLand(blockX, blockZ) < 0.5D
                || !riverSample(blockX, blockZ).insideWidth) return false;

        // A biome is sampled every four blocks.  Allowing a river at the very
        // edge of gray water makes those independent samples look like dotted
        // river biomes stamped over an ocean.  Keep only a continuous inland
        // corridor; the waterway simply joins the ocean at its final land edge.
        int margin = Math.max(16, blocksPerPixel() * 3);
        return sampleLayerLand(blockX - margin, blockZ) > 0.5D
                && sampleLayerLand(blockX + margin, blockZ) > 0.5D
                && sampleLayerLand(blockX, blockZ - margin) > 0.5D
                && sampleLayerLand(blockX, blockZ + margin) > 0.5D;
    }

    public boolean hasInlandRiverInfluence(int blockX, int blockZ) {
        return isInlandRiver(blockX, blockZ);
    }

    public boolean isNearInlandRiver(int blockX, int blockZ, int extraBlocks) {
        RiverSample sample = nearestRiver(blockX, blockZ);
        return EarthShapeServerConfig.RIVERS_ENABLED.get() && sample.widthBlocks > 0
                && sample.distanceBlocks <= sample.widthBlocks * 0.5D + Math.max(0, extraBlocks);
    }

    public int riverWidthBlocks(int blockX, int blockZ) {
        RiverSample sample = riverSample(blockX, blockZ);
        return sample.insideWidth ? sample.widthBlocks : 0;
    }

    public int effectiveRiverWidthBlocks(int blockX, int blockZ) {
        return riverWidthBlocks(blockX, blockZ);
    }

    /** Pixel-space distance is retained for density functions that use the source coordinate system. */
    public double riverCentrelineDistance(int blockX, int blockZ) {
        return nearestRiver(blockX, blockZ).distanceBlocks / Math.max(1, blocksPerPixel());
    }

    public boolean isInsideLegacyLayer(int blockX, int blockZ, int legacyWidth, int legacyHeight) {
        double x = legacyImageX(blockX, legacyWidth);
        double z = legacyImageZ(blockZ, legacyHeight);
        return x >= 0.0D && z >= 0.0D && x < legacyWidth && z < legacyHeight;
    }

    public double legacyImageX(int blockX, int legacyWidth) {
        Data map = data();
        return imageX(blockX, map.width) - (map.width - legacyWidth) * 0.5D;
    }

    public double legacyImageZ(int blockZ, int legacyHeight) {
        Data map = data();
        return imageZ(blockZ, map.height) - (map.height - legacyHeight) * 0.5D;
    }

    /** A strictly bounded source river sample.  The search only finds a line; it cannot widen it. */
    public RiverSample riverSample(int blockX, int blockZ) {
        RiverSample sample = nearestRiver(blockX, blockZ);
        return sample.insideWidth ? sample : RiverSample.NONE;
    }

    private RiverSample nearestRiver(int blockX, int blockZ) {
        Data map = data();
        double imageX = imageX(blockX, map.width);
        double imageZ = imageZ(blockZ, map.height);
        if (imageX < 0.0D || imageZ < 0.0D || imageX >= map.width || imageZ >= map.height) return RiverSample.NONE;

        int bpp = blocksPerPixel();
        int radius = Math.max(1, (int) Math.ceil((maximumConfiguredWidth() * 0.5D + 2.0D) / bpp) + 1);
        int centreX = (int) Math.floor(imageX);
        int centreZ = (int) Math.floor(imageZ);
        double best = Double.POSITIVE_INFINITY;
        int bestWidth = 0;

        for (int z = centreZ - radius; z <= centreZ + radius; z++) {
            for (int x = centreX - radius; x <= centreX + radius; x++) {
                if (inside(x, z, map.width, map.height)) {
                    int width = map.width(x, z);
                    if (width > 0) {
                        double dx = imageX - (x + 0.5D);
                        double dz = imageZ - (z + 0.5D);
                        double distance = Math.sqrt(dx * dx + dz * dz) * bpp;
                        if (distance < best) {
                            best = distance;
                            bestWidth = width;
                        }
                    }
                }
            }
        }

        if (bestWidth == 0) return RiverSample.NONE;
        boolean inside = best <= bestWidth * 0.5D + 2.0D;
        return new RiverSample(bestWidth, best, inside);
    }

    private int maximumConfiguredWidth() {
        return Math.max(EarthShapeServerConfig.RIVER_MINIMUM_WIDTH_BLOCKS.get(), Math.max(
                EarthShapeServerConfig.RIVER_WIDTH_000064.get(), Math.max(EarthShapeServerConfig.RIVER_WIDTH_000096.get(),
                Math.max(EarthShapeServerConfig.RIVER_WIDTH_0000C8.get(), Math.max(EarthShapeServerConfig.RIVER_WIDTH_0000FF.get(),
                Math.max(EarthShapeServerConfig.RIVER_WIDTH_0064FF.get(), Math.max(EarthShapeServerConfig.RIVER_WIDTH_00C8FF.get(),
                        EarthShapeServerConfig.RIVER_WIDTH_00E1FF.get())))))));
    }

    private Data data() {
        Data current = data;
        if (current != null) return current;
        synchronized (this) {
            if (data == null) data = load();
            return data;
        }
    }

    private static Data load() {
        long started = System.nanoTime();
        try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/worldmap_river.png")) {
            if (input == null) throw new IOException("missing worldmap_river.png");
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException("worldmap_river.png is not readable");
            int width = image.getWidth();
            int height = image.getHeight();
            BitSet land = new BitSet(width * height);
            BitSet rivers = new BitSet(width * height);
            byte[] widths = new byte[width * height];
            int[] row = new int[width];

            for (int z = 0; z < height; z++) {
                image.getRGB(0, z, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) {
                    int color = row[x] & 0xFFFFFF;
                    int riverWidth = riverWidthForColor(color);
                    int index = z * width + x;
                    if (color == 0xFFFFFF) land.set(index);
                    if (riverWidth > 0) {
                        rivers.set(index);
                        widths[index] = (byte) riverWidth;
                    }
                }
            }

            // Blue river ink replaces white land pixels in the source image.  It is
            // eligible as a land river only where it has substantial white-land
            // support.  This keeps broad inland strokes from becoming ocean biomes,
            // while preventing a river mouth from pulling a straight land trench out
            // through the gray ocean.
            for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
                int x = index % width;
                int z = index / width;
                int support = 0;
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dx = -4; dx <= 4; dx++) {
                        int sx = x + dx;
                        int sz = z + dz;
                        if (inside(sx, sz, width, height) && land.get(sz * width + sx)) support++;
                    }
                }
                if (support >= 8) land.set(index);
            }

            EarthShape.LOGGER.info("[EarthShape] Layer ownership loaded: {}x{} ({} ms).", width, height,
                    (System.nanoTime() - started) / 1_000_000L);
            return new Data(width, height, land, rivers, widths);
        } catch (IOException exception) {
            throw new IllegalStateException("EarthShape could not load worldmap_river.png", exception);
        }
    }

    private static int riverWidthForColor(int color) {
        int red = color >>> 16 & 0xFF;
        int green = color >>> 8 & 0xFF;
        int blue = color & 0xFF;
        if (red > 48 || blue < 128 || blue < green + 24 || blue < red + 80) return 0;
        int[] palette = {0x000064, 0x000096, 0x0000C8, 0x0000FF, 0x0064FF, 0x00C8FF, 0x00E1FF};
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int candidate : palette) {
            int cr = candidate >>> 16 & 0xFF;
            int cg = candidate >>> 8 & 0xFF;
            int cb = candidate & 0xFF;
            int distance = (red - cr) * (red - cr) + (green - cg) * (green - cg) + (blue - cb) * (blue - cb);
            if (distance < bestDistance) { bestDistance = distance; best = candidate; }
        }
        if (bestDistance > 2400) return 0;
        int configured = switch (best) {
            case 0x000064 -> EarthShapeServerConfig.RIVER_WIDTH_000064.get();
            case 0x000096 -> EarthShapeServerConfig.RIVER_WIDTH_000096.get();
            case 0x0000C8 -> EarthShapeServerConfig.RIVER_WIDTH_0000C8.get();
            case 0x0000FF -> EarthShapeServerConfig.RIVER_WIDTH_0000FF.get();
            case 0x0064FF -> EarthShapeServerConfig.RIVER_WIDTH_0064FF.get();
            case 0x00C8FF -> EarthShapeServerConfig.RIVER_WIDTH_00C8FF.get();
            default -> EarthShapeServerConfig.RIVER_WIDTH_00E1FF.get();
        };
        return Math.max(EarthShapeServerConfig.RIVER_MINIMUM_WIDTH_BLOCKS.get(),
                (int) Math.round(configured * EarthShapeServerConfig.RIVER_WIDTH_SCALE.get()));
    }

    private static double lerp(double from, double to, double amount) { return from + (to - from) * amount; }
    private static double smooth(double value) { return value * value * (3.0D - 2.0D * value); }
    private static boolean inside(int x, int z, int width, int height) { return x >= 0 && z >= 0 && x < width && z < height; }

    public record RiverSample(int widthBlocks, double distanceBlocks, boolean insideWidth) {
        static final RiverSample NONE = new RiverSample(0, Double.POSITIVE_INFINITY, false);
    }

    private record Data(int width, int height, BitSet land, BitSet rivers, byte[] widths) {
        double land(int x, int z) { return land.get(z * width + x) ? 1.0D : 0.0D; }
        int width(int x, int z) { return widths[z * width + x] & 0xFF; }
    }

    private double imageX(int blockX, int width) { return blockX / (double) blocksPerPixel() + width * 0.5D; }
    private double imageZ(int blockZ, int height) { return blockZ / (double) blocksPerPixel() + height * 0.5D; }
    private int pixelX(int blockX, int width) { return (int) Math.floor(imageX(blockX, width)); }
    private int pixelZ(int blockZ, int height) { return (int) Math.floor(imageZ(blockZ, height)); }
}
