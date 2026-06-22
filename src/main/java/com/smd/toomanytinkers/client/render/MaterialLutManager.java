package com.smd.toomanytinkers.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.client.MaterialRenderInfo;
import slimeknights.tconstruct.library.materials.Material;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MaterialLutManager {

    private static final int WIDTH = 256;
    private static final String DYNAMIC_NAME = "tmt_material_lut";
    private static final Map<String, Integer> MATERIAL_ROWS = new HashMap<>();

    private static DynamicTexture texture;
    private static ResourceLocation textureLocation;
    private static int height = 1;
    private static boolean dirty = true;

    private MaterialLutManager() {
    }

    public static int getMaterialRow(String materialId) {
        ensureTexture();
        Integer row = MATERIAL_ROWS.get(materialId);
        return row == null ? -1 : row;
    }

    public static int getHeight() {
        ensureTexture();
        return height;
    }

    public static ResourceLocation getTextureLocation() {
        ensureTexture();
        return textureLocation;
    }

    public static void markDirty() {
        dirty = true;
    }

    private static void ensureTexture() {
        if (!dirty && textureLocation != null) {
            return;
        }

        MATERIAL_ROWS.clear();
        List<Material> materials = new ArrayList<>(TinkerRegistry.getAllMaterials());
        height = Math.max(1, materials.size());
        texture = new DynamicTexture(WIDTH, height);
        int[] data = texture.getTextureData();

        for (int row = 0; row < materials.size(); row++) {
            Material material = materials.get(row);
            MATERIAL_ROWS.put(material.identifier, row);
            fillMaterialRow(data, row, material);
        }
        if (materials.isEmpty()) {
            for (int x = 0; x < WIDTH; x++) {
                data[x] = 0xffffffff;
            }
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (textureLocation != null) {
            mc.getTextureManager().deleteTexture(textureLocation);
        }
        textureLocation = mc.getTextureManager().getDynamicTextureLocation(DYNAMIC_NAME, texture);
        texture.updateDynamicTexture();
        dirty = false;
    }

    private static void fillMaterialRow(int[] data, int row, Material material) {
        for (int gray = 0; gray < WIDTH; gray++) {
            float f = gray / 255f;
            data[row * WIDTH + gray] = evaluateMaterialColor(material, f);
        }
    }

    private static int evaluateMaterialColor(Material material, float gray) {
        MaterialRenderInfo info = material.renderInfo;
        int grayByte = clamp255((int) (gray * 255f));
        if (info != null) {
            int low = readInt(info, "low", Integer.MIN_VALUE);
            int mid = readInt(info, "mid", Integer.MIN_VALUE);
            int high = readInt(info, "high", Integer.MIN_VALUE);
            if (low != Integer.MIN_VALUE && mid != Integer.MIN_VALUE && high != Integer.MIN_VALUE) {
                boolean inverse = info.getClass().getName().contains("Inverse");
                int color = grayByte < 50 ? low : grayByte > 217 ? high : mid;
                return inverse ? inverseColorByGray(color, grayByte) : multiplyColorByGray(color, grayByte);
            }

            int metalColor = readInt(info, "color", Integer.MIN_VALUE);
            if (metalColor != Integer.MIN_VALUE) {
                float shininess = readFloat(info, "shinyness", 0.35f);
                float brightness = readFloat(info, "brightness", 0.35f);
                float hueShift = readFloat(info, "hueshift", 0.08f);
                return metalColor(metalColor, grayByte, shininess, brightness, hueShift);
            }

            if (info.useVertexColoring()) {
                return multiplyColorByGray(info.getVertexColor(), grayByte);
            }
        }
        return multiplyColorByGray(material.materialTextColor, grayByte);
    }

    private static int getRepresentativeColor(Material material) {
        MaterialRenderInfo info = material.renderInfo;
        if (info == null) {
            return material.materialTextColor | 0xff000000;
        }
        if (info.useVertexColoring()) {
            return info.getVertexColor() | 0xff000000;
        }
        int color = readInt(info, "color", Integer.MIN_VALUE);
        if (color != Integer.MIN_VALUE) {
            return color | 0xff000000;
        }
        color = readInt(info, "mid", Integer.MIN_VALUE);
        if (color != Integer.MIN_VALUE) {
            return color | 0xff000000;
        }
        return material.materialTextColor | 0xff000000;
    }

    private static int multiplyColorByGray(int color, int gray) {
        int r = mult((color >> 16) & 0xff, gray);
        int g = mult((color >> 8) & 0xff, gray);
        int b = mult(color & 0xff, gray);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static int inverseColorByGray(int color, int gray) {
        int r = ~mult((color >> 16) & 0xff, gray) & 0xff;
        int g = ~mult((color >> 8) & 0xff, gray) & 0xff;
        int b = ~mult(color & 0xff, gray) & 0xff;
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static int metalColor(int color, int gray, float shininess, float brightness, float hueShift) {
        int r = mult((color >> 16) & 0xff, gray);
        int g = mult((color >> 8) & 0xff, gray);
        int b = mult(color & 0xff, gray);
        float l = gray / 255f;
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        hsb[0] -= (0.5f - l * l) * hueShift;
        if (l > 0.9f) {
            hsb[1] = clamp01(hsb[1] - (l * l * shininess));
        }
        if (l > 0.8f) {
            hsb[2] = clamp01(hsb[2] + l * l * brightness);
        }
        return 0xff000000 | (java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0x00ffffff);
    }

    private static int mult(int c1, int c2) {
        return clamp255((int) (c1 * (c2 / 255f)));
    }

    private static int clamp255(int value) {
        return Math.min(255, Math.max(0, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int readInt(Object instance, String name, int fallback) {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(instance);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return fallback;
    }

    private static float readFloat(Object instance, String name, float fallback) {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getFloat(instance);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return fallback;
    }

    @Mod.EventBusSubscriber(modid = "toomanytinkers", value = Side.CLIENT)
    public static class Events {
        @SubscribeEvent
        public static void onTextureStitch(TextureStitchEvent.Post event) {
            markDirty();
        }
    }
}
