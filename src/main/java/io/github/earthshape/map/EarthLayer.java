package io.github.earthshape.map;

import java.awt.image.BufferedImage;

/** Immutable grayscale map layer using the same centered, bilinear coordinate mapping as EarthMap. */
final class EarthLayer {
    private final int width;
    private final int height;
    private final float[] values;

    private EarthLayer(int width, int height, float[] values) {
        this.width = width;
        this.height = height;
        this.values = values;
    }

    static EarthLayer fromHeightmap(BufferedImage image) {
        return from(image, 83, 239, Mode.GRAYSCALE);
    }

    static EarthLayer fromRivers(BufferedImage image) {
        return from(image, 0, 1, Mode.RIVERS);
    }

    static EarthLayer fromTrees(BufferedImage image) {
        return from(image, 0, 1, Mode.TREES);
    }

    static EarthLayer fromNormal(BufferedImage image) {
        return from(image, 0, 1, Mode.NORMAL);
    }

    static EarthLayer fromTerrain(BufferedImage image) {
        return from(image, 0, 1, Mode.TERRAIN);
    }

    private static EarthLayer from(BufferedImage image, int minimum, int maximum, Mode mode) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[] values = new float[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int rgb = image.getRGB(x, y);
            int red = (rgb >>> 16) & 255, green = (rgb >>> 8) & 255, blue = rgb & 255;
            int gray = (red * 30 + green * 59 + blue * 11) / 100;
            values[y * width + x] = switch (mode) {
                // HOI4 reserves the low end for sea level (89).  The previous 2.2 exponent
                // compressed ordinary land values such as 100-130 to almost zero, effectively
                // disabling the heightmap except on the very highest mountains.
                case GRAYSCALE -> {
                    float normalized = Math.max(0F, Math.min(1F, (gray - 89) / 150.0F));
                    yield normalized * 0.82F + (float) Math.pow(normalized, 2.2D) * 0.18F;
                }
                case RIVERS -> (red == 255 && green == 255 && blue == 255) || (red == 122 && green == 122 && blue == 122) ? 0F : 1F;
                case TREES -> red == 0 && green == 0 && blue == 0 ? 0F : 1F;
                // Terrain normal maps encode slope in X/Y and height-facing direction in Z.
                // Z alone is near 255 even on gentle hills, so it discarded almost all relief.
                case NORMAL -> Math.min(1F, (float) Math.hypot(red - 128.0D, green - 128.0D) / 96.0F);
                // HOI4 uses this saturated yellow for the Sahara and Arabian deserts.
                case TERRAIN -> red >= 220 && green >= 220 && blue <= 64 ? 1F : 0F;
            };
        }
        return new EarthLayer(width, height, values);
    }

    int width() { return width; }
    int height() { return height; }

    double sample(double worldX, double worldZ, int blocksPerPixel, int referenceWidth, int referenceHeight) {
        double px = (worldX / blocksPerPixel / referenceWidth + 0.5D) * width;
        double py = (worldZ / blocksPerPixel / referenceHeight + 0.5D) * height;
        if (px < 0 || py < 0 || px >= width - 1 || py >= height - 1) return 0.5D;
        int x0 = (int) Math.floor(px), y0 = (int) Math.floor(py);
        double tx = px - x0, ty = py - y0;
        double a = lerp(values[y0 * width + x0], values[y0 * width + x0 + 1], tx);
        double b = lerp(values[(y0 + 1) * width + x0], values[(y0 + 1) * width + x0 + 1], tx);
        return lerp(a, b, ty);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private enum Mode { GRAYSCALE, RIVERS, TREES, NORMAL, TERRAIN }
}
