/*
 * Decompiled with CFR 0.152.
 */
package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;

public final class ClimateLayers {
    private static final int TREES_REGION_WIDTH = 5632;
    private static final int TREES_REGION_HEIGHT = 2048;
    private static final int MOUNTAIN_LOW = 10;
    private static final int MOUNTAIN_MID = 11;
    private static final int MOUNTAIN_HIGH = 12;
    private static final int MOUNTAIN_ULTRA = 13;
    public static final ClimateLayers INSTANCE = new ClimateLayers();
    private volatile Data temperature;
    private volatile Data trees;
    private volatile Data terrain;
    private volatile Data normal;

    private ClimateLayers() {
    }

    public double temperature(int x, int z) {
        Data layer = this.temperature();
        TemperatureSample sample = ClimateLayers.sampleFullTemperature(layer, x, z);
        double latitude = ClimateLayers.latitudeTemperature(z);
        double mapped = sample.value * 2.0 - 1.0;
        return latitude + (mapped - latitude) * sample.coverage;
    }

    public double oceanTemperature(int z) {
        return ClimateLayers.latitudeTemperature(z);
    }

    public boolean hasLegacyTemperature(int x, int z) {
        double mapX = (double)x / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.width() * 0.5;
        double mapZ = (double)z / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.height() * 0.5;
        return mapX >= 0.0 && mapZ >= 0.0 && mapX < (double)RiversMask.INSTANCE.width() && mapZ < (double)RiversMask.INSTANCE.height();
    }

    public double vegetation(int x, int z) {
        return ClimateLayers.sample(this.trees(), x, z) * 2.0 - 1.0;
    }

    public TreeCover treeCover(int x, int z) {
        Data layer = this.trees();
        double worldX = (double)x / (double)RiversMask.INSTANCE.blocksPerPixel() + 2816.0;
        double worldZ = (double)z / (double)RiversMask.INSTANCE.blocksPerPixel() + 1024.0;
        if (!(worldX < 0.0 || worldZ < 0.0 || worldX >= 5632.0 || worldZ >= 2048.0)) {
            int imageX = Math.min(layer.width - 1, (int)(worldX / 5632.0 * (double)layer.width));
            int imageZ = Math.min(layer.height - 1, (int)(worldZ / 2048.0 * (double)layer.height));
            int value = layer.values[imageZ * layer.width + imageX] & 0xFF;
            return value >= 235 ? TreeCover.TROPICAL : (value >= 150 ? TreeCover.TEMPERATE : TreeCover.NONE);
        }
        return TreeCover.NONE;
    }

