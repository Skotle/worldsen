/*
 * Decompiled with CFR 0.152.
 */
package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.BitSet;
import javax.imageio.ImageIO;

public final class RiversMask {
    public static final RiversMask INSTANCE = new RiversMask();
    public static final int DEFAULT_BLOCKS_PER_PIXEL = 20;
    private static final int RIVER_SEARCH_RADIUS = 4;
    private static final double RIVER_CORNER_TRIM = 0.32;
    private volatile Data data;
    private final ThreadLocal<RiverWidthCache> riverWidthCache = ThreadLocal.withInitial(RiverWidthCache::new);

    private RiversMask() {
    }

    public double sampleLand(int blockX, int blockZ) {
        return this.sampleLand(this.data(), blockX, blockZ);
    }

    public double sampleLayerLand(int blockX, int blockZ) {
        Data loaded = this.data();
        // Density functions are evaluated per block, not per map pixel. Sampling the
        // floored source cell here made every coast a blocksPerPixel-sized square.
        // Reuse the bilinear land field so the 0..1 coastline changes continuously.
        return this.sampleLand(loaded, blockX, blockZ);
    }

    public double sampleCoastLand(int blockX, int blockZ) {
        Data loaded = this.data();
        int radiusBlocks = Math.max(160, (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get() / 2);
        int sampleStep = Math.max(1, radiusBlocks / 2);
        int sampleRadius = 2;
        double total = 0.0;
        double weight = 0.0;
        for (int dz = -sampleRadius; dz <= sampleRadius; ++dz) {
            for (int dx = -sampleRadius; dx <= sampleRadius; ++dx) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > (double)sampleRadius) continue;
                double sampleWeight = (double)sampleRadius + 1.0 - distance;
                total += this.sampleLand(loaded, blockX + dx * sampleStep, blockZ + dz * sampleStep) * sampleWeight;
                weight += sampleWeight;
            }
        }
        return total / weight;
    }

    public double sampleCoastInlandness(int blockX, int blockZ, int fadeBlocks) {
        Data loaded = this.data();
        int x = (int)Math.floor((double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5);
        int z = (int)Math.floor((double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5);
        if (x < 0 || z < 0 || x >= loaded.width || z >= loaded.height || !loaded.land.get(z * loaded.width + x)) {
            return 0.0;
        }
        double t = Math.min(1.0, (double)(loaded.coastDistance[z * loaded.width + x] & 0xFF) * (double)this.blocksPerPixel() / (double)Math.max(1, fadeBlocks));
        return t * t * (3.0 - 2.0 * t);
    }

    public double sampleWaterShoreProximity(int blockX, int blockZ, int fadeBlocks) {
        Data loaded = this.data();
        int x = (int)Math.floor((double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5);
        int z = (int)Math.floor((double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5);
        if (x < 0 || z < 0 || x >= loaded.width || z >= loaded.height || loaded.land.get(z * loaded.width + x)) {
            return 0.0;
        }
        double distance = (double)(loaded.waterCoastDistance[z * loaded.width + x] & 0xFF) * (double)this.blocksPerPixel();
        double t = 1.0 - Math.min(1.0, distance / (double)Math.max(1, fadeBlocks));
        return t * t * (3.0 - 2.0 * t);
    }

    public double sampleRiverReliefFactor(int blockX, int blockZ) {
        if (!((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()).booleanValue()) {
            return 1.0;
        }
        if (!this.hasInlandRiverInfluence(blockX, blockZ)) {
            return 1.0;
        }
        double distance = this.riverCentrelineDistance(blockX, blockZ) * (double)this.blocksPerPixel();
        double riverRadius = (double)this.effectiveRiverWidthBlocks(blockX, blockZ) * 0.5;
        int fadeBlocks = Math.max(24, Math.min(56, (Integer)EarthShapeServerConfig.RIVER_HEIGHT_FADE_BLOCKS.get()));
        double rawFactor = Math.max(0.0, 1.0 - distance / Math.max(1.0, riverRadius + (double)fadeBlocks));
        double sharpRiverFactor = rawFactor * rawFactor * rawFactor;
        return 1.0 - sharpRiverFactor;
    }

    private double sampleLand(Data loaded, int blockX, int blockZ) {
        int blocksPerPixel = this.blocksPerPixel();
        double imageX = (double)blockX / (double)blocksPerPixel + (double)loaded.width * 0.5;
        double imageZ = (double)blockZ / (double)blocksPerPixel + (double)loaded.height * 0.5;
        if (!(imageX < 0.0 || imageZ < 0.0 || imageX >= (double)(loaded.width - 1) || imageZ >= (double)(loaded.height - 1))) {
            int x = (int)Math.floor(imageX);
            int z = (int)Math.floor(imageZ);
            double tx = imageX - (double)x;
            double tz = imageZ - (double)z;
            double a = RiversMask.lerp(loaded.land(x, z), loaded.land(x + 1, z), tx);
            double b = RiversMask.lerp(loaded.land(x, z + 1), loaded.land(x + 1, z + 1), tx);
            return RiversMask.lerp(a, b, tz);
        }
        return 0.0;
    }

    private double sampleReliefLand(Data loaded, int blockX, int blockZ) {
        int blocksPerPixel = this.blocksPerPixel();
        double imageX = (double)blockX / (double)blocksPerPixel + (double)loaded.width * 0.5;
        double imageZ = (double)blockZ / (double)blocksPerPixel + (double)loaded.height * 0.5;
        if (!(imageX < 0.0 || imageZ < 0.0 || imageX >= (double)(loaded.width - 1) || imageZ >= (double)(loaded.height - 1))) {
            int x = (int)Math.floor(imageX);
            int z = (int)Math.floor(imageZ);
            double tx = imageX - (double)x;
            double tz = imageZ - (double)z;
            double a = RiversMask.lerp(loaded.reliefLand(x, z), loaded.reliefLand(x + 1, z), tx);
            double b = RiversMask.lerp(loaded.reliefLand(x, z + 1), loaded.reliefLand(x + 1, z + 1), tx);
            return RiversMask.lerp(a, b, tz);
        }
        return 0.0;
    }

    public int blocksPerPixel() {
        return (Integer)EarthShapeServerConfig.BLOCKS_PER_PIXEL.get();
    }

    public int width() {
        return this.data().width;
    }

    public int height() {
        return this.data().height;
    }

    public double legacyImageX(int blockX, int legacyWidth) {
        Data loaded = this.data();
        return (double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5 - (double)(loaded.width - legacyWidth) * 0.5;
    }

    public double legacyImageZ(int blockZ, int legacyHeight) {
        Data loaded = this.data();
        return (double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5 - (double)(loaded.height - legacyHeight) * 0.5;
    }

    public boolean isInsideLegacyLayer(int blockX, int blockZ, int legacyWidth, int legacyHeight) {
        double x = this.legacyImageX(blockX, legacyWidth);
        double z = this.legacyImageZ(blockZ, legacyHeight);
        return x >= 0.0 && z >= 0.0 && x < (double)legacyWidth && z < (double)legacyHeight;
    }

    public boolean isRiverCentreline(int blockX, int blockZ) {
        int widthBlocks = this.effectiveRiverWidthBlocks(blockX, blockZ);
        if (widthBlocks == 0) {
            return false;
        }
        double halfWidthPixels = (double)Math.max(4, widthBlocks) / (2.0 * (double)this.blocksPerPixel());
        return this.riverCentrelineDistance(blockX, blockZ) <= halfWidthPixels;
    }

    public boolean isInlandRiver(int blockX, int blockZ) {
        return this.isRiverCentreline(blockX, blockZ) && this.hasInlandRiverInfluence(blockX, blockZ);
    }

    public boolean isRiverMouth(int blockX, int blockZ) {
        Data loaded = this.data();
        int x = (int)Math.floor((double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5);
        int z = (int)Math.floor((double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5);
        return x >= 0 && z >= 0 && x < loaded.width && z < loaded.height && loaded.riverMouths.get(z * loaded.width + x) && this.isRiverCentreline(blockX, blockZ);
    }

    public boolean isInlandRiverBank(int blockX, int blockZ) {
        if (!this.hasInlandRiverInfluence(blockX, blockZ)) {
            return false;
        }
        int width = this.effectiveRiverWidthBlocks(blockX, blockZ);
        if (width == 0) {
            return false;
        }
        double distanceBlocks = this.riverCentrelineDistance(blockX, blockZ) * (double)this.blocksPerPixel();
        return distanceBlocks <= Math.max(12.0, (double)width * 0.5 + 12.0);
    }

    public boolean isNearInlandRiver(int blockX, int blockZ, int exclusionBlocks) {
        if (!this.hasInlandRiverInfluence(blockX, blockZ)) {
            return false;
        }
        int width = this.effectiveRiverWidthBlocks(blockX, blockZ);
        if (width == 0) {
            return false;
        }
        double distanceBlocks = this.riverCentrelineDistance(blockX, blockZ) * (double)this.blocksPerPixel();
        return distanceBlocks <= Math.max((double)exclusionBlocks, (double)width * 0.5);
    }

    public boolean hasInlandRiverInfluence(int blockX, int blockZ) {
        Data loaded = this.data();
        int x = (int)Math.floor((double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5);
        int z = (int)Math.floor((double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5);
        return x >= 0 && z >= 0 && x < loaded.width && z < loaded.height && loaded.land.get(z * loaded.width + x) && loaded.riverInfluence.get(z * loaded.width + x);
    }

    public int riverWidthBlocks(int blockX, int blockZ) {
        Data loaded = this.data();
        RiverWidthCache cache = this.riverWidthCache.get();
        if (cache.data == loaded && cache.blockX == blockX && cache.blockZ == blockZ) {
            return cache.width;
        }
        double imageX = (double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5;
        double imageZ = (double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5;
        int width = 0;
        if (!(imageX < 1.0 || imageZ < 1.0 || imageX >= (double)loaded.width - 1.0 || imageZ >= (double)loaded.height - 1.0)) {
            int centreX = (int)Math.floor(imageX);
            int centreZ = (int)Math.floor(imageZ);
            if (!loaded.riverInfluence.get(centreZ * loaded.width + centreX)) {
                cache.data = loaded;
                cache.blockX = blockX;
                cache.blockZ = blockZ;
                cache.width = 0;
                return 0;
            }
            double best = Double.POSITIVE_INFINITY;
            for (int z = centreZ - 4; z <= centreZ + 4; ++z) {
                for (int x = centreX - 4; x <= centreX + 4; ++x) {
                    double distance;
                    int candidate = loaded.riverWidth(x, z);
                    if (candidate == 0 || !((distance = RiversMask.distanceSquared(imageX, imageZ, (double)x + 0.5, (double)z + 0.5, (double)x + 0.5, (double)z + 0.5)) < best)) continue;
                    best = distance;
                    width = candidate;
                }
            }
        }
        cache.data = loaded;
        cache.blockX = blockX;
        cache.blockZ = blockZ;
        cache.width = width;
        return width;
    }

    public int effectiveRiverWidthBlocks(int blockX, int blockZ) {
        int width = this.riverWidthBlocks(blockX, blockZ);
        return width > 0 ? Math.max(width, Math.max(12, (Integer)EarthShapeServerConfig.RIVER_MINIMUM_WIDTH_BLOCKS.get())) : 0;
    }

    public double riverCentrelineDistance(int blockX, int blockZ) {
        Data loaded = this.data();
        double imageX = (double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5;
        double imageZ = (double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5;
        if (!(imageX < 1.0 || imageZ < 1.0 || imageX >= (double)loaded.width - 1.0 || imageZ >= (double)loaded.height - 1.0)) {
            int centreX = (int)Math.floor(imageX);
            int centreZ = (int)Math.floor(imageZ);
            if (!loaded.riverInfluence.get(centreZ * loaded.width + centreX)) {
                return Double.POSITIVE_INFINITY;
            }
            double best = Double.POSITIVE_INFINITY;
            for (int z = centreZ - 4; z <= centreZ + 4; ++z) {
                for (int x = centreX - 4; x <= centreX + 4; ++x) {
                    if (!loaded.river(x, z)) continue;
                    int cornerMask = loaded.riverCornerMask(x, z);
                    if (cornerMask == 0) {
                        double pathX = RiversMask.riverPathX(loaded, x, z);
                        double pathZ = RiversMask.riverPathZ(loaded, x, z);
                        best = Math.min(best, Math.sqrt(RiversMask.distanceSquared(imageX, imageZ, pathX, pathZ, pathX, pathZ)));
                    } else {
                        best = Math.min(best, Math.sqrt(RiversMask.roundedCornerDistanceSquared(imageX, imageZ, x, z, cornerMask)));
                    }
                    for (int dz = -1; dz <= 1; ++dz) {
                        for (int dx = -1; dx <= 1; ++dx) {
                            if (dx <= 0 && (dx != 0 || dz <= 0) || !loaded.river(x + dx, z + dz)) continue;
                            int neighbourCornerMask = loaded.riverCornerMask(x + dx, z + dz);
                            double startX = RiversMask.riverPathX(loaded, x, z) + ((cornerMask & RiversMask.neighbourBit(dx, dz)) != 0 ? (double)dx * 0.32 : 0.0);
                            double startZ = RiversMask.riverPathZ(loaded, x, z) + ((cornerMask & RiversMask.neighbourBit(dx, dz)) != 0 ? (double)dz * 0.32 : 0.0);
                            double endX = RiversMask.riverPathX(loaded, x + dx, z + dz) + ((neighbourCornerMask & RiversMask.neighbourBit(-dx, -dz)) != 0 ? (double)(-dx) * 0.32 : 0.0);
                            double endZ = RiversMask.riverPathZ(loaded, x + dx, z + dz) + ((neighbourCornerMask & RiversMask.neighbourBit(-dx, -dz)) != 0 ? (double)(-dz) * 0.32 : 0.0);
                            best = Math.min(best, Math.sqrt(RiversMask.distanceSquared(imageX, imageZ, startX, startZ, endX, endZ)));
                        }
                    }
                }
            }
            return best;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static double riverPathX(Data data, int x, int z) {
        boolean northSouth = data.river(x, z - 1) || data.river(x, z + 1);
        boolean eastWest = data.river(x - 1, z) || data.river(x + 1, z);
        return (double)x + 0.5 + (northSouth && !eastWest ? 0.22 * RiversMask.axialNoise(z, x, 20903L) : 0.0);
    }

    private static double riverPathZ(Data data, int x, int z) {
        boolean northSouth = data.river(x, z - 1) || data.river(x, z + 1);
        boolean eastWest = data.river(x - 1, z) || data.river(x + 1, z);
        return (double)z + 0.5 + (eastWest && !northSouth ? 0.22 * RiversMask.axialNoise(x, z, 42773L) : 0.0);
    }

    private static double axialNoise(int coordinate, int line, long salt) {
        int cell = Math.floorDiv(coordinate, 6);
        double t = (double)Math.floorMod(coordinate, 6) / 6.0;
        t = t * t * (3.0 - 2.0 * t);
        return RiversMask.lerp(RiversMask.axialValue(cell, line, salt), RiversMask.axialValue(cell + 1, line, salt), t);
    }

    private static double axialValue(int cell, int line, long salt) {
        long value = salt ^ (long)cell * 341873128712L ^ (long)line * 132897987541L;
        value ^= value >>> 33;
        value *= -49064778989728563L;
        value ^= value >>> 33;
        return (double)(value >>> 11 & 0x1FFFFFL) / 1048575.5 - 1.0;
    }

    private static double distanceSquared(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double length = dx * dx + dz * dz;
        if (length == 0.0) {
            double ox = px - ax;
            double oz = pz - az;
            return ox * ox + oz * oz;
        }
        double t = Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (pz - az) * dz) / length));
        double ox = px - (ax + t * dx);
        double oz = pz - (az + t * dz);
        return ox * ox + oz * oz;
    }

    private static double roundedCornerDistanceSquared(double px, double pz, int x, int z, int cornerMask) {
        int first = Integer.numberOfTrailingZeros(cornerMask & 0xFF);
        int second = Integer.numberOfTrailingZeros(cornerMask & 0xFF & ~(1 << first));
        int firstX = RiversMask.neighbourX(first);
        int firstZ = RiversMask.neighbourZ(first);
        int secondX = RiversMask.neighbourX(second);
        int secondZ = RiversMask.neighbourZ(second);
        double startX = (double)x + 0.5 + (double)firstX * 0.32;
        double startZ = (double)z + 0.5 + (double)firstZ * 0.32;
        double endX = (double)x + 0.5 + (double)secondX * 0.32;
        double endZ = (double)z + 0.5 + (double)secondZ * 0.32;
        double controlX = (double)x + 0.5;
        double controlZ = (double)z + 0.5;
        double best = Double.POSITIVE_INFINITY;
        double previousX = startX;
        double previousZ = startZ;
        for (int step = 1; step <= 9; ++step) {
            double t = (double)step / 9.0;
            double inverse = 1.0 - t;
            double nextX = inverse * inverse * startX + 2.0 * inverse * t * controlX + t * t * endX;
            double nextZ = inverse * inverse * startZ + 2.0 * inverse * t * controlZ + t * t * endZ;
            best = Math.min(best, RiversMask.distanceSquared(px, pz, previousX, previousZ, nextX, nextZ));
            previousX = nextX;
            previousZ = nextZ;
        }
        return best;
    }

    private static int neighbourBit(int dx, int dz) {
        int index = (dz + 1) * 3 + dx + 1;
        return 1 << (index > 4 ? index - 1 : index);
    }

    private static int neighbourX(int bit) {
        int index = bit >= 4 ? bit + 1 : bit;
        return index % 3 - 1;
    }

    private static int neighbourZ(int bit) {
        int index = bit >= 4 ? bit + 1 : bit;
        return index / 3 - 1;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Data data() {
        Data current = this.data;
        if (current != null) {
            return current;
        }
        RiversMask riversMask = this;
        synchronized (riversMask) {
            if (this.data == null) {
                this.data = RiversMask.load();
            }
            return this.data;
        }
    }

    private static Data load() {
        long started = System.nanoTime();
        try {
            Data var21x;
            try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/worldmap_river.png");){
                if (input == null) {
                    throw new IOException("missing /earthshape/hoi4/worldmap_river.png");
                }
                BufferedImage image = ImageIO.read(input);
                if (image == null) {
                    throw new IOException("worldmap_river.png is not readable");
                }
                int width = image.getWidth();
                int height = image.getHeight();
                BitSet land = new BitSet(width * height);
                BitSet rivers = new BitSet(width * height);
                byte[] riverWidths = new byte[width * height];
                int[] row = new int[width];
                for (int z = 0; z < height; ++z) {
                    image.getRGB(0, z, width, 1, row, 0, width);
                    for (int x = 0; x < width; ++x) {
                        int rgb = row[x];
                        int red = rgb >>> 16 & 0xFF;
                        int green = rgb >>> 8 & 0xFF;
                        int blue = rgb & 0xFF;
                        int riverWidth = RiversMask.riverWidthForColor(red, green, blue);
                        if (riverWidth == 0 && RiversMask.isFullMapLand(red, green, blue)) {
                            land.set(z * width + x);
                        }
                        if (riverWidth <= 0) continue;
                        int index = z * width + x;
                        rivers.set(index);
                        riverWidths[index] = (byte)riverWidth;
                    }
                }
                RiversMask.bridgeSmallRiverGaps(width, height, rivers, riverWidths);
                RiversMask.stabilizeRiverWidths(width, height, rivers, riverWidths);
                RiversMask.restoreOnlyInlandRiverPixels(width, height, land, rivers);
                BitSet riverMouths = RiversMask.createRiverMouths(width, height, land, rivers);
                byte[] riverCorners = RiversMask.createRiverCornerMasks(width, height, rivers);
                BitSet riverInfluence = RiversMask.createRiverInfluence(width, height, land, rivers);
                byte[] coastDistance = RiversMask.createCoastDistance(width, height, land, rivers);
                byte[] waterCoastDistance = RiversMask.createWaterCoastDistance(width, height, land);
                EarthShape.LOGGER.info("[EarthShape] worldmap_river.png land/ocean and river mask loaded: {}x{} in {} ms.", new Object[]{width, height, (System.nanoTime() - started) / 1000000L});
                var21x = new Data(width, height, land, rivers, riverWidths, riverCorners, riverMouths, riverInfluence, coastDistance, waterCoastDistance);
            }
            return var21x;
        }
        catch (IOException var21) {
            throw new IllegalStateException("EarthShape could not load worldmap_river.png", var21);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void removeCoastalRiverInk(int width, BitSet rivers, byte[] riverWidths, OceanProximity ocean) {
        int index = rivers.nextSetBit(0);
        while (index >= 0) {
            int x = index % width;
            int z = index / width;
            if (ocean.isNearOpenOcean(x, z)) {
                rivers.clear(index);
                riverWidths[index] = 0;
            }
            index = rivers.nextSetBit(index + 1);
        }
    }

    private static void restoreOnlyInlandRiverPixels(int width, int height, BitSet land, BitSet rivers) {
        int index = rivers.nextSetBit(0);
        while (index >= 0) {
            int x = index % width;
            int z = index / width;
            int support = 0;
            for (int dz = -2; dz <= 2; ++dz) {
                for (int dx = -2; dx <= 2; ++dx) {
                    int sx = x + dx;
                    int sz = z + dz;
                    if (sx < 0 || sz < 0 || sx >= width || sz >= height || !land.get(sz * width + sx)) continue;
                    ++support;
                }
            }
            // Broad blue source strokes contain fewer surrounding land cells than a
            // thin European tributary.  The old 16/25 requirement therefore removed
            // many valid desert channels before any density function could carve
            // them. Eight still rejects open-ocean ink while retaining inland rivers.
            if (support >= 8) {
                land.set(index);
            }
            index = rivers.nextSetBit(index + 1);
        }
    }

    private static BitSet createRiverInfluence(int width, int height, BitSet land, BitSet rivers) {
        BitSet influence = new BitSet(width * height);
        int index = rivers.nextSetBit(0);
        while (index >= 0) {
            if (land.get(index)) {
                int centreX = index % width;
                int centreZ = index / width;
                for (int dz = -4; dz <= 4; ++dz) {
                    for (int dx = -4; dx <= 4; ++dx) {
                        int x = centreX + dx;
                        int z = centreZ + dz;
                        if (!RiversMask.inside(x, z, width, height)) continue;
                        influence.set(z * width + x);
                    }
                }
            }
            index = rivers.nextSetBit(index + 1);
        }
        return influence;
    }

    private static BitSet createRiverMouths(int width, int height, BitSet land, BitSet rivers) {
        BitSet mouths = new BitSet(width * height);
        int index = rivers.nextSetBit(0);
        while (index >= 0) {
            if (land.get(index)) {
                int x = index % width;
                int z = index / width;
                block1: for (int dz = -2; dz <= 2; ++dz) {
                    for (int dx = -2; dx <= 2; ++dx) {
                        int sample;
                        int sampleX = x + dx;
                        int sampleZ = z + dz;
                        if (!RiversMask.inside(sampleX, sampleZ, width, height) || land.get(sample = sampleZ * width + sampleX) || rivers.get(sample)) continue;
                        mouths.set(index);
                        int var13 = 3;
                        continue block1;
                    }
                }
            }
            index = rivers.nextSetBit(index + 1);
        }
        return mouths;
    }

    private static byte[] createRiverCornerMasks(int width, int height, BitSet rivers) {
        byte[] corners = new byte[width * height];
        int index = rivers.nextSetBit(0);
        while (index >= 0) {
            boolean south;
            boolean north;
            boolean east;
            int x = index % width;
            int z = index / width;
            int mask = 0;
            int count = 0;
            boolean west = RiversMask.inside(x - 1, z, width, height) && rivers.get(z * width + x - 1);
            int cardinalCount = (west ? 1 : 0) + ((east = RiversMask.inside(x + 1, z, width, height) && rivers.get(z * width + x + 1)) ? 1 : 0) + ((north = RiversMask.inside(x, z - 1, width, height) && rivers.get((z - 1) * width + x)) ? 1 : 0) + ((south = RiversMask.inside(x, z + 1, width, height) && rivers.get((z + 1) * width + x)) ? 1 : 0);
            if (!(cardinalCount != 2 || west && east || north && south)) {
                int cardinalMask = 0;
                if (west) {
                    cardinalMask |= RiversMask.neighbourBit(-1, 0);
                }
                if (east) {
                    cardinalMask |= RiversMask.neighbourBit(1, 0);
                }
                if (north) {
                    cardinalMask |= RiversMask.neighbourBit(0, -1);
                }
                if (south) {
                    cardinalMask |= RiversMask.neighbourBit(0, 1);
                }
                corners[index] = (byte)cardinalMask;
            } else {
                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        if (dx == 0 && dz == 0 || !RiversMask.inside(x + dx, z + dz, width, height) || !rivers.get((z + dz) * width + x + dx)) continue;
                        mask |= RiversMask.neighbourBit(dx, dz);
                        ++count;
                    }
                }
                if (count == 2) {
                    int first = Integer.numberOfTrailingZeros(mask);
                    int second = Integer.numberOfTrailingZeros(mask & ~(1 << first));
                    int dot = RiversMask.neighbourX(first) * RiversMask.neighbourX(second) + RiversMask.neighbourZ(first) * RiversMask.neighbourZ(second);
                    if (dot > -1) {
                        corners[index] = (byte)mask;
                    }
                }
            }
            index = rivers.nextSetBit(index + 1);
        }
        return corners;
    }

    private static byte[] createCoastDistance(int width, int height, BitSet land, BitSet rivers) {
        int index;
        int z;
        byte[] distance = new byte[width * height];
        Arrays.fill(distance, (byte)127);
        for (z = 0; z < height; ++z) {
            for (int x = 0; x < width; ++x) {
                index = z * width + x;
                if (!land.get(index) && !rivers.get(index)) {
                    distance[index] = 0;
                    continue;
                }
                int best = distance[index] & 0xFF;
                if (x > 0) {
                    best = Math.min(best, (distance[index - 1] & 0xFF) + 1);
                }
                if (z > 0) {
                    best = Math.min(best, (distance[index - width] & 0xFF) + 1);
                    if (x > 0) {
                        best = Math.min(best, (distance[index - width - 1] & 0xFF) + 1);
                    }
                    if (x + 1 < width) {
                        best = Math.min(best, (distance[index - width + 1] & 0xFF) + 1);
                    }
                }
                distance[index] = (byte)Math.min(127, best);
            }
        }
        for (z = height - 1; z >= 0; --z) {
            for (int xx = width - 1; xx >= 0; --xx) {
                index = z * width + xx;
                int bestx = distance[index] & 0xFF;
                if (xx + 1 < width) {
                    bestx = Math.min(bestx, (distance[index + 1] & 0xFF) + 1);
                }
                if (z + 1 < height) {
                    bestx = Math.min(bestx, (distance[index + width] & 0xFF) + 1);
                    if (xx > 0) {
                        bestx = Math.min(bestx, (distance[index + width - 1] & 0xFF) + 1);
                    }
                    if (xx + 1 < width) {
                        bestx = Math.min(bestx, (distance[index + width + 1] & 0xFF) + 1);
                    }
                }
                distance[index] = (byte)Math.min(127, bestx);
            }
        }
        return distance;
    }

    private static byte[] createWaterCoastDistance(int width, int height, BitSet land) {
        int best;
        int index;
        int x;
        int z;
        byte[] distance = new byte[width * height];
        Arrays.fill(distance, (byte)127);
        for (z = 0; z < height; ++z) {
            for (x = 0; x < width; ++x) {
                index = z * width + x;
                if (land.get(index)) continue;
                boolean touchesLand = false;
                block2: for (int dz = -1; dz <= 1 && !touchesLand; ++dz) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (dx == 0 && dz == 0 || !RiversMask.inside(nx, nz, width, height) || !land.get(nz * width + nx)) continue;
                        touchesLand = true;
                        continue block2;
                    }
                }
                if (!touchesLand) continue;
                distance[index] = 0;
            }
        }
        for (z = 0; z < height; ++z) {
            for (x = 0; x < width; ++x) {
                index = z * width + x;
                if (land.get(index)) continue;
                best = distance[index] & 0xFF;
                if (x > 0 && !land.get(index - 1)) {
                    best = Math.min(best, (distance[index - 1] & 0xFF) + 1);
                }
                if (z > 0) {
                    if (!land.get(index - width)) {
                        best = Math.min(best, (distance[index - width] & 0xFF) + 1);
                    }
                    if (x > 0 && !land.get(index - width - 1)) {
                        best = Math.min(best, (distance[index - width - 1] & 0xFF) + 1);
                    }
                    if (x + 1 < width && !land.get(index - width + 1)) {
                        best = Math.min(best, (distance[index - width + 1] & 0xFF) + 1);
                    }
                }
                distance[index] = (byte)Math.min(127, best);
            }
        }
        for (z = height - 1; z >= 0; --z) {
            for (x = width - 1; x >= 0; --x) {
                index = z * width + x;
                if (land.get(index)) continue;
                best = distance[index] & 0xFF;
                if (x + 1 < width && !land.get(index + 1)) {
                    best = Math.min(best, (distance[index + 1] & 0xFF) + 1);
                }
                if (z + 1 < height) {
                    if (!land.get(index + width)) {
                        best = Math.min(best, (distance[index + width] & 0xFF) + 1);
                    }
                    if (x > 0 && !land.get(index + width - 1)) {
                        best = Math.min(best, (distance[index + width - 1] & 0xFF) + 1);
                    }
                    if (x + 1 < width && !land.get(index + width + 1)) {
                        best = Math.min(best, (distance[index + width + 1] & 0xFF) + 1);
                    }
                }
                distance[index] = (byte)Math.min(127, best);
            }
        }
        return distance;
    }

    private static void bridgeSmallRiverGaps(int width, int height, BitSet rivers, byte[] riverWidths) {
        int maximumGap = Math.max(3, Math.min(4, (Integer)EarthShapeServerConfig.RIVER_GAP_BRIDGE_PIXELS.get()));
        if (maximumGap > 0) {
            BitSet sourceRivers = (BitSet)rivers.clone();
            int index = sourceRivers.nextSetBit(0);
            while (index >= 0) {
                int x = index % width;
                int z = index / width;
                if (RiversMask.riverNeighbours(x, z, width, height, sourceRivers) <= 2) {
                    for (int dz = -maximumGap - 1; dz <= maximumGap + 1; ++dz) {
                        for (int dx = -maximumGap - 1; dx <= maximumGap + 1; ++dx) {
                            int target;
                            int targetZ;
                            int targetX;
                            double length;
                            if (dx == 0 && dz == 0 || (length = Math.sqrt(dx * dx + dz * dz)) < 2.0 || length > (double)maximumGap + 1.0 || !RiversMask.inside(targetX = x + dx, targetZ = z + dz, width, height) || !sourceRivers.get(target = targetZ * width + targetX) || RiversMask.riverNeighbours(targetX, targetZ, width, height, sourceRivers) > 2 || !RiversMask.continuesInDirection(x, z, -dx, -dz, width, height, sourceRivers) || !RiversMask.continuesInDirection(targetX, targetZ, dx, dz, width, height, sourceRivers) || !RiversMask.clearLine(x, z, targetX, targetZ, width, sourceRivers)) continue;
                            byte bridgeWidth = (byte)Math.min(riverWidths[index] & 0xFF, riverWidths[target] & 0xFF);
                            RiversMask.paintLine(x, z, targetX, targetZ, width, rivers, riverWidths, bridgeWidth);
                        }
                    }
                }
                index = sourceRivers.nextSetBit(index + 1);
            }
        }
    }

    private static void stabilizeRiverWidths(int width, int height, BitSet rivers, byte[] riverWidths) {
        byte[] source = (byte[])riverWidths.clone();
        int[] nearbyWidths = new int[9];
        int index = rivers.nextSetBit(0);
        while (index >= 0) {
            int x = index % width;
            int z = index / width;
            int count = 0;
            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    int sampleX = x + dx;
                    int sampleZ = z + dz;
                    if (!RiversMask.inside(sampleX, sampleZ, width, height) || !rivers.get(sampleZ * width + sampleX)) continue;
                    nearbyWidths[count++] = source[sampleZ * width + sampleX] & 0xFF;
                }
            }
            if (count >= 3) {
                Arrays.sort(nearbyWidths, 0, count);
                riverWidths[index] = (byte)nearbyWidths[count / 2];
            }
            index = rivers.nextSetBit(index + 1);
        }
    }

    private static boolean continuesInDirection(int x, int z, int directionX, int directionZ, int width, int height, BitSet rivers) {
        double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
        for (int neighbourZ = -1; neighbourZ <= 1; ++neighbourZ) {
            for (int neighbourX = -1; neighbourX <= 1; ++neighbourX) {
                int nextZ;
                int nextX;
                if (neighbourX == 0 && neighbourZ == 0 || !RiversMask.inside(nextX = x + neighbourX, nextZ = z + neighbourZ, width, height) || !rivers.get(nextZ * width + nextX) || !((double)(neighbourX * directionX + neighbourZ * directionZ) / (Math.sqrt(neighbourX * neighbourX + neighbourZ * neighbourZ) * directionLength) >= 0.707)) continue;
                return true;
            }
        }
        return false;
    }

    private static boolean clearLine(int startX, int startZ, int endX, int endZ, int width, BitSet rivers) {
        int dx = Math.abs(endX - startX);
        int sx = startX < endX ? 1 : -1;
        int dz = -Math.abs(endZ - startZ);
        int sz = startZ < endZ ? 1 : -1;
        int error = dx + dz;
        int x = startX;
        int z = startZ;
        while (x != endX || z != endZ) {
            int twiceError = 2 * error;
            if (twiceError >= dz) {
                error += dz;
                x += sx;
            }
            if (twiceError <= dx) {
                error += dx;
                z += sz;
            }
            if (x == endX && z == endZ) {
                return true;
            }
            if (!rivers.get(z * width + x)) continue;
            return false;
        }
        return true;
    }

    private static void paintLine(int startX, int startZ, int endX, int endZ, int width, BitSet rivers, byte[] riverWidths, byte bridgeWidth) {
        int dx = Math.abs(endX - startX);
        int sx = startX < endX ? 1 : -1;
        int dz = -Math.abs(endZ - startZ);
        int sz = startZ < endZ ? 1 : -1;
        int error = dx + dz;
        int x = startX;
        int z = startZ;
        while (x != endX || z != endZ) {
            int twiceError = 2 * error;
            if (twiceError >= dz) {
                error += dz;
                x += sx;
            }
            if (twiceError <= dx) {
                error += dx;
                z += sz;
            }
            if (x == endX && z == endZ) {
                return;
            }
            int index = z * width + x;
            rivers.set(index);
            riverWidths[index] = bridgeWidth;
        }
    }

    private static int riverNeighbours(int x, int z, int width, int height, BitSet rivers) {
        int count = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                int pz;
                int px;
                if (dx == 0 && dz == 0 || !RiversMask.inside(px = x + dx, pz = z + dz, width, height) || !rivers.get(pz * width + px)) continue;
                ++count;
            }
        }
        return count;
    }

    private static boolean inside(int x, int z, int width, int height) {
        return x >= 0 && z >= 0 && x < width && z < height;
    }

    private static boolean isFullMapOcean(int red, int green, int blue) {
        return Math.abs(red - green) <= 2 && Math.abs(green - blue) <= 2 && red >= 100 && red <= 170;
    }

    private static boolean isFullMapLand(int red, int green, int blue) {
        if (red == 255 && green == 255 && blue == 255) {
            return true;
        }
        if (red == 122 && green == 122 && blue == 122) {
            return false;
        }
        int toLand = (255 - red) * (255 - red) + (255 - green) * (255 - green) + (255 - blue) * (255 - blue);
        int toOcean = (122 - red) * (122 - red) + (122 - green) * (122 - green) + (122 - blue) * (122 - blue);
        return toLand <= toOcean;
    }

    private static int riverWidthForColor(int red, int green, int blue) {
        int configuredWidth = RiversMask.configuredRiverWidth(red, green, blue);
        if (configuredWidth == 0) {
            configuredWidth = RiversMask.antialiasedRiverWidth(red, green, blue);
        }
        return configuredWidth == 0 ? 0 : Math.max(1, (int)Math.round((double)configuredWidth * (Double)EarthShapeServerConfig.RIVER_WIDTH_SCALE.get()));
    }

    private static int antialiasedRiverWidth(int red, int green, int blue) {
        int[][] palette = new int[][]{{0, 0, 100}, {0, 0, 150}, {0, 0, 200}, {0, 0, 255}, {0, 100, 255}, {0, 200, 255}, {0, 225, 255}};
        int[][] backgrounds = new int[][]{{255, 255, 255}, {128, 128, 128}};
        double nearestDistance = Double.POSITIVE_INFINITY;
        double nearestAlpha = 0.0;
        int[] nearest = null;
        for (int[] colour : palette) {
            for (int[] background : backgrounds) {
                double eb;
                double eg;
                double dr = colour[0] - background[0];
                double dg = colour[1] - background[1];
                double db = colour[2] - background[2];
                double length = dr * dr + dg * dg + db * db;
                double alpha = Math.max(0.0, Math.min(1.0, ((double)(red - background[0]) * dr + (double)(green - background[1]) * dg + (double)(blue - background[2]) * db) / length));
                double er = (double)red - ((double)background[0] + alpha * dr);
                double distance = er * er + (eg = (double)green - ((double)background[1] + alpha * dg)) * eg + (eb = (double)blue - ((double)background[2] + alpha * db)) * eb;
                if (!(distance < nearestDistance)) continue;
                nearestDistance = distance;
                nearestAlpha = alpha;
                nearest = colour;
            }
        }
        return nearestAlpha >= 0.04 && nearestDistance <= 64.0 ? RiversMask.configuredRiverWidth((int)nearest[0], (int)nearest[1], (int)nearest[2]) : 0;
    }

    private static int configuredRiverWidth(int red, int green, int blue) {
        if (red != 0) {
            return 0;
        }
        return switch (green << 8 | blue) {
            case 100 -> (Integer)EarthShapeServerConfig.RIVER_WIDTH_000064.get();
            case 150 -> (Integer)EarthShapeServerConfig.RIVER_WIDTH_000096.get();
            case 200 -> (Integer)EarthShapeServerConfig.RIVER_WIDTH_0000C8.get();
            case 255 -> (Integer)EarthShapeServerConfig.RIVER_WIDTH_0000FF.get();
            case 25855 -> (Integer)EarthShapeServerConfig.RIVER_WIDTH_0064FF.get();
            case 51455 -> (Integer)EarthShapeServerConfig.RIVER_WIDTH_00C8FF.get();
            case 57855 -> (Integer)EarthShapeServerConfig.RIVER_WIDTH_00E1FF.get();
            default -> 0;
        };
    }

    private record Data(int width, int height, BitSet land, BitSet rivers, byte[] riverWidths, byte[] riverCorners, BitSet riverMouths, BitSet riverInfluence, byte[] coastDistance, byte[] waterCoastDistance) {
        double land(int x, int z) {
            return this.land.get(z * this.width + x) ? 1.0 : 0.0;
        }

        double reliefLand(int x, int z) {
            return !this.land.get(z * this.width + x) && !this.rivers.get(z * this.width + x) ? 0.0 : 1.0;
        }

        boolean river(int x, int z) {
            return x >= 0 && z >= 0 && x < this.width && z < this.height && this.rivers.get(z * this.width + x);
        }

        int riverWidth(int x, int z) {
            return x >= 0 && z >= 0 && x < this.width && z < this.height ? this.riverWidths[z * this.width + x] & 0xFF : 0;
        }

        int riverCornerMask(int x, int z) {
            return x >= 0 && z >= 0 && x < this.width && z < this.height ? this.riverCorners[z * this.width + x] & 0xFF : 0;
        }
    }

    private static final class RiverWidthCache {
        private Data data;
        private int blockX;
        private int blockZ;
        private int width;

        private RiverWidthCache() {
        }
    }

    private static final class OceanProximity {
        private static final int SCALE = 4;
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
            int width = (sourceWidth + 4 - 1) / 4;
            int height = (sourceHeight + 4 - 1) / 4;
            int cells = width * height;
            BitSet water = new BitSet(cells);
            for (int z = 0; z < height; ++z) {
                for (int x = 0; x < width; ++x) {
                    int landCount = 0;
                    for (int dz = 0; dz < 4; ++dz) {
                        for (int dx = 0; dx < 4; ++dx) {
                            int px = x * 4 + dx;
                            int pz = z * 4 + dz;
                            if (px >= sourceWidth || pz >= sourceHeight || !land.get(pz * sourceWidth + px)) continue;
                            ++landCount;
                        }
                    }
                    if (landCount > 8) continue;
                    water.set(z * width + x);
                }
            }
            BitSet openOcean = new BitSet(cells);
            int[] queue = new int[cells];
            int head = 0;
            int tail = 0;
            for (int z = 0; z < height; ++z) {
                for (int x = 0; x < width; ++x) {
                    int index;
                    if (x != 0 && z != 0 && x != width - 1 && z != height - 1 || !water.get(index = z * width + x) || openOcean.get(index)) continue;
                    openOcean.set(index);
                    queue[tail++] = index;
                }
            }
            while (head < tail) {
                int index = queue[head++];
                int xx = index % width;
                int z = index / width;
                if (xx > 0) {
                    tail = OceanProximity.floodWater(index - 1, water, openOcean, queue, tail);
                }
                if (xx + 1 < width) {
                    tail = OceanProximity.floodWater(index + 1, water, openOcean, queue, tail);
                }
                if (z > 0) {
                    tail = OceanProximity.floodWater(index - width, water, openOcean, queue, tail);
                }
                if (z + 1 >= height) continue;
                tail = OceanProximity.floodWater(index + width, water, openOcean, queue, tail);
            }
            byte[] distance = new byte[cells];
            Arrays.fill(distance, (byte)127);
            head = 0;
            tail = 0;
            int indexx = openOcean.nextSetBit(0);
            while (indexx >= 0) {
                distance[indexx] = 0;
                queue[tail++] = indexx;
                indexx = openOcean.nextSetBit(indexx + 1);
            }
            while (head < tail) {
                int current;
                if ((current = distance[indexx = queue[head++]] & 0xFF) >= 3) continue;
                int xxx = indexx % width;
                int zx = indexx / width;
                if (xxx > 0) {
                    tail = OceanProximity.floodDistance(indexx - 1, current, distance, queue, tail);
                }
                if (xxx + 1 < width) {
                    tail = OceanProximity.floodDistance(indexx + 1, current, distance, queue, tail);
                }
                if (zx > 0) {
                    tail = OceanProximity.floodDistance(indexx - width, current, distance, queue, tail);
                }
                if (zx + 1 >= height) continue;
                tail = OceanProximity.floodDistance(indexx + width, current, distance, queue, tail);
            }
            return new OceanProximity(width, height, distance);
        }

        boolean isNearOpenOcean(int sourceX, int sourceZ) {
            int x = Math.max(0, Math.min(this.width - 1, sourceX / 4));
            int z = Math.max(0, Math.min(this.height - 1, sourceZ / 4));
            return (this.distance[z * this.width + x] & 0xFF) <= 3;
        }

        private static int floodWater(int index, BitSet water, BitSet openOcean, int[] queue, int tail) {
            if (water.get(index) && !openOcean.get(index)) {
                openOcean.set(index);
                queue[tail++] = index;
            }
            return tail;
        }

        private static int floodDistance(int index, int current, byte[] distance, int[] queue, int tail) {
            if ((distance[index] & 0xFF) == 127) {
                distance[index] = (byte)(current + 1);
                queue[tail++] = index;
            }
            return tail;
        }
    }
}
