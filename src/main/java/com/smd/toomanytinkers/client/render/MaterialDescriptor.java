package com.smd.toomanytinkers.client.render;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

public final class MaterialDescriptor {

    public static final int FLAG_VERTEX_COLOR = 1;
    public static final int FLAG_TEXTURE_SUFFIX = 1 << 1;
    public static final int FLAG_ANIMATED = 1 << 2;

    private final String materialId;
    private final int rampRow;
    private final int animationId;
    private final int flags;
    private final MaterialRenderMode renderMode;
    private final String textureSuffix;
    private final ResourceLocation sourceTexture;

    public MaterialDescriptor(String materialId,
                              int rampRow,
                              int animationId,
                              int flags,
                              MaterialRenderMode renderMode,
                              @Nullable String textureSuffix,
                              @Nullable ResourceLocation sourceTexture) {
        this.materialId = materialId;
        this.rampRow = rampRow;
        this.animationId = animationId;
        this.flags = flags;
        this.renderMode = renderMode;
        this.textureSuffix = textureSuffix;
        this.sourceTexture = sourceTexture;
    }

    public String getMaterialId() {
        return materialId;
    }

    public int getRampRow() {
        return rampRow;
    }

    public int getSourceLayer() {
        return MaterialSourceTextureManager.getSourceLayer(materialId);
    }

    public int getAnimationId() {
        return animationId;
    }

    public int getFlags() {
        return flags;
    }

    public MaterialRenderMode getRenderMode() {
        return renderMode;
    }

    @Nullable
    public String getTextureSuffix() {
        return textureSuffix;
    }

    @Nullable
    public ResourceLocation getSourceTexture() {
        return sourceTexture;
    }

    public boolean hasTextureSuffix() {
        return textureSuffix != null && !textureSuffix.isEmpty();
    }
}