    public TerrainKind terrainKind(int x, int z) {
        Data layer = this.terrain();
        if (!RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height)) {
            return TerrainKind.PLAINS;
        }
        int imageX = ClimateLayers.sourceX(layer, x);
        int imageZ = ClimateLayers.sourceZ(layer, z);
        int code = layer.values[imageZ * layer.width + imageX] & 0xFF;
        TerrainKind kind = ClimateLayers.isMountainElevationCode(code) ? TerrainKind.MOUNTAIN : TerrainKind.byCode(code);
        return kind != TerrainKind.CITY && kind != TerrainKind.SURROUNDING ? kind : ClimateLayers.surroundingLandKind(layer, imageX, imageZ);
    }

    public double mountainElevationWeight(int x, int z) {
        Data layer = this.terrain();
        if (!RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height)) {
            return 0.0;
        }
        int code = layer.values[ClimateLayers.sourceZ(layer, z) * layer.width + ClimateLayers.sourceX(layer, x)] & 0xFF;
        return switch (code) {
            case 10 -> 0.38;
            case 11 -> 0.58;
            case 12 -> 0.78;
            case 13 -> 1.0;
            default -> 0.0;
        };
    }

    public boolean isUltraMountain(int x, int z) {
        Data layer = this.terrain();
        return RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height) && (layer.values[ClimateLayers.sourceZ(layer, z) * layer.width + ClimateLayers.sourceX(layer, x)] & 0xFF) == 13;
    }

    public boolean isPolarTemperatureZone(int x, int z) {
        return this.temperature(x, z) <= -0.5;
    }

    public boolean isTerrainBoundary(int x, int z, int distanceBlocks) {
        TerrainKind centre = this.terrainKind(x, z);
        return centre != this.terrainKind(x - distanceBlocks, z) || centre != this.terrainKind(x + distanceBlocks, z) || centre != this.terrainKind(x, z - distanceBlocks) || centre != this.terrainKind(x, z + distanceBlocks);
    }

    private static boolean isMountainElevationCode(int code) {
        return code >= 10 && code <= 13;
    }

    public double desert(int x, int z) {
        return this.terrainKind(x, z) == TerrainKind.DESERT ? 1.0 : 0.0;
    }

    public double steepness(int x, int z) {
        return ClimateLayers.sample(this.normal(), x, z);
    }

    public boolean isMesaRegion(int blockX, int blockZ) {
        double u = ((double)blockX / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.width() * 0.5) / (double)RiversMask.INSTANCE.width();
        double v = ((double)blockZ / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.height() * 0.5) / (double)RiversMask.INSTANCE.height();
        boolean americas = u > 0.05 && u < 0.43 && v > 0.08 && v < 0.88;
        boolean oceania = u > 0.73 && u < 0.97 && v > 0.5 && v < 0.92;
        return americas || oceania;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Data temperature() {
        Data v = this.temperature;
        if (v != null) {
            return v;
        }
        ClimateLayers climateLayers = this;
        synchronized (climateLayers) {
            this.temperature = this.temperature == null ? ClimateLayers.load("earth_temperature.png", Kind.TEMPERATURE) : this.temperature;
            return this.temperature;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Data trees() {
        Data v = this.trees;
        if (v != null) {
            return v;
        }
        ClimateLayers climateLayers = this;
        synchronized (climateLayers) {
            this.trees = this.trees == null ? ClimateLayers.load("trees.bmp", Kind.VEGETATION) : this.trees;
            return this.trees;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Data terrain() {
        Data v = this.terrain;
        if (v != null) {
            return v;
        }
        ClimateLayers climateLayers = this;
        synchronized (climateLayers) {
            this.terrain = this.terrain == null ? ClimateLayers.load("terrain.bmp", Kind.TERRAIN_CLASS) : this.terrain;
            return this.terrain;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Data normal() {
        Data v = this.normal;
        if (v != null) {
            return v;
        }
        ClimateLayers climateLayers = this;
        synchronized (climateLayers) {
            this.normal = this.normal == null ? ClimateLayers.load("world_normal.bmp", Kind.NORMAL) : this.normal;
            return this.normal;
        }
    }

    private static double sample(Data layer, int blockX, int blockZ) {
        double imageX = RiversMask.INSTANCE.legacyImageX(blockX, layer.width);
        double imageZ = RiversMask.INSTANCE.legacyImageZ(blockZ, layer.height);
        if (!(imageX < 0.0 || imageZ < 0.0 || imageX >= (double)layer.width - 1.0 || imageZ >= (double)layer.height - 1.0)) {
            int x = (int)imageX;
            int z = (int)imageZ;
            double tx = imageX - (double)x;
            double tz = imageZ - (double)z;
            return ClimateLayers.lerp(ClimateLayers.lerp(layer.value(x, z), layer.value(x + 1, z), tx), ClimateLayers.lerp(layer.value(x, z + 1), layer.value(x + 1, z + 1), tx), tz);
        }
        return 0.5;
    }

    private static TemperatureSample sampleFullTemperature(Data layer, int blockX, int blockZ) {
        double worldX = (double)blockX / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.width() * 0.5;
        double worldZ = (double)blockZ / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.height() * 0.5;
        double imageX = Math.max(0.0, Math.min((double)layer.width - 1.001, worldX / (double)RiversMask.INSTANCE.width() * (double)layer.width));
        double imageZ = Math.max(0.0, Math.min((double)layer.height - 1.001, worldZ / (double)RiversMask.INSTANCE.height() * (double)layer.height));
        int x = (int)imageX;
        int z = (int)imageZ;
        double tx = imageX - (double)x;
        double tz = imageZ - (double)z;
        double value = ClimateLayers.lerp(ClimateLayers.lerp(layer.value(x, z), layer.value(x + 1, z), tx), ClimateLayers.lerp(layer.value(x, z + 1), layer.value(x + 1, z + 1), tx), tz);
        double coverage = ClimateLayers.lerp(ClimateLayers.lerp(layer.coverage(x, z), layer.coverage(x + 1, z), tx), ClimateLayers.lerp(layer.coverage(x, z + 1), layer.coverage(x + 1, z + 1), tx), tz);
        return new TemperatureSample(value, coverage);
    }

    private static int sourceX(Data layer, int blockX) {
        double imageX = RiversMask.INSTANCE.legacyImageX(blockX, layer.width);
        return Math.max(0, Math.min(layer.width - 1, (int)imageX));
    }

    private static int sourceZ(Data layer, int blockZ) {
        double imageZ = RiversMask.INSTANCE.legacyImageZ(blockZ, layer.height);
        return Math.max(0, Math.min(layer.height - 1, (int)imageZ));
    }

    private static TerrainKind surroundingLandKind(Data layer, int centreX, int centreZ) {
        for (int radius = 1; radius <= 48; ++radius) {
            int[] counts = new int[TerrainKind.values().length];
            for (int z = Math.max(0, centreZ - radius); z <= Math.min(layer.height - 1, centreZ + radius); ++z) {
                for (int x = Math.max(0, centreX - radius); x <= Math.min(layer.width - 1, centreX + radius); ++x) {
                    TerrainKind kind;
                    if (Math.max(Math.abs(x - centreX), Math.abs(z - centreZ)) != radius || (kind = TerrainKind.byCode(layer.values[z * layer.width + x] & 0xFF)) == TerrainKind.CITY || kind == TerrainKind.SURROUNDING || kind == TerrainKind.WATER || kind == TerrainKind.DESERT) continue;
                    int n = kind.code;
                    counts[n] = counts[n] + 1;
                }
            }
            TerrainKind result = TerrainKind.PLAINS;
            for (TerrainKind kind : TerrainKind.values()) {
                if (counts[kind.code] <= counts[result.code]) continue;
                result = kind;
            }
            if (counts[result.code] <= 0) continue;
            return result;
        }
        return TerrainKind.PLAINS;
    }

    private static Data load(String name, Kind kind) {
        try {
            Data var14x;
            try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/" + name);){
                if (input == null) {
                    throw new IOException("missing " + name);
                }
                BufferedImage image = ImageIO.read(input);
                if (image == null) {
                    throw new IOException(name + " is not readable");
                }
                int width = image.getWidth();
                int height = image.getHeight();
                byte[] values = new byte[width * height];
                byte[] coverage = new byte[width * height];
                int[] row = new int[width];
                for (int z = 0; z < height; ++z) {
                    image.getRGB(0, z, width, 1, row, 0, width);
                    for (int x = 0; x < width; ++x) {
                        values[z * width + x] = (byte)kind.value(row[x]);
                        coverage[z * width + x] = (byte)kind.coverage(row[x]);
                    }
                }
                if (kind == Kind.TERRAIN_CLASS) {
                    values = ClimateLayers.smoothTerrainClasses(values, width, height);
                    values = ClimateLayers.smoothTerrainClasses(values, width, height);
                    values = ClimateLayers.removeSmallTerrainRegions(values, width, height, (Integer)EarthShapeServerConfig.TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS.get(), true);
                    values = ClimateLayers.removeSmallTerrainRegions(values, width, height, (Integer)EarthShapeServerConfig.TERRAIN_BIOME_MINIMUM_REGION_PIXELS.get(), false);
                }
                EarthShape.LOGGER.info("[EarthShape] {} climate layer loaded: {}x{}.", new Object[]{name, width, height});
                var14x = new Data(width, height, values, coverage);
            }
            return var14x;
        }
        catch (IOException var14) {
            throw new IllegalStateException("EarthShape could not load " + name, var14);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static byte[] smoothTerrainClasses(byte[] source, int width, int height) {
        byte[] result = (byte[])source.clone();
        int[] counts = new int[TerrainKind.values().length];
        for (int z = 1; z < height - 1; ++z) {
            for (int x = 1; x < width - 1; ++x) {
                Arrays.fill(counts, 0);
                int current = source[z * width + x] & 0xFF;
                int currentKind = ClimateLayers.terrainKindForSmoothing(current);
                if (currentKind < 0) continue;
                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        int kind = ClimateLayers.terrainKindForSmoothing(source[(z + dz) * width + x + dx] & 0xFF);
                        if (kind < 0) continue;
                        int n = kind;
                        counts[n] = counts[n] + 1;
                    }
                }
                int winner = currentKind;
                for (int kind = 0; kind < counts.length; ++kind) {
                    if (counts[kind] <= counts[winner]) continue;
                    winner = kind;
                }
                if (winner == currentKind || counts[winner] < 5 || counts[currentKind] > 2) continue;
                result[z * width + x] = (byte)ClimateLayers.representativeTerrainCode(source, width, x, z, winner);
            }
        }
        return result;
    }

    private static int terrainKindForSmoothing(int code) {
        if (code >= 10 && code <= 13) {
            return TerrainKind.MOUNTAIN.code;
        }
        TerrainKind kind = TerrainKind.byCode(code);
        return kind == TerrainKind.CITY || kind == TerrainKind.SURROUNDING || kind == TerrainKind.WATER ? -1 : kind.code;
    }

    private static int representativeTerrainCode(byte[] source, int width, int x, int z, int kind) {
        if (kind != TerrainKind.MOUNTAIN.code) {
            return kind;
        }
        int[] mountainCounts = new int[4];
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                int code = source[(z + dz) * width + x + dx] & 0xFF;
                if (code < 10 || code > 13) continue;
                int n = code - 10;
                mountainCounts[n] = mountainCounts[n] + 1;
            }
        }
        int best = 0;
        for (int index = 1; index < mountainCounts.length; ++index) {
            if (mountainCounts[index] <= mountainCounts[best]) continue;
            best = index;
        }
        return 10 + best;
    }

    private static byte[] removeSmallTerrainRegions(byte[] source, int width, int height, int minimumArea, boolean isolatedOnly) {
        if (minimumArea <= 1) {
            return source;
        }
        byte[] result = (byte[])source.clone();
        boolean[] visited = new boolean[source.length];
        TerrainRegionQueue region = new TerrainRegionQueue();
        int[] neighbours = new int[TerrainKind.values().length];
        for (int start = 0; start < source.length; ++start) {
            if (visited[start]) continue;
            int terrain = ClimateLayers.terrainKindForSmoothing(source[start] & 0xFF);
            if (terrain < 0) {
                visited[start] = true;
                continue;
            }
            region.clear();
            region.add(start);
            visited[start] = true;
            Arrays.fill(neighbours, 0);
            for (int cursor = 0; cursor < region.size(); ++cursor) {
                int index = region.get(cursor);
                int x = index % width;
                int z = index / width;
                if (x > 0) {
                    ClimateLayers.collectTerrainNeighbour(source, visited, region, index - 1, terrain, neighbours);
                }
                if (x + 1 < width) {
                    ClimateLayers.collectTerrainNeighbour(source, visited, region, index + 1, terrain, neighbours);
                }
                if (z > 0) {
                    ClimateLayers.collectTerrainNeighbour(source, visited, region, index - width, terrain, neighbours);
                }
                if (z + 1 >= height) continue;
                ClimateLayers.collectTerrainNeighbour(source, visited, region, index + width, terrain, neighbours);
            }
            int surrounding = -1;
            int surroundingKinds = 0;
            for (int kind = 0; kind < neighbours.length; ++kind) {
                if (neighbours[kind] <= 0) continue;
                ++surroundingKinds;
                if (surrounding >= 0 && neighbours[kind] <= neighbours[surrounding]) continue;
                surrounding = kind;
            }
            if (region.size() >= minimumArea || surrounding < 0 || isolatedOnly && surroundingKinds != 1) continue;
            for (int cursor = 0; cursor < region.size(); ++cursor) {
                result[region.get((int)cursor)] = (byte)surrounding;
            }
        }
        return result;
    }

    private static void collectTerrainNeighbour(byte[] source, boolean[] visited, TerrainRegionQueue region, int index, int terrain, int[] neighbours) {
        int neighbour = ClimateLayers.terrainKindForSmoothing(source[index] & 0xFF);
        if (neighbour < 0) {
            return;
        }
        if (neighbour != terrain) {
            int n = neighbour;
            neighbours[n] = neighbours[n] + 1;
        } else if (!visited[index]) {
            visited[index] = true;
            region.add(index);
        }
    }

    private static double latitudeTemperature(int blockZ) {
        double imageZ = (double)blockZ / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.height() * 0.5;
        double latitude = Math.abs(imageZ / Math.max(1.0, (double)RiversMask.INSTANCE.height() - 1.0) * 2.0 - 1.0);
        return 0.55 - 1.35 * latitude * latitude;
    }

    private record Data(int width, int height, byte[] values, byte[] coverage) {
        double value(int x, int z) {
            return (double)(this.values[z * this.width + x] & 0xFF) / 255.0;
        }

        double coverage(int x, int z) {
            return (double)(this.coverage[z * this.width + x] & 0xFF) / 255.0;
        }
    }

    private record TemperatureSample(double value, double coverage) {
    }

    public static enum TreeCover {
        NONE,
        TEMPERATE,
        TROPICAL;

    }

    public static enum TerrainKind {
        WATER(0),
        DESERT(1),
        WETLAND(2),
        FOREST(3),
        JUNGLE(4),
        PLAINS(5),
        HILLS(6),
        MOUNTAIN(7),
        CITY(8),
        SURROUNDING(9);

        private final int code;

        private TerrainKind(int code) {
            this.code = code;
        }

        static TerrainKind byCode(int code) {
            for (TerrainKind kind : TerrainKind.values()) {
                if (kind.code != code) continue;
                return kind;
            }
            return PLAINS;
        }

        static TerrainKind fromColor(int color) {
            int rgb = color & 0xFFFFFF;
            switch (rgb) {
                case 21074: {
                    return JUNGLE;
                }
                case 22022: 
                case 444427: 
                case 3834706: {
                    return FOREST;
                }
                case 532354: {
                    return WATER;
                }
                case 4799247: 
                case 6050636: 
                case 11403519: {
                    return MOUNTAIN;
                }
                case 4953006: {
                    return WETLAND;
                }
                case 5667867: 
                case 8716032: {
                    return PLAINS;
                }
                case 7359007: 
                case 7506281: 
                case 8803358: {
                    return HILLS;
                }
                case 13543779: 
                case 0xFCFF00: {
                    return DESERT;
                }
                case 0xF0FF00: {
                    return SURROUNDING;
                }
                case 16711704: 
                case 0xFF007F: 
                case 0xFF00F0: 
                case 0xFFFFFF: {
                    return CITY;
                }
            }
            TerrainKind[] kinds = new TerrainKind[]{JUNGLE, FOREST, FOREST, FOREST, WATER, MOUNTAIN, MOUNTAIN, MOUNTAIN, WETLAND, PLAINS, PLAINS, HILLS, HILLS, HILLS, DESERT, DESERT, SURROUNDING, CITY, CITY, CITY, CITY};
            int[] palette = new int[]{21074, 22022, 444427, 3834706, 532354, 4799247, 6050636, 11403519, 4953006, 5667867, 8716032, 7359007, 7506281, 8803358, 13543779, 0xFCFF00, 0xF0FF00, 16711704, 0xFF007F, 0xFF00F0, 0xFFFFFF};
            TerrainKind nearest = PLAINS;
            int distance = Integer.MAX_VALUE;
            for (int i = 0; i < palette.length; ++i) {
                int d = Kind.colourDistance(rgb, palette[i]);
                if (d >= distance) continue;
                distance = d;
                nearest = kinds[i];
            }
            return nearest;
        }
    }

    private static enum Kind {
        LUMINANCE{

            @Override
            int value(int c) {
                return ((c >>> 16 & 0xFF) * 30 + (c >>> 8 & 0xFF) * 59 + (c & 0xFF) * 11) / 100;
            }
        }
        ,
        TEMPERATURE{

            @Override
            int value(int c) {
                int band = Kind.temperatureBand(c);
                return band < 0 ? 127 : band * 255 / 8;
            }

            @Override
            int coverage(int c) {
                return Kind.temperatureBand(c) < 0 ? 0 : 255;
            }
        }
        ,
        VEGETATION{

            @Override
            int value(int c) {
                return Kind.treeCover(c);
            }
        }
        ,
        TERRAIN_CLASS{

            @Override
            int value(int c) {
                return Kind.terrainElevationCode(c);
            }
        }
        ,
        NORMAL{

            @Override
            int value(int c) {
                double x = ((double)(c >>> 16 & 0xFF) - 127.5) / 127.5;
                double z = ((double)(c >>> 8 & 0xFF) - 127.5) / 127.5;
                return (int)(255.0 * Math.min(1.0, Math.sqrt(x * x + z * z)));
            }
        };


        abstract int value(int var1);

        int coverage(int color) {
            return 255;
        }

        private static int temperatureBand(int color) {
            int r = color >>> 16 & 0xFF;
            int g = color >>> 8 & 0xFF;
            int b = color & 0xFF;
            if (r > 244 && g > 244 && b > 244) {
                return -1;
            }
            switch (color & 0xFFFFFF) {
                case 129: 
                case 9787603: {
                    return 0;
                }
                case 190: 
                case 10341200: {
                    return 3;
                }
                case 33470: 
                case 16491568: {
                    return 5;
                }
                case 4694770: {
                    return 1;
                }
                case 4772041: {
                    return 2;
                }
                case 14954539: {
                    return 8;
                }
                case 16373540: {
                    return 4;
                }
                case 16540464: {
                    return 6;
                }
            }
            int[] palette = new int[]{9787603, 4694770, 4772041, 10341200, 16373540, 16491568, 16540464, 14954539, 129, 190, 33470};
            int[] bands = new int[]{0, 1, 2, 3, 4, 5, 6, 8, 0, 3, 5};
            int best = -1;
            int distance = Integer.MAX_VALUE;
            for (int i = 0; i < palette.length; ++i) {
                int pr = palette[i] >>> 16 & 0xFF;
                int pg = palette[i] >>> 8 & 0xFF;
                int pb = palette[i] & 0xFF;
                int d = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb);
                if (d >= distance) continue;
                distance = d;
                best = i;
            }
            return distance <= 24000 ? bands[best] : -1;
        }

        private static int treeCover(int color) {
            int rgb = color & 0xFFFFFF;
            switch (rgb) {
                case 0: {
                    return 0;
                }
                case 3110936: {
                    return 190;
                }
                case 5020723: {
                    return 210;
                }
                case 5767306: 
                case 9830655: {
                    return 255;
                }
                case 0xFFFF00: {
                    return 80;
                }
            }
            int[] palette = new int[]{0, 3110936, 5020723, 9830655, 5767306, 0xFFFF00};
            int[] values = new int[]{0, 190, 210, 255, 255, 80};
            int best = 0;
            int distance = Integer.MAX_VALUE;
            for (int i = 0; i < palette.length; ++i) {
                int d = Kind.colourDistance(rgb, palette[i]);
                if (d >= distance) continue;
                distance = d;
                best = i;
            }
            return values[best];
        }

        private static int terrainElevationCode(int color) {
            int rgb = color & 0xFFFFFF;
            return switch (rgb) {
                case 0xFFFFFF -> 13;
                case 6050636 -> 12;
                case 7359007 -> 11;
                case 4799245, 4799247 -> 10;
                default -> TerrainKind.fromColor((int)color).code;
            };
        }

        private static int colourDistance(int first, int second) {
            int dr = (first >>> 16 & 0xFF) - (second >>> 16 & 0xFF);
            int dg = (first >>> 8 & 0xFF) - (second >>> 8 & 0xFF);
            int db = (first & 0xFF) - (second & 0xFF);
            return dr * dr * 2 + dg * dg * 4 + db * db;
        }
    }

    private static final class TerrainRegionQueue {
        private int[] values = new int[64];
        private int size;

        private TerrainRegionQueue() {
        }

        void clear() {
            this.size = 0;
        }

        int size() {
            return this.size;
        }

        int get(int index) {
            return this.values[index];
        }

        void add(int value) {
            if (this.size == this.values.length) {
                this.values = Arrays.copyOf(this.values, this.values.length * 2);
            }
            this.values[this.size++] = value;
        }
    }
}

