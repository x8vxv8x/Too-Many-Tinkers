package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.smd.toomanytinkers.client.render.MaterialDescriptor;
import com.smd.toomanytinkers.client.render.MaterialDescriptorRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.List;

public class TmtResolvedToolModel implements TmtGpuItemModel {

    public static class PartInstance {
        private final IBakedModel baseModel;
        private final String materialId;
        private final MaterialDescriptor materialDescriptor;

        public PartInstance(IBakedModel baseModel, String materialId) {
            this.baseModel = baseModel;
            this.materialId = materialId;
            this.materialDescriptor = MaterialDescriptorRegistry.get(materialId);
        }

        public IBakedModel getBaseModel() {
            return baseModel;
        }

        public String getMaterialId() {
            return materialId;
        }

        public MaterialDescriptor getMaterialDescriptor() {
            return materialDescriptor;
        }
    }

    private static final ItemOverrideList NO_OVERRIDES = new ItemOverrideList(ImmutableList.of());

    private final IBakedModel parent;
    private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;
    private final ImmutableList<PartInstance> parts;
    private final ImmutableList<IBakedModel> vanillaModels;

    public TmtResolvedToolModel(IBakedModel parent,
                                ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms,
                                ImmutableList<PartInstance> parts,
                                ImmutableList<IBakedModel> vanillaModels) {
        this.parent = parent;
        this.transforms = transforms;
        this.parts = parts;
        this.vanillaModels = vanillaModels;
    }

    public ImmutableList<PartInstance> getParts() {
        return parts;
    }

    public ImmutableList<IBakedModel> getVanillaModels() {
        return vanillaModels;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        return ImmutableList.of();
    }

    @Override
    public boolean isAmbientOcclusion() {
        return parent.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return parent.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return parent.getParticleTexture();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return NO_OVERRIDES;
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
        return PerspectiveMapWrapper.handlePerspective(this, transforms, type);
    }
}
