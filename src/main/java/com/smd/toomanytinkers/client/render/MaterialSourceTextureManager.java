package com.smd.toomanytinkers.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialSourceTextureManager {

    private static final int TILE_SIZE = 16;
    private static final String DYNAMIC_NAME = "tmt_material_source";
    private static final Map<String, ResourceLocation> REGISTERED_SOURCES = new LinkedHashMap<>();
    private static final Map<String, Integer> SOURCE_LAYERS = new java.util.HashMap<>();

    private static DynamicTexture texture;
    private static ResourceLocation textureLocation;
    private static int height = TILE_SIZE;
    private static boolean dirty = true;

    private MaterialSourceTextureManager() {
    }

    public static void registerSource(String materialId, ResourceLocation texture) {
        REGISTERED_SOURCES.put(materialId, texture);
        dirty = true;
    }

    public static int getSourceLayer(String materialId) {
        ensureTexture();
        Integer layer = SOURCE_LAYERS.get(materialId);
        return layer == null ? -1 : layer;
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

        SOURCE_LAYERS.clear();
        int layers = Math.max(1, REGISTERED_SOURCES.size());
        height = layers * TILE_SIZE;
        texture = new DynamicTexture(TILE_SIZE, height);
        int[] data = texture.getTextureData();
        java.util.Arrays.fill(data, 0xffffffff);

        int layer = 0;
        TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
        for (Map.Entry<String, ResourceLocation> entry : REGISTERED_SOURCES.entrySet()) {
            TextureAtlasSprite sprite = map.getTextureExtry(entry.getValue().toString());
            if (sprite == null || sprite.getFrameCount() <= 0) {
                continue;
            }
            copySpriteLayer(data, layer, sprite);
            SOURCE_LAYERS.put(entry.getKey(), layer);
            layer++;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (textureLocation != null) {
            mc.getTextureManager().deleteTexture(textureLocation);
        }
        textureLocation = mc.getTextureManager().getDynamicTextureLocation(DYNAMIC_NAME, texture);
        texture.updateDynamicTexture();
        dirty = false;
    }

    private static void copySpriteLayer(int[] data, int layer, TextureAtlasSprite sprite) {
        int[][] frames = sprite.getFrameTextureData(0);
        if (frames == null || frames.length == 0 || frames[0] == null) {
            return;
        }
        int[] source = frames[0];
        int width = sprite.getIconWidth();
        int height = sprite.getIconHeight();
        for (int y = 0; y < TILE_SIZE; y++) {
            int sourceY = (int) ((y + 0.5f) * height / TILE_SIZE);
            for (int x = 0; x < TILE_SIZE; x++) {
                int sourceX = (int) ((x + 0.5f) * width / TILE_SIZE);
                data[(layer * TILE_SIZE + y) * TILE_SIZE + x] =
                        source[Math.min(source.length - 1, sourceY * width + sourceX)];
            }
        }
    }

    @Mod.EventBusSubscriber(modid = "toomanytinkers", value = Side.CLIENT)
    public static class Events {
        @SubscribeEvent
        public static void onTextureStitch(TextureStitchEvent.Post event) {
            markDirty();
        }
    }
}
