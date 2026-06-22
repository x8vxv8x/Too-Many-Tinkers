package com.smd.toomanytinkers.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TmtPartMaskMapManager {

    private static final int TILE_SIZE = 16;
    private static final String DYNAMIC_NAME = "tmt_part_mask_map";
    private static final ResourceLocation MISSING = new ResourceLocation("minecraft", "missingno");
    private static final Map<ResourceLocation, MaskEntry> MASKS = new LinkedHashMap<>();

    private static DynamicTexture texture;
    private static ResourceLocation textureLocation;
    private static int height = TILE_SIZE;
    private static boolean dirty = true;

    private TmtPartMaskMapManager() {
    }

    public static void rebuildStart() {
        MASKS.clear();
        registerMask(MISSING);
        dirty = true;
    }

    public static void registerMask(ResourceLocation texture) {
        if (MASKS.containsKey(texture)) {
            return;
        }
        int slot = MASKS.size();
        MASKS.put(texture, loadMask(texture, slot));
        dirty = true;
    }

    public static int getSlot(ResourceLocation texture) {
        return getOrRegister(texture).slot;
    }

    public static boolean[] getOpacity(ResourceLocation texture) {
        return getOrRegister(texture).opaque;
    }

    public static ResourceLocation getTextureLocation() {
        ensureTexture();
        return textureLocation;
    }

    public static int getHeight() {
        ensureTexture();
        return height;
    }

    public static int getMaskCount() {
        return MASKS.size();
    }

    private static void ensureTexture() {
        if (!dirty && textureLocation != null) {
            return;
        }

        height = Math.max(1, MASKS.size()) * TILE_SIZE;
        texture = new DynamicTexture(TILE_SIZE, height);
        int[] data = texture.getTextureData();
        java.util.Arrays.fill(data, 0);
        for (MaskEntry entry : MASKS.values()) {
            int base = entry.slot * TILE_SIZE * TILE_SIZE;
            System.arraycopy(entry.pixels, 0, data, base, entry.pixels.length);
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (textureLocation != null) {
            mc.getTextureManager().deleteTexture(textureLocation);
        }
        textureLocation = mc.getTextureManager().getDynamicTextureLocation(DYNAMIC_NAME, texture);
        texture.updateDynamicTexture();
        dirty = false;
    }

    private static MaskEntry loadMask(ResourceLocation texture, int slot) {
        BufferedImage image = readTexture(texture);
        int[] pixels = new int[TILE_SIZE * TILE_SIZE];
        boolean[] opaque = new boolean[TILE_SIZE * TILE_SIZE];
        if (image == null) {
            java.util.Arrays.fill(pixels, 0xffff00ff);
            java.util.Arrays.fill(opaque, true);
            return new MaskEntry(slot, pixels, opaque);
        }

        for (int y = 0; y < TILE_SIZE; y++) {
            int sourceY = sampleCoord(y, TILE_SIZE, image.getHeight());
            for (int x = 0; x < TILE_SIZE; x++) {
                int sourceX = sampleCoord(x, TILE_SIZE, image.getWidth());
                int argb = image.getRGB(sourceX, sourceY);
                int alpha = (argb >>> 24) & 0xff;
                int index = y * TILE_SIZE + x;
                pixels[index] = argb;
                opaque[index] = alpha > 16;
            }
        }
        return new MaskEntry(slot, pixels, opaque);
    }

    private static MaskEntry getOrRegister(ResourceLocation texture) {
        MaskEntry entry = MASKS.get(texture);
        if (entry != null) {
            return entry;
        }
        registerMask(texture);
        entry = MASKS.get(texture);
        if (entry != null) {
            return entry;
        }
        if (!MASKS.containsKey(MISSING)) {
            registerMask(MISSING);
        }
        return MASKS.get(MISSING);
    }

    @Nullable
    private static BufferedImage readTexture(ResourceLocation texture) {
        ResourceLocation file = normalizeTextureFile(texture);
        try (InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(file).getInputStream()) {
            return ImageIO.read(stream);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static ResourceLocation normalizeTextureFile(ResourceLocation texture) {
        String value = texture.toString();
        int separator = value.indexOf(':');
        String domain = separator >= 0 ? value.substring(0, separator) : "minecraft";
        String path = separator >= 0 ? value.substring(separator + 1) : value;
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path += ".png";
        }
        return new ResourceLocation(domain, path);
    }

    private static int sampleCoord(int value, int sourceSize, int targetSize) {
        return Math.min(targetSize - 1, Math.max(0, (int) ((value + 0.5f) * targetSize / sourceSize)));
    }

    private static final class MaskEntry {
        private final int slot;
        private final int[] pixels;
        private final boolean[] opaque;

        private MaskEntry(int slot, int[] pixels, boolean[] opaque) {
            this.slot = slot;
            this.pixels = pixels;
            this.opaque = opaque;
        }
    }
}
