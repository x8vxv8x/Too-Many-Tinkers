package com.smd.toomanytinkers.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.materials.Material;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TmtMaterialMapManager {

    public static final int UNIT_SIZE = 256;
    public static final int SOURCE_TILE_SIZE = 16;

    private static final int RAMPS_PER_UNIT = UNIT_SIZE;
    private static final int SOURCE_TILES_PER_AXIS = UNIT_SIZE / SOURCE_TILE_SIZE;
    private static final int SOURCE_TILES_PER_UNIT = SOURCE_TILES_PER_AXIS * SOURCE_TILES_PER_AXIS;
    private static final ResourceLocation TEXTURE_LOCATION =
            new ResourceLocation("toomanytinkers", "dynamic/material_map");

    private static final List<MaterialEntry> MATERIALS = new ArrayList<>();
    private static final Map<ResourceLocation, Integer> SOURCE_TILES = new LinkedHashMap<>();

    private static int unitColumns = 1;
    private static int unitRows = 1;
    private static int width = UNIT_SIZE;
    private static int height = UNIT_SIZE;
    private static int sourceUnitBase = 1;
    private static int slotCount;
    private static UploadedMaterialTexture texture;
    private static ResourceLocation textureLocation;
    private static boolean dirty = true;

    private TmtMaterialMapManager() {
    }

    public static void rebuildStart() {
        MATERIALS.clear();
        SOURCE_TILES.clear();
        unitColumns = 1;
        unitRows = 1;
        width = UNIT_SIZE;
        height = UNIT_SIZE;
        sourceUnitBase = 1;
        slotCount = 0;
        dirty = true;
    }

    public static Allocation addMaterial(Material material, @Nullable ResourceLocation sourceTexture) {
        int rampIndex = MATERIALS.size();
        int sourceIndex = -1;
        int type = MaterialDescriptor.TYPE_RAMP;
        if (sourceTexture != null) {
            type = MaterialDescriptor.TYPE_TEXTURE;
            Integer existing = SOURCE_TILES.get(sourceTexture);
            if (existing == null) {
                existing = SOURCE_TILES.size();
                SOURCE_TILES.put(sourceTexture, existing);
            }
            sourceIndex = existing;
        }
        MATERIALS.add(new MaterialEntry(material, rampIndex));
        dirty = true;
        return new Allocation(type, rampIndex, sourceIndex);
    }

    public static void finishUpload() {
        ensureTexture();
    }

    public static ResourceLocation getTextureLocation() {
        ensureTexture();
        return textureLocation;
    }

    public static int getWidth() {
        ensureTexture();
        return width;
    }

    public static int getHeight() {
        ensureTexture();
        return height;
    }

    public static int getUnitColumns() {
        ensureTexture();
        return unitColumns;
    }

    public static int getSourceUnitBase() {
        ensureTexture();
        return sourceUnitBase;
    }

    public static int getSlotCount() {
        return slotCount;
    }

    private static void ensureTexture() {
        if (!dirty && textureLocation != null) {
            return;
        }

        planLayout();
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, 0xffffffff);
        writeRampRows(pixels);
        writeSourceTiles(pixels);

        UploadedMaterialTexture uploaded = new UploadedMaterialTexture(pixels, width, height);

        Minecraft mc = Minecraft.getMinecraft();
        if (textureLocation != null) {
            mc.getTextureManager().deleteTexture(textureLocation);
        }
        texture = uploaded;
        textureLocation = TEXTURE_LOCATION;
        mc.getTextureManager().loadTexture(textureLocation, texture);
        dirty = false;
    }

    private static void planLayout() {
        int rampUnits = Math.max(1, divCeil(MATERIALS.size(), RAMPS_PER_UNIT));
        int sourceUnits = divCeil(SOURCE_TILES.size(), SOURCE_TILES_PER_UNIT);
        sourceUnitBase = rampUnits;
        int totalUnits = Math.max(1, rampUnits + sourceUnits);
        int size = 1;
        int sqrtCeil = (int) Math.ceil(Math.sqrt(totalUnits));
        while (size < sqrtCeil) {
            size <<= 1;
        }
        if ((size * size) / 2 >= totalUnits) {
            unitColumns = Math.max(1, size / 2);
            unitRows = size;
        } else {
            unitColumns = size;
            unitRows = size;
        }
        width = unitColumns * UNIT_SIZE;
        height = unitRows * UNIT_SIZE;
        slotCount = MATERIALS.size() + SOURCE_TILES.size();
    }

    private static void writeRampRows(int[] pixels) {
        for (MaterialEntry entry : MATERIALS) {
            int unit = entry.rampIndex / RAMPS_PER_UNIT;
            int row = entry.rampIndex % RAMPS_PER_UNIT;
            int origin = pixelIndex(unit, 0, row);
            for (int gray = 0; gray < UNIT_SIZE; gray++) {
                pixels[origin + gray] = MaterialColorEvaluator.evaluate(entry.material, gray / 255f);
            }
        }
    }

    private static void writeSourceTiles(int[] pixels) {
        for (Map.Entry<ResourceLocation, Integer> sourceEntry : SOURCE_TILES.entrySet()) {
            BufferedImage source = readTexture(sourceEntry.getKey());
            int sourceIndex = sourceEntry.getValue();
            int unit = sourceUnitBase + sourceIndex / SOURCE_TILES_PER_UNIT;
            int local = sourceIndex % SOURCE_TILES_PER_UNIT;
            int tileX = (local % SOURCE_TILES_PER_AXIS) * SOURCE_TILE_SIZE;
            int tileY = (local / SOURCE_TILES_PER_AXIS) * SOURCE_TILE_SIZE;

            for (int y = 0; y < SOURCE_TILE_SIZE; y++) {
                for (int x = 0; x < SOURCE_TILE_SIZE; x++) {
                    int color = 0xffffffff;
                    if (source != null) {
                        int sourceX = sampleCoord(x, SOURCE_TILE_SIZE, source.getWidth());
                        int sourceY = sampleCoord(y, SOURCE_TILE_SIZE, source.getHeight());
                        color = source.getRGB(sourceX, sourceY);
                    }
                    pixels[pixelIndex(unit, tileX + x, tileY + y)] = color;
                }
            }
        }
    }

    private static int pixelIndex(int unit, int localX, int localY) {
        int unitX = unit % unitColumns;
        int unitY = unit / unitColumns;
        return (unitY * UNIT_SIZE + localY) * width + unitX * UNIT_SIZE + localX;
    }

    private static int divCeil(int value, int divisor) {
        return value == 0 ? 0 : (value + divisor - 1) / divisor;
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

    private static final class MaterialEntry {
        private final Material material;
        private final int rampIndex;

        private MaterialEntry(Material material, int rampIndex) {
            this.material = material;
            this.rampIndex = rampIndex;
        }
    }

    private static final class UploadedMaterialTexture extends AbstractTexture {
        @Nullable
        private int[] pendingPixels;
        private final int width;
        private final int height;

        private UploadedMaterialTexture(int[] pixels, int width, int height) {
            this.pendingPixels = pixels;
            this.width = width;
            this.height = height;
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) {
            if (pendingPixels != null) {
                int textureId = getGlTextureId();
                TextureUtil.allocateTexture(textureId, width, height);
                TextureUtil.uploadTexture(textureId, pendingPixels, width, height);
                pendingPixels = null;
            }
        }
    }

    public static final class Allocation {
        private final int type;
        private final int index;
        private final int sourceIndex;

        private Allocation(int type, int index, int sourceIndex) {
            this.type = type;
            this.index = index;
            this.sourceIndex = sourceIndex;
        }

        public int getType() {
            return type;
        }

        public int getIndex() {
            return index;
        }

        public int getSourceIndex() {
            return sourceIndex;
        }
    }
}
