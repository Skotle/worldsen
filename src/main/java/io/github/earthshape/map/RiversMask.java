package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.BitSet;
import javax.imageio.ImageIO;

/** Full-world coastline and river hierarchy from separate, aligned source layers. */
public final class RiversMask {
    public static final RiversMask INSTANCE = new RiversMask();
    public static final int DEFAULT_BLOCKS_PER_PIXEL = 20;
    private static final int RIVER_SEARCH_RADIUS = 4;

    private volatile Data data;

    private RiversMask() {}

    /** Returns a bilinear land value: 0 for open sea and 1 for land. */
    public double sampleLand(int blockX, int blockZ) {
        return sampleLand(data(), blockX, blockZ);
    }

    /**
     * Exact no-river coastline layer class used for continent and surface-biome ownership.
     * #FFFFFF is always land and #7A7A7A is always ocean; this avoids a river or terrain
     * layer changing the ownership of a source-map pixel at the coast.
     */
    public double sampleLayerLand(int blockX, int blockZ) {
        Data loaded = data();
        int blocksPerPixel = blocksPerPixel();
        double imageX = blockX / (double) blocksPerPixel + loaded.width * 0.5D;
        double imageZ = blockZ / (double) blocksPerPixel + loaded.height * 0.5D;
        if (imageX < 0.0D || imageZ < 0.0D || imageX >= loaded.width || imageZ >= loaded.height) return 0.0D;
        return loaded.land((int) Math.floor(imageX), (int) Math.floor(imageZ));
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
        // Retain a safe physical shelf even if an existing world keeps an older config.
        int radiusBlocks = Math.max(160, EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get() / 2);
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
        int fadeBlocks = Math.max(320, EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get());
        int rings = Math.max(1, (int) Math.ceil(fadeBlocks / (double) step));
        for (int ring = 1; ring <= rings; ring++) {
            int offset = ring * step;
            // A layer river is water only after its own river-density path is accepted.
            // It must not be an ocean pixel for the heightmap's coast-distance scan;
            // otherwise every thin blue line becomes a 320-block-wide false coast.
            if (sampleReliefLand(loaded, blockX - offset, blockZ) < 0.5D
                    || sampleReliefLand(loaded, blockX + offset, blockZ) < 0.5D
                    || sampleReliefLand(loaded, blockX, blockZ - offset) < 0.5D
                    || sampleReliefLand(loaded, blockX, blockZ + offset) < 0.5D
                    || sampleReliefLand(loaded, blockX - offset, blockZ - offset) < 0.5D
                    || sampleReliefLand(loaded, blockX + offset, blockZ - offset) < 0.5D
                    || sampleReliefLand(loaded, blockX - offset, blockZ + offset) < 0.5D
                    || sampleReliefLand(loaded, blockX + offset, blockZ + offset) < 0.5D) {
                double t = Math.min(1.0D, (ring - 1D) * step / fadeBlocks);
                return t * t * (3.0D - 2.0D * t);
            }
        }
        return 1.0D;
    }

