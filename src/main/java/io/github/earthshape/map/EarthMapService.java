package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import net.neoforged.bus.api.Event;

/** Loads the override map once and exposes a pure absolute-coordinate sampling API to integrations. */
public final class EarthMapService {
    public static final EarthMapService INSTANCE = new EarthMapService();
    private volatile EarthMap map;
    private volatile EnvironmentLayers environmentLayers;
    private final AtomicLong revision = new AtomicLong();
    private final ThreadLocal<SampleCache> sampleCache = ThreadLocal.withInitial(SampleCache::new);
    private final ThreadLocal<EnvironmentCache> environmentCache = ThreadLocal.withInitial(EnvironmentCache::new);

    private EarthMapService() {}

    public void onConfigReload(Event ignored) {
        map = null;
        environmentLayers = null;
        revision.incrementAndGet();
    }

    public EarthSignal sample(long worldSeed, int blockX, int blockZ) {
        if (!EarthShapeConfig.ENABLED.get()) return new EarthSignal(0, 0.5D, 0D);
        EarthMap loaded = getMap();
        int scale = EarthShapeConfig.BLOCKS_PER_PIXEL.get();
        int wavelength = EarthShapeConfig.WARP_SCALE_BLOCKS.get();
        // Strict coastline mode intentionally has no domain warp: the source PNG is authoritative.
        int strength = EarthShapeConfig.STRICT_OCEAN_MASK.get() ? 0 : EarthShapeConfig.WARP_STRENGTH_BLOCKS.get();
        double coast = EarthShapeConfig.COAST_WIDTH_BLOCKS.get();
        long currentRevision = revision.get();
        SampleCache cached = sampleCache.get();
        if (cached.matches(currentRevision, worldSeed, blockX, blockZ, scale, wavelength, strength, coast)) {
            return cached.signal;
        }
        double warpedX = blockX + smoothValueNoise(worldSeed ^ 0x31A54A91L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        double warpedZ = blockZ + smoothValueNoise(worldSeed ^ 0x7F4A7C15L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        double distance = loaded.sampleSignedDistance(warpedX, warpedZ, scale);
        double land = smootherstep(-coast, coast, distance);
        // A land mask is geometry, not a mountain-height signal.  Feeding +1.0 to vanilla
        // continentalness made every map interior fall into its far-inland peak/stony biome
        // bands even though EarthTerrainDensity deliberately keeps it flat.  Keep the full
        // smooth shoreline ramp, but map it to vanilla's ocean -> mid-inland climate range so
        // normal plains, forests and coast variants can be selected from the remaining noises.
        double continentalness = -0.70D + land * 0.90D;
        EarthSignal signal = new EarthSignal(distance, land, continentalness);
        cached.store(currentRevision, worldSeed, blockX, blockZ, scale, wavelength, strength, coast, signal);
        return signal;
    }

    /** True only for a retained source-map land pixel; use this for topology-sensitive checks. */
    public boolean isLandPixel(int blockX, int blockZ) {
        if (!EarthShapeConfig.ENABLED.get()) return true;
        return getMap().sampleLandPixel(blockX, blockZ, EarthShapeConfig.BLOCKS_PER_PIXEL.get());
    }

    public boolean isNarrowWaterPassage(int blockX, int blockZ) {
        if (!EarthShapeConfig.ENABLED.get()) return false;
        return getMap().isNarrowWaterPassage(blockX, blockZ, EarthShapeConfig.BLOCKS_PER_PIXEL.get());
    }

    /** Samples optional real-world height, temperature and humidity maps. */
    public EarthEnvironmentSignal sampleEnvironment(int blockX, int blockZ) {
        if (!EarthShapeConfig.REAL_WORLD_LAYERS_ENABLED.get()) return EarthEnvironmentSignal.INACTIVE;
        EnvironmentLayers loaded = getEnvironmentLayers();
        if (!loaded.heightActive && !loaded.climateActive) return EarthEnvironmentSignal.INACTIVE;

        int scale = EarthShapeConfig.BLOCKS_PER_PIXEL.get();
        int wavelength = EarthShapeConfig.WARP_SCALE_BLOCKS.get();
        int strength = EarthShapeConfig.STRICT_OCEAN_MASK.get() ? 0 : EarthShapeConfig.WARP_STRENGTH_BLOCKS.get();
        long currentRevision = revision.get();
        EnvironmentCache cached = environmentCache.get();
        if (cached.matches(currentRevision, blockX, blockZ, scale, wavelength, strength)) return cached.signal;
        double warpedX = blockX + smoothValueNoise(0x31A54A91L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        double warpedZ = blockZ + smoothValueNoise(0x7F4A7C15L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        EarthMap landMap = getMap();
        double trees = loaded.trees.sample(warpedX, warpedZ, scale, landMap.width(), landMap.height());
        double height = loaded.heightActive ? loaded.height.sample(warpedX, warpedZ, scale, landMap.width(), landMap.height()) : 0.5D;
        double terrainDesert = loaded.terrain.sample(warpedX, warpedZ, scale, landMap.width(), landMap.height());
        double temperature = loaded.temperature.sample(warpedX, warpedZ, scale, landMap.width(), landMap.height());
        double rivers = loaded.rivers.sample(warpedX, warpedZ, scale, landMap.width(), landMap.height());
        double steepness = loaded.normal.sample(warpedX, warpedZ, scale, landMap.width(), landMap.height());

        // Every bundled layer contributes: the dedicated climate raster supplies temperature;
        // height cools it; trees and rivers supply moisture; and terrain supplies aridity.
        double latitude = Math.max(0.0D, Math.min(1.0D,
                warpedZ / (scale * (double) landMap.height()) + 0.5D));
        double desert = terrainDesert;
        double equatorWeight = 1.0D - Math.abs(latitude * 2.0D - 1.0D);
        temperature = clamp(temperature - height * 0.16D);
        double humidity = clamp(0.12D + trees * 0.62D + rivers * 0.18D + equatorWeight * 0.08D);
        // Explicit terrain desert palette overrides incidental river/latitude moisture.  This
        // keeps the Sahara hot and dry independently of the procedural world seed.
        temperature = Math.max(temperature, 0.86D * desert);
        humidity = humidity * (1.0D - desert) + 0.04D * desert;
        EarthEnvironmentSignal signal = new EarthEnvironmentSignal(loaded.heightActive, loaded.climateActive,
                height, temperature, humidity, rivers, steepness, desert);
        cached.store(currentRevision, blockX, blockZ, scale, wavelength, strength, signal);
        return signal;
    }

    private EarthMap getMap() {
        EarthMap current = map;
        if (current != null) return current;
        synchronized (this) {
            if (map == null) map = load();
            return map;
        }
    }

    private EnvironmentLayers getEnvironmentLayers() {
        EnvironmentLayers current = environmentLayers;
        if (current != null) return current;
        synchronized (this) {
            if (environmentLayers == null) environmentLayers = loadEnvironmentLayers();
            return environmentLayers;
        }
    }

    private EarthMap load() {
        try (InputStream in = EarthShape.class.getResourceAsStream("/earthshape/hoi4/rivers.bmp")) {
            if (in == null) throw new IOException("bundled HOI4 rivers map is missing");
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("not a readable image");
            EarthMap loaded = EarthMap.fromHoi4Rivers(image, EarthShapeConfig.MINIMUM_LAND_COMPONENT_PIXELS.get());
            EarthShape.LOGGER.info("[EarthShape] Bundled HOI4 rivers map loaded as the temporary coastline mask ({}x{} pixels, removed {} isolated land fragments / {} pixels; min component={} px).",
                    image.getWidth(), image.getHeight(), loaded.removedLandComponents(), loaded.removedLandPixels(),
                    EarthShapeConfig.MINIMUM_LAND_COMPONENT_PIXELS.get());
            return loaded;
        } catch (IOException ex) {
            throw new IllegalStateException("EarthShape could not load bundled HOI4 rivers.bmp", ex);
        }
    }

    private EnvironmentLayers loadEnvironmentLayers() {
        try {
            EarthMap landMap = getMap();
            EarthLayer height = loadBundledLayer("/earthshape/hoi4/heightmap.bmp", landMap, LayerType.HEIGHT);
            EarthLayer terrain = loadBundledLayer("/earthshape/hoi4/terrain.bmp", landMap, LayerType.TERRAIN);
            EarthLayer temperature = loadBundledLayer("/earthshape/hoi4/earth_temperature.png", landMap, LayerType.TEMPERATURE);
            EarthLayer rivers = loadBundledLayer("/earthshape/hoi4/rivers.bmp", landMap, LayerType.RIVERS);
            EarthLayer trees = loadBundledLayer("/earthshape/hoi4/trees.bmp", landMap, LayerType.TREES);
            EarthLayer normal = loadBundledLayer("/earthshape/hoi4/world_normal.bmp", landMap, LayerType.NORMAL);
            EarthShape.LOGGER.info("[EarthShape] Bundled height, temperature, terrain, rivers, trees and normal layers loaded.");
            return new EnvironmentLayers(true, height, true, terrain, temperature, rivers, trees, normal);
        } catch (IOException | IllegalArgumentException ex) {
            EarthShape.LOGGER.warn("[EarthShape] Real-world layers are enabled but unavailable; using seed-only height and climate. {}", ex.getMessage());
            return EnvironmentLayers.INACTIVE;
        }
    }

    private static EarthLayer loadBundledLayer(String resource, EarthMap landMap, LayerType type) throws IOException {
        try (InputStream in = EarthShape.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing bundled resource " + resource);
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("unreadable bundled resource " + resource);
            if (image.getWidth() * landMap.height() != image.getHeight() * landMap.width()) {
                throw new IllegalArgumentException(resource + " must use the terrain map's aspect ratio");
            }
            return switch (type) {
                case HEIGHT -> EarthLayer.fromHeightmap(image);
                case RIVERS -> EarthLayer.fromRivers(image);
                case TREES -> EarthLayer.fromTrees(image);
                case NORMAL -> EarthLayer.fromNormal(image);
                case TERRAIN -> EarthLayer.fromTerrain(image);
                case TEMPERATURE -> EarthLayer.fromTemperature(image);
            };
        }
    }

    private static double smoothValueNoise(long salt, double x, double z) {
        long x0 = (long) Math.floor(x), z0 = (long) Math.floor(z);
        double tx = smooth(x - x0), tz = smooth(z - z0);
        double a = lerp(hashToUnit(salt, x0, z0), hashToUnit(salt, x0 + 1, z0), tx);
        double b = lerp(hashToUnit(salt, x0, z0 + 1), hashToUnit(salt, x0 + 1, z0 + 1), tx);
        return lerp(a, b, tz) * 2.0D - 1.0D;
    }
    private static double hashToUnit(long seed, long x, long z) { long n = seed ^ (x * 341873128712L) ^ (z * 132897987541L); n = (n ^ (n >>> 33)) * 0xff51afd7ed558ccdL; n = (n ^ (n >>> 33)) * 0xc4ceb9fe1a85ec53L; return ((n ^ (n >>> 33)) >>> 11) * 0x1.0p-53; }
    private static double smooth(double t) { return t * t * (3.0D - 2.0D * t); }
    private static double smootherstep(double min, double max, double value) { double t = Math.max(0, Math.min(1, (value - min) / (max - min))); return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp(double value) { return Math.max(0.0D, Math.min(1.0D, value)); }

    /** One-entry, per-worker cache: density is normally evaluated repeatedly at the same X/Z over Y. */
    private static final class SampleCache {
        private long revision = Long.MIN_VALUE;
        private long seed;
        private int x, z, scale, wavelength, strength;
        private double coast;
        private EarthSignal signal;

        boolean matches(long revision, long seed, int x, int z, int scale, int wavelength, int strength, double coast) {
            return this.signal != null && this.revision == revision && this.seed == seed && this.x == x && this.z == z
                    && this.scale == scale && this.wavelength == wavelength && this.strength == strength && this.coast == coast;
        }

        void store(long revision, long seed, int x, int z, int scale, int wavelength, int strength, double coast, EarthSignal signal) {
            this.revision = revision; this.seed = seed; this.x = x; this.z = z; this.scale = scale;
            this.wavelength = wavelength; this.strength = strength; this.coast = coast; this.signal = signal;
        }
    }

    private record EnvironmentLayers(boolean heightActive, EarthLayer height, boolean climateActive, EarthLayer terrain, EarthLayer temperature, EarthLayer rivers, EarthLayer trees, EarthLayer normal) {
        private static final EnvironmentLayers INACTIVE = new EnvironmentLayers(false, null, false, null, null, null, null, null);
    }

    private enum LayerType { HEIGHT, TERRAIN, TEMPERATURE, RIVERS, TREES, NORMAL }

    /** Shares an environment lookup among height, temperature and humidity density functions on one worker. */
    private static final class EnvironmentCache {
        private long revision = Long.MIN_VALUE;
        private int x, z, scale, wavelength, strength;
        private EarthEnvironmentSignal signal;

        boolean matches(long revision, int x, int z, int scale, int wavelength, int strength) {
            return signal != null && this.revision == revision && this.x == x && this.z == z
                    && this.scale == scale && this.wavelength == wavelength && this.strength == strength;
        }

        void store(long revision, int x, int z, int scale, int wavelength, int strength, EarthEnvironmentSignal signal) {
            this.revision = revision; this.x = x; this.z = z; this.scale = scale;
            this.wavelength = wavelength; this.strength = strength; this.signal = signal;
        }
    }
}
