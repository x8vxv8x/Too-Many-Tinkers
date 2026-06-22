package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.List;

public final class TmtGpuToolStackModel implements TmtLayeredItemModel {

    private final TmtGpuToolTemplateModel template;
    private final TmtToolRenderDescriptor descriptor;

    public TmtGpuToolStackModel(TmtGpuToolTemplateModel template, TmtToolRenderDescriptor descriptor) {
        this.template = template;
        this.descriptor = descriptor;
    }

    public TmtToolRenderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public ImmutableList<TmtToolRenderDescriptor.Layer> getLayers() {
        return descriptor.getLayers();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        return ImmutableList.of();
    }

    @Override
    public boolean isAmbientOcclusion() {
        return template.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return template.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return template.getParticleTexture();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return new ItemOverrideList(ImmutableList.of());
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
        return PerspectiveMapWrapper.handlePerspective(this, descriptor.getDefinition().getTransforms(), type);
    }
}