    /** Suppresses heightmap mountain relief beside a source river without changing its biome. */
    public double sampleRiverReliefFactor(int blockX, int blockZ) {
        if (!EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()) return 1.0D;
        if (!hasInlandRiverInfluence(blockX, blockZ)) return 1.0D;
        double distance = riverCentrelineDistance(blockX, blockZ) * blocksPerPixel();
        double riverRadius = effectiveRiverWidthBlocks(blockX, blockZ) * 0.5D;
        int fadeBlocks = Math.max(160, EarthShapeServerConfig.RIVER_HEIGHT_FADE_BLOCKS.get());
        double t = Math.max(0.0D, Math.min(1.0D,
                (distance - riverRadius) / fadeBlocks));
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

    /**
     * Land used only when measuring distance to the real ocean for heightmap relief.
     * Source river ink is included so it cannot masquerade as an ocean edge; the normal
     * land mask remains unchanged for continentalness and coastline placement.
     */
    private double sampleReliefLand(Data loaded, int blockX, int blockZ) {
        int blocksPerPixel = blocksPerPixel();
        double imageX = blockX / (double) blocksPerPixel + loaded.width * 0.5D;
        double imageZ = blockZ / (double) blocksPerPixel + loaded.height * 0.5D;
        if (imageX < 0.0D || imageZ < 0.0D || imageX >= loaded.width - 1 || imageZ >= loaded.height - 1) return 0.0D;
        int x = (int) Math.floor(imageX);
        int z = (int) Math.floor(imageZ);
        double tx = imageX - x;
        double tz = imageZ - z;
        double a = lerp(loaded.reliefLand(x, z), loaded.reliefLand(x + 1, z), tx);
        double b = lerp(loaded.reliefLand(x, z + 1), loaded.reliefLand(x + 1, z + 1), tx);
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
        int widthBlocks = effectiveRiverWidthBlocks(blockX, blockZ);
        if (widthBlocks == 0) return false;
        // Do not promote every connected thin source line to the legacy 8-block minimum.
        // At four blocks per pixel that doubled narrow rivers into obvious blue corridors.
        double halfWidthPixels = Math.max(4, widthBlocks) / (2.0D * blocksPerPixel());
        return riverCentrelineDistance(blockX, blockZ) <= halfWidthPixels;
    }

    /**
     * Returns true only for a source river with a real land buffer on every side.  Coastline
     * strokes and tiny islands otherwise receive the minimum river width and can disappear
     * beneath their own generated channel at coarse map scales.
     */
    public boolean isInlandRiver(int blockX, int blockZ) {
        return isRiverCentreline(blockX, blockZ) && hasInlandRiverInfluence(blockX, blockZ);
    }

    /** A dry river-bank biome strip; this changes surface material, not water width. */
    public boolean isInlandRiverBank(int blockX, int blockZ) {
        if (!hasInlandRiverInfluence(blockX, blockZ)) return false;
        int width = effectiveRiverWidthBlocks(blockX, blockZ);
        if (width == 0) return false;
        double distanceBlocks = riverCentrelineDistance(blockX, blockZ) * blocksPerPixel();
        return distanceBlocks <= Math.max(12.0D, width * 0.5D + 12.0D);
    }

    /**
     * A small dry exclusion band around a verified layer river.  This is deliberately
     * separate from {@link #isInlandRiverBank(int, int)}: it prevents vanilla beach
     * selection from painting sand beside a river without widening the river biome or
     * its water channel.
     */
    public boolean isNearInlandRiver(int blockX, int blockZ, int exclusionBlocks) {
        if (!hasInlandRiverInfluence(blockX, blockZ)) return false;
        int width = effectiveRiverWidthBlocks(blockX, blockZ);
        if (width == 0) return false;
        double distanceBlocks = riverCentrelineDistance(blockX, blockZ) * blocksPerPixel();
        return distanceBlocks <= Math.max(exclusionBlocks, width * 0.5D);
    }

    /**
     * True only for a source river well inside a continent.  The source artwork contains
     * some coast-adjacent blue strokes; accepting their small local land buffer turns an
     * entire shoreline into river biome and destroys the coastline silhouette.
     */
    public boolean hasInlandRiverInfluence(int blockX, int blockZ) {
        if (riverWidthBlocks(blockX, blockZ) == 0 || sampleLayerLand(blockX, blockZ) < 0.5D) return false;
        int margin = Math.max(192, EarthShapeServerConfig.RIVER_MINIMUM_INLAND_BLOCKS.get());
        Data loaded = data();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                if (sampleLayerLand(blockX + dx * margin, blockZ + dz * margin) < 0.5D) return false;
            }
        }
        return true;
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

