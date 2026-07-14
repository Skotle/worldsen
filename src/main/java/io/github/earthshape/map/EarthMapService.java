package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Loads the override map once and exposes a pure absolute-coordinate sampling API to integrations. */
public final class EarthMapService {
    public static final EarthMapService INSTANCE = new EarthMapService();
    private volatile EarthMap map;

    private EarthMapService() {}

    public void onConfigReload(Event ignored) {
        map = null;
    }

    public EarthSignal sample(long worldSeed, int blockX, int blockZ) {
        if (!EarthShapeConfig.ENABLED.get()) return new EarthSignal(0, 0.5D, 0D);
        EarthMap loaded = getMap();
        int scale = EarthShapeConfig.BLOCKS_PER_PIXEL.get();
        int wavelength = EarthShapeConfig.WARP_SCALE_BLOCKS.get();
        int strength = EarthShapeConfig.WARP_STRENGTH_BLOCKS.get();
        double warpedX = blockX + smoothValueNoise(worldSeed ^ 0x31A54A91L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        double warpedZ = blockZ + smoothValueNoise(worldSeed ^ 0x7F4A7C15L, blockX / (double) wavelength, blockZ / (double) wavelength) * strength;
        double distance = loaded.sampleSignedDistance(warpedX, warpedZ, scale);
        double coast = EarthShapeConfig.COAST_WIDTH_BLOCKS.get();
        double land = smootherstep(-coast, coast, distance);
        // The -1..1 range can be blended with a pack's original continentalness without selecting biomes directly.
        return new EarthSignal(distance, land, land * 2.0D - 1.0D);
    }

    private EarthMap getMap() {
        EarthMap current = map;
        if (current != null) return current;
        synchronized (this) {
            if (map == null) map = load();
            return map;
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
            EarthShape.LOGGER.info("[EarthShape] Map loaded: {} ({}x{} pixels).", target, image.getWidth(), image.getHeight());
            return EarthMap.from(image);
        } catch (IOException ex) {
            throw new IllegalStateException("EarthShape could not load " + target, ex);
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
}
