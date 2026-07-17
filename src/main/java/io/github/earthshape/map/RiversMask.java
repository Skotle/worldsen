package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import javax.imageio.ImageIO;

/** Full-world land/ocean mask and river hierarchy from the bundled Worldmap_river.png layer. */
public final class RiversMask {
    public static final RiversMask INSTANCE = new RiversMask();
    public static final int DEFAULT_BLOCKS_PER_PIXEL = 20;
    private static final int RIVER_SEARCH_RADIUS = 4;

    private volatile Data data;

    private RiversMask() {}

    /** Returns a bilinear land value: 0 for open sea and 1 for land. */
    public double sampleLand(int blockX, int blockZ) {
        Data loaded = data();
        return sampleLand(loaded, blockX, blockZ);
    }

    /**
     * Coast-only low-pass filtering for density functions.  The original layer remains the
     * source of truth, but one 20-block binary BMP edge is too narrow for vanilla's vertical
     * terrain response and produces a cliff.  This spreads only the density transition.
     */
    public double sampleCoastLand(int blockX, int blockZ) {
        Data loaded = data();
        // Use a physical block-radius, not a fixed source-pixel radius.  At 4 blocks/pixel
        // the old 4-pixel blur was only 16 blocks wide and recreated coastal cliffs.
        int radiusBlocks = Math.max(32, EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get() / 2);
        int sampleStep = Math.max(1, radiusBlocks / 2);
        int sampleRadius = 2;
        double total = 0.0D;
        double weight = 0.0D;
        for (int dz = -sampleRadius; dz <= sampleRadius; dz++) {
            for (int dx = -sampleRadius; dx <= sampleRadius; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > sampleRadius) continue;
                double sampleWeight = sampleRadius + 1.0D - distance;
                total += sampleLand(loaded, blockX + dx * sampleStep, blockZ + dz * sampleStep) * sampleWeight;
                weight += sampleWeight;
            }
        }
        return total / weight;
    }

    /**
     * Conservative inland distance for heightmap relief only.  Continentalness keeps the
     * exact coast shape; high raster elevation is withheld until several source pixels inland.
     */
    public double sampleHeightmapInlandness(int blockX, int blockZ) {
        Data loaded = data();
        int step = blocksPerPixel();
        int fadeBlocks = EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get();
        int rings = Math.max(1, (int) Math.ceil(fadeBlocks / (double) step));
        for (int ring = 1; ring <= rings; ring++) {
            int offset = ring * step;
            if (sampleLand(loaded, blockX - offset, blockZ) < 0.5D
                    || sampleLand(loaded, blockX + offset, blockZ) < 0.5D
                    || sampleLand(loaded, blockX, blockZ - offset) < 0.5D
                    || sampleLand(loaded, blockX, blockZ + offset) < 0.5D
                    || sampleLand(loaded, blockX - offset, blockZ - offset) < 0.5D
                    || sampleLand(loaded, blockX + offset, blockZ - offset) < 0.5D
                    || sampleLand(loaded, blockX - offset, blockZ + offset) < 0.5D
                    || sampleLand(loaded, blockX + offset, blockZ + offset) < 0.5D) {
                double t = Math.min(1.0D, (ring - 1D) * step / fadeBlocks);
                return t * t * (3.0D - 2.0D * t);
            }
        }
        return 1.0D;
    }

    /** Suppresses heightmap mountain relief beside a source river without changing its biome. */
    public double sampleRiverReliefFactor(int blockX, int blockZ) {
        if (!EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()) return 1.0D;
        double distance = riverCentrelineDistance(blockX, blockZ);
        double t = Math.max(0.0D, Math.min(1.0D,
                distance * blocksPerPixel() / EarthShapeServerConfig.RIVER_HEIGHT_FADE_BLOCKS.get()));
        return t * t * (3.0D - 2.0D * t);
    }

    private double sampleLand(Data loaded, int blockX, int blockZ) {
        int blocksPerPixel = blocksPerPixel();
        double imageX = blockX / (double) blocksPerPixel + loaded.width * 0.5D;
        double imageZ = blockZ / (double) blocksPerPixel + loaded.height * 0.5D;
        if (imageX < 0.0D || imageZ < 0.0D || imageX >= loaded.width - 1 || imageZ >= loaded.height - 1) return 0.0D;
        int x = (int) Math.floor(imageX);
        int z = (int) Math.floor(imageZ);
        double tx = imageX - x;
        double tz = imageZ - z;
        double a = lerp(loaded.land(x, z), loaded.land(x + 1, z), tx);
        double b = lerp(loaded.land(x, z + 1), loaded.land(x + 1, z + 1), tx);
        return lerp(a, b, tz);
    }

    public int blocksPerPixel() { return EarthShapeServerConfig.BLOCKS_PER_PIXEL.get(); }
    public int width() { return data().width; }
    public int height() { return data().height; }

    /** Maps a block coordinate into a centred legacy 5632x2048 layer. */
    public double legacyImageX(int blockX, int legacyWidth) {
        Data loaded = data();
        return blockX / (double) blocksPerPixel() + loaded.width * 0.5D - (loaded.width - legacyWidth) * 0.5D;
    }

    /** Maps a block coordinate into a centred legacy 5632x2048 layer. */
    public double legacyImageZ(int blockZ, int legacyHeight) {
        Data loaded = data();
        return blockZ / (double) blocksPerPixel() + loaded.height * 0.5D - (loaded.height - legacyHeight) * 0.5D;
    }

    public boolean isInsideLegacyLayer(int blockX, int blockZ, int legacyWidth, int legacyHeight) {
        double x = legacyImageX(blockX, legacyWidth);
        double z = legacyImageZ(blockZ, legacyHeight);
        return x >= 0.0D && z >= 0.0D && x < legacyWidth && z < legacyHeight;
    }

    /** Samples river width encoded by the source colour, capped at 27 Minecraft blocks. */
    public boolean isRiverCentreline(int blockX, int blockZ) {
        int widthBlocks = riverWidthBlocks(blockX, blockZ);
        if (widthBlocks == 0) return false;
        double halfWidthPixels = Math.max(widthBlocks, EarthShapeServerConfig.RIVER_MINIMUM_WIDTH_BLOCKS.get())
                / (2.0D * blocksPerPixel());
        return riverCentrelineDistance(blockX, blockZ) <= halfWidthPixels;
    }

    /** Width of the closest source river line in Minecraft blocks.  Zero means no river. */
    public int riverWidthBlocks(int blockX, int blockZ) {
        Data loaded = data();
        double imageX = blockX / (double) blocksPerPixel() + loaded.width * 0.5D;
        double imageZ = blockZ / (double) blocksPerPixel() + loaded.height * 0.5D;
        if (imageX < 1.0D || imageZ < 1.0D || imageX >= loaded.width - 1.0D || imageZ >= loaded.height - 1.0D) return 0;
        int centreX = (int) Math.floor(imageX);
        int centreZ = (int) Math.floor(imageZ);
        double best = Double.POSITIVE_INFINITY;
        int width = 0;
        for (int z = centreZ - RIVER_SEARCH_RADIUS; z <= centreZ + RIVER_SEARCH_RADIUS; z++) {
            for (int x = centreX - RIVER_SEARCH_RADIUS; x <= centreX + RIVER_SEARCH_RADIUS; x++) {
                int candidate = loaded.riverWidth(x, z);
                if (candidate == 0) continue;
                double distance = distanceSquared(imageX, imageZ, x + 0.5D, z + 0.5D, x + 0.5D, z + 0.5D);
                if (distance < best) { best = distance; width = candidate; }
            }
        }
        return width;
    }

    /** Distance in source pixels to the connected blue river centreline. */
    public double riverCentrelineDistance(int blockX, int blockZ) {
        Data loaded = data();
        double imageX = blockX / (double) blocksPerPixel() + loaded.width * 0.5D;
        double imageZ = blockZ / (double) blocksPerPixel() + loaded.height * 0.5D;
        if (imageX < 1.0D || imageZ < 1.0D || imageX >= loaded.width - 1.0D || imageZ >= loaded.height - 1.0D) return Double.POSITIVE_INFINITY;
        int centreX = (int) Math.floor(imageX);
        int centreZ = (int) Math.floor(imageZ);
        double best = Double.POSITIVE_INFINITY;
        for (int z = centreZ - RIVER_SEARCH_RADIUS; z <= centreZ + RIVER_SEARCH_RADIUS; z++) {
            for (int x = centreX - RIVER_SEARCH_RADIUS; x <= centreX + RIVER_SEARCH_RADIUS; x++) {
                if (!loaded.river(x, z)) continue;
                // An isolated source pixel is still a tiny river point.
                best = Math.min(best, Math.sqrt(distanceSquared(imageX, imageZ, x + 0.5D, z + 0.5D, x + 0.5D, z + 0.5D)));
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx != 0 || dz != 0) && loaded.river(x + dx, z + dz)) {
                            best = Math.min(best, Math.sqrt(distanceSquared(imageX, imageZ, x + 0.5D, z + 0.5D, x + dx + 0.5D, z + dz + 0.5D)));
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double distanceSquared(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double length = dx * dx + dz * dz;
        if (length == 0.0D) { double ox = px - ax, oz = pz - az; return ox * ox + oz * oz; }
        double t = Math.max(0.0D, Math.min(1.0D, ((px - ax) * dx + (pz - az) * dz) / length));
        double ox = px - (ax + t * dx), oz = pz - (az + t * dz);
        return ox * ox + oz * oz;
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
            if (input == null) throw new IOException("missing /earthshape/hoi4/worldmap_river.png");
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException("worldmap_river.png is not readable");
            int width = image.getWidth();
            int height = image.getHeight();
            BitSet land = new BitSet(width * height);
            BitSet rivers = new BitSet(width * height);
            byte[] riverWidths = new byte[width * height];
            int[] row = new int[width];
            for (int z = 0; z < height; z++) {
                image.getRGB(0, z, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) {
                    int rgb = row[x];
                    int red = (rgb >>> 16) & 255;
                    int green = (rgb >>> 8) & 255;
                    int blue = rgb & 255;
                    // Full-map water is neutral mid-grey; dark province borders remain land.
                    // The original rivers.bmp is already centred inside this source image.
                    if (!isFullMapOcean(red, green, blue)) land.set(z * width + x);
                    int riverWidth = riverWidthForColor(red, green, blue);
                    if (riverWidth > 0) {
                        int index = z * width + x;
                        rivers.set(index);
                        riverWidths[index] = (byte) riverWidth;
                    }
                }
            }
            bridgeSmallRiverGaps(width, height, rivers, riverWidths);
            EarthShape.LOGGER.info("[EarthShape] Worldmap_river.png mask loaded: {}x{} in {} ms.",
                    width, height, (System.nanoTime() - started) / 1_000_000L);
            return new Data(width, height, land, rivers, riverWidths);
        } catch (IOException exception) {
            throw new IllegalStateException("EarthShape could not load Worldmap_river.png", exception);
        }
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /**
     * Connects only facing river endpoints separated by one or two missing raster pixels.
     * This repairs export/anti-alias gaps before biome sampling without joining nearby branches.
     */
    private static void bridgeSmallRiverGaps(int width, int height, BitSet rivers, byte[] riverWidths) {
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
            int x = index % width;
            int z = index / width;
            if (riverNeighbours(x, z, width, height, rivers) > 1) continue;
            for (int[] direction : directions) {
                for (int missing = 1; missing <= 2; missing++) {
                    int targetX = x + direction[0] * (missing + 1);
                    int targetZ = z + direction[1] * (missing + 1);
                    if (!inside(targetX, targetZ, width, height)) continue;
                    int target = targetZ * width + targetX;
                    if (!rivers.get(target) || riverNeighbours(targetX, targetZ, width, height, rivers) > 1) continue;
                    boolean clear = true;
                    for (int step = 1; step <= missing; step++) {
                        if (rivers.get((z + direction[1] * step) * width + x + direction[0] * step)) { clear = false; break; }
                    }
                    if (!clear) continue;
                    byte bridgeWidth = (byte) Math.min(riverWidths[index] & 255, riverWidths[target] & 255);
                    for (int step = 1; step <= missing; step++) {
                        int bridge = (z + direction[1] * step) * width + x + direction[0] * step;
                        rivers.set(bridge);
                        riverWidths[bridge] = bridgeWidth;
                    }
                }
            }
        }
    }

    private static int riverNeighbours(int x, int z, int width, int height, BitSet rivers) {
        int count = 0;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            int px = x + dx, pz = z + dz;
            if (inside(px, pz, width, height) && rivers.get(pz * width + px)) count++;
        }
        return count;
    }

    private static boolean inside(int x, int z, int width, int height) {
        return x >= 0 && z >= 0 && x < width && z < height;
    }

    private static boolean isFullMapOcean(int red, int green, int blue) {
        // #7A7A7A is the sea fill.  Its anti-aliased edge shades stay in this narrow grey band.
        return Math.abs(red - green) <= 2 && Math.abs(green - blue) <= 2 && red >= 100 && red <= 170;
    }

    /** HOI4 blue river hierarchy: darker lines represent wider rivers. */
    private static int riverWidthForColor(int red, int green, int blue) {
        if (red != 0) return 0;
        int configuredWidth = switch ((green << 8) | blue) {
            case 0x0064 -> EarthShapeServerConfig.RIVER_WIDTH_000064.get();
            case 0x0096 -> EarthShapeServerConfig.RIVER_WIDTH_000096.get();
            case 0x00C8 -> EarthShapeServerConfig.RIVER_WIDTH_0000C8.get();
            case 0x00FF -> EarthShapeServerConfig.RIVER_WIDTH_0000FF.get();
            case 0x64FF -> EarthShapeServerConfig.RIVER_WIDTH_0064FF.get();
            case 0xC8FF -> EarthShapeServerConfig.RIVER_WIDTH_00C8FF.get();
            case 0xE1FF -> EarthShapeServerConfig.RIVER_WIDTH_00E1FF.get();
            default -> 0;
        };
        return configuredWidth == 0 ? 0 : Math.max(1, (int) Math.round(configuredWidth * EarthShapeServerConfig.RIVER_WIDTH_SCALE.get()));
    }

    private record Data(int width, int height, BitSet land, BitSet rivers, byte[] riverWidths) {
        double land(int x, int z) { return land.get(z * width + x) ? 1.0D : 0.0D; }
        boolean river(int x, int z) { return x >= 0 && z >= 0 && x < width && z < height && rivers.get(z * width + x); }
        int riverWidth(int x, int z) { return x >= 0 && z >= 0 && x < width && z < height ? riverWidths[z * width + x] & 255 : 0; }
    }
}
