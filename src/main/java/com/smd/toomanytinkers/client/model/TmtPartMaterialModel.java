package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;
import slimeknights.tconstruct.library.materials.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class TmtPartMaterialModel extends BakedMaterialModel {

    private final IBakedModel baseModel;
    private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;
    private final String baseTexture;
    private final IModelState state;
    private final VertexFormat format;
    private final Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter;
    private final Map<String, IBakedModel> suffixModels = new HashMap<>();
    private final Map<String, TmtResolvedPartModel> resolvedParts = new HashMap<>();

    public TmtPartMaterialModel(IBakedModel base,
                                ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms,
                                String baseTexture,
                                IModelState state,
                                VertexFormat format,
                                Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        super(base, transforms);
        this.baseModel = base;
        this.transforms = transforms;
        this.baseTexture = baseTexture;
        this.state = state;
        this.format = format;
        this.bakedTextureGetter = bakedTextureGetter;
    }

    public IBakedModel getBaseModel() {
        return baseModel;
    }

    public ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> getTransforms() {
        return transforms;
    }

    @Override
    public IBakedModel getModelByIdentifier(String identifier) {
        return getResolvedPart(identifier);
    }

    public TmtResolvedPartModel getResolvedPart(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return new TmtResolvedPartModel(baseModel, transforms, "");
        }
        return resolvedParts.computeIfAbsent(identifier, material ->
                new TmtResolvedPartModel(getGrayMaskModel(material), transforms, material));
    }

    private IBakedModel getGrayMaskModel(String materialId) {
        Material material = TinkerRegistry.getMaterial(materialId);
        if (material.renderInfo == null || material.renderInfo.getTextureSuffix() == null) {
            return baseModel;
        }
        String suffixTexture = baseTexture + "_" + material.renderInfo.getTextureSuffix();
        if (!CustomTextureCreator.exists(suffixTexture)) {
            return baseModel;
        }
        return suffixModels.computeIfAbsent(suffixTexture, texture -> ItemLayerModel.INSTANCE
                .retexture(ImmutableMap.of("layer0", texture))
                .bake(state, format, bakedTextureGetter));
    }
}
