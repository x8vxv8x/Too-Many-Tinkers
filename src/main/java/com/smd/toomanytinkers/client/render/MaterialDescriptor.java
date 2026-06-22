package com.smd.toomanytinkers.client.render;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

public final class MaterialDescriptor {

    public static final int TYPE_DIRECT = 0;
    public static final int TYPE_SOLID = 1;
    public static final int TYPE_RAMP = 2;
    public static final int TYPE_TEXTURE = 3;

    public static final int FLAG_VERTEX_COLOR = 1;
    public static final int FLAG_TEXTURE_SUFFIX = 1 << 1;
    public static final int FLAG_ANIMATED = 1 << 2;
    public static final int FLAG_SOURCE_TEXTURE = 1 << 3;

    private final String materialId;
    private final int materialType;
    private final int materialIndex;
    private final int animationId;
    private final int flags;
    private final MaterialRenderMode renderMode;
    private final String textureSuffix;
    private final ResourceLocation sourceTexture;

    public MaterialDescriptor(String materialId,
                              int materialType,
                              int materialIndex,
                              int animationId,
                              int flags,
                              MaterialRenderMode renderMode,
                              @Nullable String textureSuffix,
                              @Nullable ResourceLocation sourceTexture) {
        this.materialId = materialId;
        this.materialType = materialType;
        this.materialIndex = materialIndex;
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
        return materialIndex;
    }

    public int getMapSlot() {
        return materialIndex;
    }

    public int getMaterialType() {
        return materialType;
    }

    public int getMaterialIndex() {
        return materialIndex;
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
