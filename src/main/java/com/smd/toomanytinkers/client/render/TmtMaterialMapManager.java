package com.smd.toomanytinkers.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.materials.Material;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class TmtMaterialMapManager {

    public static final int WIDTH = 256;
    public static final int UNIT_SIZE = 256;
    public static final int SOURCE_TILE_SIZE = 16;

    private static final String DYNAMIC_NAME = "tmt_material_map";

    private static int expectedMaterials;
    private static int solidRows;
    private static final List<Integer> solidPixels = new ArrayList<>();
    private static final List<int[]> rampRows = new ArrayList<>();
    private static final List<int[]> textureUnits = new ArrayList<>();

    private static int[] pixels = new int[WIDTH];
    private static int height = 1;
    private static int textureBaseY;
    private static DynamicTexture texture;
    private static ResourceLocation textureLocation;
    private static boolean dirty = true;

    private TmtMaterialMapManager() {
    }

    public static void rebuildStart(int expectedMaterialCount) {
        expectedMaterials = Math.max(1, expectedMaterialCount);
        solidRows = Math.max(1, (expectedMaterials + WIDTH - 1) / WIDTH);
        solidPixels.clear();
        rampRows.clear();
        textureUnits.clear();
        pixels = new int[WIDTH * solidRows];
        java.util.Arrays.fill(pixels, 0xffffffff);
        height = solidRows;
        textureBaseY = solidRows;
        dirty = true;
    }

    public static Allocation addMaterial(Material material, @Nullable ResourceLocation sourceTexture) {
        if (sourceTexture != null) {
            int index = textureUnits.size();
            textureUnits.add(buildTextureUnit(material, sourceTexture));
            dirty = true;
            return new Allocation(MaterialDescriptor.TYPE_TEXTURE, index);
        }
        if (MaterialColorEvaluator.isSolid(material)) {
            int index = solidPixels.size();
            solidPixels.add(MaterialColorEvaluator.evaluate(material, 1f));
            dirty = true;
            return new Allocation(MaterialDescriptor.TYPE_SOLID, index);
        }
        int index = rampRows.size();
        rampRows.add(buildRamp(material));
        dirty = true;
        return new Allocation(MaterialDescriptor.TYPE_RAMP, index);
    }

    public static ResourceLocation getTextureLocation() {
        ensureTexture();
        return textureLocation;
    }

    public static int getHeight() {
        ensureTexture();
        return height;
    }

    public static int getSolidRows() {
        ensureTexture();
        return solidRows;
    }

    public static int getTextureBaseY() {
        ensureTexture();
        return textureBaseY;
    }

    public static int getSlotCount() {
        return solidPixels.size() + rampRows.size() + textureUnits.size();
    }

    private static void ensureTexture() {
        if (!dirty && textureLocation != null) {
            return;
        }

        composePixels();
        texture = new DynamicTexture(WIDTH, height);
        System.arraycopy(pixels, 0, texture.getTextureData(), 0, Math.min(pixels.length, texture.getTextureData().length));

        Minecraft mc = Minecraft.getMinecraft();
        if (textureLocation != null) {
            mc.getTextureManager().deleteTexture(textureLocation);
        }
        textureLocation = mc.getTextureManager().getDynamicTextureLocation(DYNAMIC_NAME, texture);
        texture.updateDynamicTexture();
        dirty = false;
    }

    private static void composePixels() {
        int rampBaseY = solidRows;
        textureBaseY = rampBaseY + rampRows.size();
        height = Math.max(1, textureBaseY + textureUnits.size() * UNIT_SIZE);
        pixels = new int[WIDTH * height];
        java.util.Arrays.fill(pixels, 0xffffffff);

        for (int i = 0; i < solidPixels.size(); i++) {
            pixels[i] = solidPixels.get(i);
        }
        for (int i = 0; i < rampRows.size(); i++) {
            System.arraycopy(rampRows.get(i), 0, pixels, (rampBaseY + i) * WIDTH, WIDTH);
        }
        for (int i = 0; i < textureUnits.size(); i++) {
            System.arraycopy(textureUnits.get(i), 0, pixels, (textureBaseY + i * UNIT_SIZE) * WIDTH, WIDTH * UNIT_SIZE);
        }
    }

    private static int[] buildRamp(Material material) {
        int[] row = new int[WIDTH];
        for (int gray = 0; gray < WIDTH; gray++) {
            row[gray] = MaterialColorEvaluator.evaluate(material, gray / 255f);
        }
        return row;
    }

    private static int[] buildTextureUnit(Material material, ResourceLocation sourceTexture) {
        int[] unit = new int[WIDTH * UNIT_SIZE];
        BufferedImage source = readTexture(sourceTexture);
        for (int gray = 0; gray < WIDTH; gray++) {
            int rampColor = MaterialColorEvaluator.evaluate(material, gray / 255f);
            int tileX = (gray / SOURCE_TILE_SIZE) * SOURCE_TILE_SIZE;
            int tileY = (gray % SOURCE_TILE_SIZE) * SOURCE_TILE_SIZE;
            for (int y = 0; y < SOURCE_TILE_SIZE; y++) {
                for (int x = 0; x < SOURCE_TILE_SIZE; x++) {
                    int sourceColor = 0xffffffff;
                    if (source != null) {
                        int sourceX = sampleCoord(x, SOURCE_TILE_SIZE, source.getWidth());
                        int sourceY = sampleCoord(y, SOURCE_TILE_SIZE, source.getHeight());
                        sourceColor = source.getRGB(sourceX, sourceY);
                    }
                    unit[(tileY + y) * WIDTH + tileX + x] = multiply(rampColor, sourceColor);
                }
            }
        }
        return unit;
    }

    private static int multiply(int first, int second) {
        int a = channel(first, 24) * channel(second, 24) / 255;
        int r = channel(first, 16) * channel(second, 16) / 255;
        int g = channel(first, 8) * channel(second, 8) / 255;
        int b = channel(first, 0) * channel(second, 0) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int argb, int shift) {
        return (argb >>> shift) & 0xff;
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

    public static final class Allocation {
        private final int type;
        private final int index;

        private Allocation(int type, int index) {
            this.type = type;
            this.index = index;
        }

        public int getType() {
            return type;
        }

        public int getIndex() {
            return index;
        }
    }
}
