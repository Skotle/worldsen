package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.loading.FMLPaths;

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
        double coast = Math.min(EarthShapeConfig.COAST_WIDTH_BLOCKS.get(), 96.0D);
        long currentRevision = revision.get();
        SampleCache cached = sampleCache.get();
        if (cached.matches(currentRevision, worldSeed, blockX, blockZ, scale, wavelength, strength, coast)) {
            return cached.signal;
        }
        double warpedX = blockX + smoothValueNoise(worldSeed ^ 0x31A54A91L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        double warpedZ = blockZ + smoothValueNoise(worldSeed ^ 0x7F4A7C15L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        double distance = loaded.sampleSignedDistance(warpedX, warpedZ, scale);
        double land = smootherstep(-coast, coast, distance);
        // The -1..1 range can be blended with a pack's original continentalness without selecting biomes directly.
        EarthSignal signal = new EarthSignal(distance, land, land * 2.0D - 1.0D);
        cached.store(currentRevision, worldSeed, blockX, blockZ, scale, wavelength, strength, coast, signal);
        return signal;
    }

    /** Samples optional real-world height, temperature and humidity maps. */
    public EarthEnvironmentSignal sampleEnvironment(int blockX, int blockZ) {
        if (!EarthShapeConfig.REAL_WORLD_LAYERS_ENABLED.get()) return EarthEnvironmentSignal.INACTIVE;
        EnvironmentLayers loaded = getEnvironmentLayers();
        if (!loaded.active) return EarthEnvironmentSignal.INACTIVE;

        int scale = EarthShapeConfig.BLOCKS_PER_PIXEL.get();
        int wavelength = EarthShapeConfig.WARP_SCALE_BLOCKS.get();
        int strength = EarthShapeConfig.STRICT_OCEAN_MASK.get() ? 0 : EarthShapeConfig.WARP_STRENGTH_BLOCKS.get();
        long currentRevision = revision.get();
        EnvironmentCache cached = environmentCache.get();
        if (cached.matches(currentRevision, blockX, blockZ, scale, wavelength, strength)) return cached.signal;
        double warpedX = blockX + smoothValueNoise(0x31A54A91L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        double warpedZ = blockZ + smoothValueNoise(0x7F4A7C15L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        EarthEnvironmentSignal signal = new EarthEnvironmentSignal(true,
                loaded.height.sample(warpedX, warpedZ, scale),
                loaded.temperature.sample(warpedX, warpedZ, scale),
                loaded.humidity.sample(warpedX, warpedZ, scale));
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
        Path target = FMLPaths.CONFIGDIR.get().resolve("earthshape/earth_continentalness.png");
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) try (InputStream in = EarthShape.class.getResourceAsStream("/earthshape/earth_continentalness.png")) {
                if (in == null) throw new IOException("bundled earth map is missing");
                Files.copy(in, target);
            }
            BufferedImage image = ImageIO.read(target.toFile());
            if (image == null) throw new IOException("not a readable image");
            EarthMap loaded = EarthMap.from(image, EarthShapeConfig.MINIMUM_LAND_COMPONENT_PIXELS.get());
            EarthShape.LOGGER.info("[EarthShape] Map loaded: {} ({}x{} pixels, removed {} isolated land fragments / {} pixels; min component={} px).",
                    target, image.getWidth(), image.getHeight(), loaded.removedLandComponents(), loaded.removedLandPixels(),
                    EarthShapeConfig.MINIMUM_LAND_COMPONENT_PIXELS.get());
            return loaded;
        } catch (IOException ex) {
            throw new IllegalStateException("EarthShape could not load " + target, ex);
        }
    }

    private EnvironmentLayers loadEnvironmentLayers() {
        Path directory = FMLPaths.CONFIGDIR.get().resolve("earthshape");
        try {
            EarthMap landMap = getMap();
            EarthLayer height = loadLayer(directory.resolve("earth_height.png"), landMap);
            EarthLayer temperature = loadLayer(directory.resolve("earth_temperature.png"), landMap);
            EarthLayer humidity = loadLayer(directory.resolve("earth_humidity.png"), landMap);
            EarthShape.LOGGER.info("[EarthShape] Real-world height and climate layers loaded.");
            return new EnvironmentLayers(true, height, temperature, humidity);
        } catch (IOException | IllegalArgumentException ex) {
            EarthShape.LOGGER.warn("[EarthShape] Real-world layers are enabled but unavailable; using seed-only height and climate. {}", ex.getMessage());
            return EnvironmentLayers.INACTIVE;
        }
    }

    private static EarthLayer loadLayer(Path path, EarthMap landMap) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("missing " + path.getFileName());
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("unreadable " + path.getFileName());
        EarthLayer layer = EarthLayer.from(image);
        if (layer.width() != landMap.width() || layer.height() != landMap.height()) {
            throw new IllegalArgumentException(path.getFileName() + " must be " + landMap.width() + "x" + landMap.height());
        }
        return layer;
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

    private record EnvironmentLayers(boolean active, EarthLayer height, EarthLayer temperature, EarthLayer humidity) {
        private static final EnvironmentLayers INACTIVE = new EnvironmentLayers(false, null, null, null);
    }

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
