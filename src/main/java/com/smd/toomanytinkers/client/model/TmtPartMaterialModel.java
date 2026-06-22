package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;

import java.util.function.Function;

public class TmtPartMaterialModel extends BakedMaterialModel {

    private final IBakedModel baseModel;
    private final TmtPartDefinition definition;
    private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;

    public TmtPartMaterialModel(IBakedModel base,
                                TmtPartDefinition definition,
                                ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms,
                                String baseTexture,
                                IModelState state,
                                VertexFormat format,
                                Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        super(base, transforms);
        this.baseModel = base;
        this.definition = definition;
        this.transforms = transforms;
    }

    public IBakedModel getBaseModel() {
        return baseModel;
    }

    public ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> getTransforms() {
        return transforms;
    }

    @Override
    public IBakedModel getModelByIdentifier(String identifier) {
        return new TmtGpuPartStackModel(baseModel, transforms, definition, identifier);
    }
}
