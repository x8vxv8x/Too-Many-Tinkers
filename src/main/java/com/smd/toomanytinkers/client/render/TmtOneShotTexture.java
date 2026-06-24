package com.smd.toomanytinkers.client.render;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;

import javax.annotation.Nullable;

public final class TmtOneShotTexture extends AbstractTexture {

    @Nullable
    private int[] pendingPixels;
    private final int width;
    private final int height;

    public TmtOneShotTexture(int[] pixels, int width, int height) {
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
