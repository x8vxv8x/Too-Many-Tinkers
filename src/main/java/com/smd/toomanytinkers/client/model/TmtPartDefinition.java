package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.client.model.IPatternOffset;
import slimeknights.tconstruct.library.client.model.MaterialModel;

import java.util.Collection;

public final class TmtPartDefinition {

    private final ImmutableList<ResourceLocation> textures;
    private final int offsetX;
    private final int offsetY;
    private final float rotationDegrees;
    private final float zBias;

    public TmtPartDefinition(Collection<ResourceLocation> textures, int offsetX, int offsetY, float rotationDegrees) {
        this(textures, offsetX, offsetY, rotationDegrees, 0f);
    }

    private TmtPartDefinition(Collection<ResourceLocation> textures, int offsetX, int offsetY, float rotationDegrees, float zBias) {
        this.textures = ImmutableList.copyOf(textures);
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.rotationDegrees = rotationDegrees;
        this.zBias = zBias;
    }

    public static TmtPartDefinition fromMaterialModel(MaterialModel model, float rotationDegrees) {
        int x = 0;
        int y = 0;
        if (model instanceof IPatternOffset) {
            x = ((IPatternOffset) model).getXOffset();
            y = ((IPatternOffset) model).getYOffset();
        }
        return new TmtPartDefinition(model.getTextures(), x, y, rotationDegrees);
    }

    public static TmtPartDefinition singleTexture(ResourceLocation texture, float zBias) {
        return new TmtPartDefinition(ImmutableList.of(texture), 0, 0, 0f, zBias);
    }

    public ImmutableList<ResourceLocation> getTextures() {
        return textures;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public float getRotationDegrees() {
        return rotationDegrees;
    }

    public float getZBias() {
        return zBias;
    }
}
