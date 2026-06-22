package com.smd.toomanytinkers.api.client.material;

import java.awt.image.BufferedImage;

/**
 * Utility entry points for external tools or resource pipelines that want to
 * convert ordinary images into part parameter maps.
 *
 * <p>The runtime compatibility renderer does not call this class. Runtime
 * rendering treats parameter-map pixels as authored data and samples them
 * directly.</p>
 */
public final class PartParamMapConverter {

    private PartParamMapConverter() {
    }

    public static BufferedImage preserveAuthoredPixels(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        forEachPixel(source, (x, y, argb) -> out.setRGB(x, y, argb));
        return out;
    }

    public static BufferedImage luminanceToRampParameter(BufferedImage source, Options options) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Range range = options.normalize() ? scanLuminanceRange(source, options.alphaThreshold()) : Range.FULL;

        forEachPixel(source, (x, y, argb) -> {
            int alpha = alpha(argb);
            if (alpha <= options.alphaThreshold()) {
                out.setRGB(x, y, 0);
                return;
            }

            int luminance = luminance(argb);
            int parameter = range.normalize(luminance);
            int rgb = (parameter << 16) | (parameter << 8) | parameter;
            out.setRGB(x, y, (alpha << 24) | rgb);
        });
        return out;
    }

    private static Range scanLuminanceRange(BufferedImage source, int alphaThreshold) {
        MutableRange range = new MutableRange();
        forEachPixel(source, (x, y, argb) -> {
            if (alpha(argb) > alphaThreshold) {
                range.include(luminance(argb));
            }
        });
        return range.toRange();
    }

    private static void forEachPixel(BufferedImage source, PixelConsumer consumer) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                consumer.accept(x, y, source.getRGB(x, y));
            }
        }
    }

    private static int luminance(int argb) {
        double r = red(argb) / 255.0;
        double g = green(argb) / 255.0;
        double b = blue(argb) / 255.0;
        return clamp255((int) (Math.sqrt(0.241 * r * r + 0.691 * g * g + 0.068 * b * b) * 255.0));
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xff;
    }

    private static int red(int argb) {
        return (argb >>> 16) & 0xff;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xff;
    }

    private static int blue(int argb) {
        return argb & 0xff;
    }

    private static int clamp255(int value) {
        return Math.min(255, Math.max(0, value));
    }

    public static final class Options {
        public static final Options DEFAULT = new Options(false, 0);
        public static final Options NORMALIZED = new Options(true, 0);

        private final boolean normalize;
        private final int alphaThreshold;

        public Options(boolean normalize, int alphaThreshold) {
            this.normalize = normalize;
            this.alphaThreshold = clamp255(alphaThreshold);
        }

        public boolean normalize() {
            return normalize;
        }

        public int alphaThreshold() {
            return alphaThreshold;
        }
    }

    private interface PixelConsumer {
        void accept(int x, int y, int argb);
    }

    private static final class Range {
        private static final Range FULL = new Range(0, 255);

        private final int min;
        private final int max;

        private Range(int min, int max) {
            this.min = min;
            this.max = max;
        }

        private int normalize(int value) {
            if (max <= min) {
                return value;
            }
            return clamp255((value - min) * 255 / (max - min));
        }
    }

    private static final class MutableRange {
        private int min = 255;
        private int max = 0;
        private boolean seen;

        private void include(int value) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            seen = true;
        }

        private Range toRange() {
            return seen ? new Range(min, max) : Range.FULL;
        }
    }
}