    /**
     * Keeps the configurable fallback only for an isolated exported pixel. Normal connected
     * strokes use their exact palette width, so small colour classes do not collapse into the
     * same minimum width as major rivers.
     */
    public int effectiveRiverWidthBlocks(int blockX, int blockZ) {
        Data loaded = data();
        double imageX = blockX / (double) blocksPerPixel() + loaded.width * 0.5D;
        double imageZ = blockZ / (double) blocksPerPixel() + loaded.height * 0.5D;
        int centreX = (int) Math.floor(imageX), centreZ = (int) Math.floor(imageZ);
        double best = Double.POSITIVE_INFINITY;
        int width = 0, nearestX = 0, nearestZ = 0;
        for (int z = centreZ - RIVER_SEARCH_RADIUS; z <= centreZ + RIVER_SEARCH_RADIUS; z++) for (int x = centreX - RIVER_SEARCH_RADIUS; x <= centreX + RIVER_SEARCH_RADIUS; x++) {
            int candidate = loaded.riverWidth(x, z);
            if (candidate == 0) continue;
            double distance = distanceSquared(imageX, imageZ, x + 0.5D, z + 0.5D, x + 0.5D, z + 0.5D);
            if (distance < best) { best = distance; width = candidate; nearestX = x; nearestZ = z; }
        }
        return width > 0 && riverNeighbours(nearestX, nearestZ, loaded.width, loaded.height, loaded.rivers) == 0
                ? Math.max(width, EarthShapeServerConfig.RIVER_MINIMUM_WIDTH_BLOCKS.get()) : width;
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
        try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/rivers.bmp")) {
            if (input == null) throw new IOException("missing /earthshape/hoi4/rivers.bmp");
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException("rivers.bmp is not readable");
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
                    int riverWidth = riverWidthForColor(red, green, blue);
                    // Land/sea ownership starts from the actual fill colours.  In
                    // particular #FFFFFF is always land; blue river ink must not decide
                    // whether a coastline pixel becomes land or water.
                    if (riverWidth == 0 && isFullMapLand(red, green, blue)) land.set(z * width + x);
                    if (riverWidth > 0) {
                        int index = z * width + x;
                        rivers.set(index);
                        riverWidths[index] = (byte) riverWidth;
                    }
                }
            }
            bridgeSmallRiverGaps(width, height, rivers, riverWidths);
            // Restore only river pixels surrounded by real land.  This keeps a normal
            // inland river continuous while preventing blue coastline strokes from
            // turning ocean or a land edge into a false channel.
            restoreOnlyInlandRiverPixels(width, height, land, rivers);
            EarthShape.LOGGER.info("[EarthShape] rivers.bmp land/ocean and river mask loaded: {}x{} in {} ms.",
                    width, height, (System.nanoTime() - started) / 1_000_000L);
            return new Data(width, height, land, rivers, riverWidths);
        } catch (IOException exception) {
            throw new IllegalStateException("EarthShape could not load Worldmap_river.png", exception);
        }
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /** Removes river pixels in a 64-source-pixel band around only the open ocean. */
    private static void removeCoastalRiverInk(int width, BitSet rivers, byte[] riverWidths, OceanProximity ocean) {
        for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
            int x = index % width;
            int z = index / width;
            if (ocean.isNearOpenOcean(x, z)) {
                rivers.clear(index);
                riverWidths[index] = 0;
            }
        }
    }

    /**
     * Coarse open-ocean component and its short shoreline band.  Flooding starts only at
     * the image border, which keeps closed lakes out of the coastal-river exclusion.
     */
    private static final class OceanProximity {
        private static final int SCALE = 4;
        // Three 4-pixel cells: enough to reject an anti-aliased shoreline stroke, while
        // retaining genuine rivers and estuaries shortly after they enter the continent.
        private static final int SHORE_BAND_CELLS = 3;
        private final int width;
        private final int height;
        private final byte[] distance;

        private OceanProximity(int width, int height, byte[] distance) {
            this.width = width;
            this.height = height;
            this.distance = distance;
        }

        static OceanProximity create(int sourceWidth, int sourceHeight, BitSet land) {
            int width = (sourceWidth + SCALE - 1) / SCALE;
            int height = (sourceHeight + SCALE - 1) / SCALE;
            int cells = width * height;
            BitSet water = new BitSet(cells);
            for (int z = 0; z < height; z++) for (int x = 0; x < width; x++) {
                int landCount = 0;
                for (int dz = 0; dz < SCALE; dz++) for (int dx = 0; dx < SCALE; dx++) {
                    int px = x * SCALE + dx, pz = z * SCALE + dz;
                    if (px < sourceWidth && pz < sourceHeight && land.get(pz * sourceWidth + px)) landCount++;
                }
                if (landCount <= 8) water.set(z * width + x);
            }
            BitSet openOcean = new BitSet(cells);
            int[] queue = new int[cells];
            int head = 0, tail = 0;
            for (int z = 0; z < height; z++) for (int x = 0; x < width; x++) {
                if (x != 0 && z != 0 && x != width - 1 && z != height - 1) continue;
                int index = z * width + x;
                if (water.get(index) && !openOcean.get(index)) {
                    openOcean.set(index);
                    queue[tail++] = index;
                }
            }
            while (head < tail) {
                int index = queue[head++];
                int x = index % width, z = index / width;
                if (x > 0) tail = floodWater(index - 1, water, openOcean, queue, tail);
                if (x + 1 < width) tail = floodWater(index + 1, water, openOcean, queue, tail);
                if (z > 0) tail = floodWater(index - width, water, openOcean, queue, tail);
                if (z + 1 < height) tail = floodWater(index + width, water, openOcean, queue, tail);
            }
            byte[] distance = new byte[cells];
            Arrays.fill(distance, (byte) 127);
            head = 0;
            tail = 0;
            for (int index = openOcean.nextSetBit(0); index >= 0; index = openOcean.nextSetBit(index + 1)) {
                distance[index] = 0;
                queue[tail++] = index;
            }
            while (head < tail) {
                int index = queue[head++];
                int current = distance[index] & 255;
                if (current >= SHORE_BAND_CELLS) continue;
                int x = index % width, z = index / width;
                if (x > 0) tail = floodDistance(index - 1, current, distance, queue, tail);
                if (x + 1 < width) tail = floodDistance(index + 1, current, distance, queue, tail);
                if (z > 0) tail = floodDistance(index - width, current, distance, queue, tail);
                if (z + 1 < height) tail = floodDistance(index + width, current, distance, queue, tail);
            }
            return new OceanProximity(width, height, distance);
        }

        boolean isNearOpenOcean(int sourceX, int sourceZ) {
            int x = Math.max(0, Math.min(width - 1, sourceX / SCALE));
            int z = Math.max(0, Math.min(height - 1, sourceZ / SCALE));
            return (distance[z * width + x] & 255) <= SHORE_BAND_CELLS;
        }

        private static int floodWater(int index, BitSet water, BitSet openOcean, int[] queue, int tail) {
            if (water.get(index) && !openOcean.get(index)) {
                openOcean.set(index);
                queue[tail++] = index;
            }
            return tail;
        }

        private static int floodDistance(int index, int current, byte[] distance, int[] queue, int tail) {
            if ((distance[index] & 255) == 127) {
                distance[index] = (byte) (current + 1);
                queue[tail++] = index;
            }
            return tail;
        }
    }

    /**
     * Blue source pixels inherit land only when embedded in a clear land neighbourhood.
     * The 5x5 majority rejects a coast stroke with ocean on one side, while preserving
     * ordinary one-pixel rivers that have land on both banks.
     */
    private static void restoreOnlyInlandRiverPixels(int width, int height, BitSet land, BitSet rivers) {
        for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
            int x = index % width, z = index / width;
            int support = 0;
            for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                int sx = x + dx, sz = z + dz;
                if (sx >= 0 && sz >= 0 && sx < width && sz < height && land.get(sz * width + sx)) support++;
            }
            if (support >= 16) land.set(index);
        }
    }

    /**
     * Connects short raster gaps where both sides have the same local tangent.  The former
     * cardinal/45-degree-only repair missed gently curved lines, which left visible breaks
     * even though the source image contained one continuous river.
     */
    private static void bridgeSmallRiverGaps(int width, int height, BitSet rivers, byte[] riverWidths) {
        int maximumGap = EarthShapeServerConfig.RIVER_GAP_BRIDGE_PIXELS.get();
        if (maximumGap <= 0) return;
        // Search the unmodified raster only.  Newly painted bridge pixels must not become
        // extra endpoints during this pass and accidentally extend a river further.
        BitSet sourceRivers = (BitSet) rivers.clone();
        for (int index = sourceRivers.nextSetBit(0); index >= 0; index = sourceRivers.nextSetBit(index + 1)) {
            int x = index % width;
            int z = index / width;
            if (riverNeighbours(x, z, width, height, sourceRivers) > 2) continue;
            for (int dz = -maximumGap - 1; dz <= maximumGap + 1; dz++) {
                for (int dx = -maximumGap - 1; dx <= maximumGap + 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    double length = Math.sqrt(dx * dx + dz * dz);
                    if (length < 2.0D || length > maximumGap + 1.0D) continue;
                    int targetX = x + dx;
                    int targetZ = z + dz;
                    if (!inside(targetX, targetZ, width, height)) continue;
                    int target = targetZ * width + targetX;
                    if (!sourceRivers.get(target) || riverNeighbours(targetX, targetZ, width, height, sourceRivers) > 2) continue;
                    if (!continuesInDirection(x, z, -dx, -dz, width, height, sourceRivers)
                            || !continuesInDirection(targetX, targetZ, dx, dz, width, height, sourceRivers)
                            || !clearLine(x, z, targetX, targetZ, width, sourceRivers)) continue;
                    byte bridgeWidth = (byte) Math.min(riverWidths[index] & 255, riverWidths[target] & 255);
                    paintLine(x, z, targetX, targetZ, width, rivers, riverWidths, bridgeWidth);
                }
            }
        }
    }

    private static boolean continuesInDirection(int x, int z, int directionX, int directionZ,
            int width, int height, BitSet rivers) {
        double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
        for (int neighbourZ = -1; neighbourZ <= 1; neighbourZ++) for (int neighbourX = -1; neighbourX <= 1; neighbourX++) {
            if (neighbourX == 0 && neighbourZ == 0) continue;
            int nextX = x + neighbourX, nextZ = z + neighbourZ;
            if (!inside(nextX, nextZ, width, height) || !rivers.get(nextZ * width + nextX)) continue;
            // cos(45°): permits curved strokes but not a perpendicular nearby tributary.
            if ((neighbourX * directionX + neighbourZ * directionZ) / (Math.sqrt(neighbourX * neighbourX + neighbourZ * neighbourZ) * directionLength) >= 0.707D) return true;
        }
        return false;
    }

    private static boolean clearLine(int startX, int startZ, int endX, int endZ, int width, BitSet rivers) {
        int dx = Math.abs(endX - startX), sx = startX < endX ? 1 : -1;
        int dz = -Math.abs(endZ - startZ), sz = startZ < endZ ? 1 : -1;
        int error = dx + dz, x = startX, z = startZ;
        while (true) {
            if (x == endX && z == endZ) return true;
            int twiceError = 2 * error;
            if (twiceError >= dz) { error += dz; x += sx; }
            if (twiceError <= dx) { error += dx; z += sz; }
            if (x == endX && z == endZ) return true;
            if (rivers.get(z * width + x)) return false;
        }
    }

    private static void paintLine(int startX, int startZ, int endX, int endZ, int width,
            BitSet rivers, byte[] riverWidths, byte bridgeWidth) {
        int dx = Math.abs(endX - startX), sx = startX < endX ? 1 : -1;
        int dz = -Math.abs(endZ - startZ), sz = startZ < endZ ? 1 : -1;
        int error = dx + dz, x = startX, z = startZ;
        while (!(x == endX && z == endZ)) {
            int twiceError = 2 * error;
            if (twiceError >= dz) { error += dz; x += sx; }
            if (twiceError <= dx) { error += dx; z += sz; }
            if (x == endX && z == endZ) return;
            int index = z * width + x;
            rivers.set(index);
            riverWidths[index] = bridgeWidth;
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

    private static boolean isFullMapLand(int red, int green, int blue) {
        // The two source fill colours are authoritative.  Edge antialiasing is assigned
        // to its nearest fill colour, never inferred from blue river ink.
        if (red == 255 && green == 255 && blue == 255) return true;
        if (red == 122 && green == 122 && blue == 122) return false;
        int toLand = (255 - red) * (255 - red) + (255 - green) * (255 - green) + (255 - blue) * (255 - blue);
        int toOcean = (122 - red) * (122 - red) + (122 - green) * (122 - green) + (122 - blue) * (122 - blue);
        return toLand <= toOcean;
    }

    /** HOI4 blue river hierarchy: darker lines represent wider rivers. */
    private static int riverWidthForColor(int red, int green, int blue) {
        int configuredWidth = configuredRiverWidth(red, green, blue);
        // The replacement PNG contains antialiased blends such as #333383 (a #000064
        // line over white).  Match the colour against every source palette entry blended
        // over the white land or grey sea background, preserving its own width class.
        if (configuredWidth == 0) configuredWidth = antialiasedRiverWidth(red, green, blue);
        return configuredWidth == 0 ? 0 : Math.max(1, (int) Math.round(configuredWidth * EarthShapeServerConfig.RIVER_WIDTH_SCALE.get()));
    }

    private static int antialiasedRiverWidth(int red, int green, int blue) {
        int[][] palette = {{0, 0, 100}, {0, 0, 150}, {0, 0, 200}, {0, 0, 255}, {0, 100, 255}, {0, 200, 255}, {0, 225, 255}};
        int[][] backgrounds = {{255, 255, 255}, {128, 128, 128}};
        double nearestDistance = Double.POSITIVE_INFINITY;
        double nearestAlpha = 0.0D;
        int[] nearest = null;
        for (int[] colour : palette) for (int[] background : backgrounds) {
            double dr = colour[0] - background[0], dg = colour[1] - background[1], db = colour[2] - background[2];
            double length = dr * dr + dg * dg + db * db;
            double alpha = Math.max(0.0D, Math.min(1.0D, ((red - background[0]) * dr + (green - background[1]) * dg + (blue - background[2]) * db) / length));
            double er = red - (background[0] + alpha * dr), eg = green - (background[1] + alpha * dg), eb = blue - (background[2] + alpha * db);
            double distance = er * er + eg * eg + eb * eb;
            if (distance < nearestDistance) { nearestDistance = distance; nearestAlpha = alpha; nearest = colour; }
        }
        // Do not accept alpha=0: pure white/grey background is mathematically on every
        // blend line, and treating it as a river turns every continent into a water channel.
        return nearestAlpha >= 0.10D && nearestDistance <= 9.0D
                ? configuredRiverWidth(nearest[0], nearest[1], nearest[2]) : 0;
    }

    private static int configuredRiverWidth(int red, int green, int blue) {
        if (red != 0) return 0;
        return switch ((green << 8) | blue) {
            case 0x0064 -> EarthShapeServerConfig.RIVER_WIDTH_000064.get();
            case 0x0096 -> EarthShapeServerConfig.RIVER_WIDTH_000096.get();
            case 0x00C8 -> EarthShapeServerConfig.RIVER_WIDTH_0000C8.get();
            case 0x00FF -> EarthShapeServerConfig.RIVER_WIDTH_0000FF.get();
            case 0x64FF -> EarthShapeServerConfig.RIVER_WIDTH_0064FF.get();
            case 0xC8FF -> EarthShapeServerConfig.RIVER_WIDTH_00C8FF.get();
            case 0xE1FF -> EarthShapeServerConfig.RIVER_WIDTH_00E1FF.get();
            default -> 0;
        };
    }

    private record Data(int width, int height, BitSet land, BitSet rivers, byte[] riverWidths) {
        double land(int x, int z) { return land.get(z * width + x) ? 1.0D : 0.0D; }
        double reliefLand(int x, int z) { return (land.get(z * width + x) || rivers.get(z * width + x)) ? 1.0D : 0.0D; }
        boolean river(int x, int z) { return x >= 0 && z >= 0 && x < width && z < height && rivers.get(z * width + x); }
        int riverWidth(int x, int z) { return x >= 0 && z >= 0 && x < width && z < height ? riverWidths[z * width + x] & 255 : 0; }
    }
}
