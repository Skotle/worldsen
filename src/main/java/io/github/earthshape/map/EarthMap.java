package io.github.earthshape.map;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Immutable grayscale continentalness map. Sampling is bilinear and uses a signed distance field,
 * so it has no knowledge of chunks and cannot produce 16-block seams.
 */
public final class EarthMap {
    private static final float INF = 1.0e20F;
    private final int width;
    private final int height;
    private final float[] signedDistancePixels;

    private EarthMap(int width, int height, float[] signedDistancePixels) {
        this.width = width;
        this.height = height;
        this.signedDistancePixels = signedDistancePixels;
    }

    public static EarthMap from(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[] land = new boolean[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int rgb = image.getRGB(x, y);
            int gray = (((rgb >>> 16) & 255) * 30 + ((rgb >>> 8) & 255) * 59 + (rgb & 255) * 11) / 100;
            land[y * width + x] = gray >= 128;
        }
        float[] toLand = chamferDistance(land, width, height, true);
        float[] toOcean = chamferDistance(land, width, height, false);
        float[] sdf = new float[land.length];
        for (int i = 0; i < sdf.length; i++) sdf[i] = land[i] ? toOcean[i] : -toLand[i];
        return new EarthMap(width, height, sdf);
    }

    /** Positive inside land; negative in ocean. World coordinates map to a finite centered image. */
    public double sampleSignedDistance(double worldX, double worldZ, int blocksPerPixel) {
        double px = worldX / blocksPerPixel + width * 0.5D;
        double py = worldZ / blocksPerPixel + height * 0.5D;
        if (px < 0 || py < 0 || px >= width - 1 || py >= height - 1) return -Math.max(width, height) * blocksPerPixel;
        int x0 = (int) Math.floor(px), y0 = (int) Math.floor(py);
        double tx = px - x0, ty = py - y0;
        double a = lerp(signedDistancePixels[y0 * width + x0], signedDistancePixels[y0 * width + x0 + 1], tx);
        double b = lerp(signedDistancePixels[(y0 + 1) * width + x0], signedDistancePixels[(y0 + 1) * width + x0 + 1], tx);
        return lerp(a, b, ty) * blocksPerPixel;
    }

    private static float[] chamferDistance(boolean[] land, int w, int h, boolean targetLand) {
        float[] d = new float[land.length];
        for (int i = 0; i < d.length; i++) d[i] = land[i] == targetLand ? 0F : INF;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) relax(d, w, h, x, y, -1, 0, 1F, -1, -1, 1.4142135F, 0, -1, 1F, 1, -1, 1.4142135F);
        for (int y = h - 1; y >= 0; y--) for (int x = w - 1; x >= 0; x--) relax(d, w, h, x, y, 1, 0, 1F, 1, 1, 1.4142135F, 0, 1, 1F, -1, 1, 1.4142135F);
        return d;
    }

    private static void relax(float[] d, int w, int h, int x, int y, int dx1, int dy1, float c1, int dx2, int dy2, float c2, int dx3, int dy3, float c3, int dx4, int dy4, float c4) {
        int here = y * w + x;
        check(d, w, h, here, x + dx1, y + dy1, c1); check(d, w, h, here, x + dx2, y + dy2, c2);
        check(d, w, h, here, x + dx3, y + dy3, c3); check(d, w, h, here, x + dx4, y + dy4, c4);
    }
    private static void check(float[] d, int w, int h, int here, int x, int y, float cost) { if (x >= 0 && y >= 0 && x < w && y < h) d[here] = Math.min(d[here], d[y * w + x] + cost); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
