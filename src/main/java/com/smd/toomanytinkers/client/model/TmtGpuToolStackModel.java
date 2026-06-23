package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.List;

public final class TmtGpuToolStackModel implements TmtLayeredItemModel {

    private static final ItemOverrideList NO_OVERRIDES = new ItemOverrideList(ImmutableList.of());

    private final TmtGpuToolTemplateModel template;
    private final TmtToolRenderDescriptor descriptor;
    @Nullable
    private final ItemStack renderStack;
    @Nullable
    private final World renderWorld;
    @Nullable
    private final EntityLivingBase renderEntity;

    public TmtGpuToolStackModel(TmtGpuToolTemplateModel template, TmtToolRenderDescriptor descriptor) {
        this(template, descriptor, null, null, null);
    }

    private TmtGpuToolStackModel(TmtGpuToolTemplateModel template,
                                 TmtToolRenderDescriptor descriptor,
                                 @Nullable ItemStack renderStack,
                                 @Nullable World renderWorld,
                                 @Nullable EntityLivingBase renderEntity) {
        this.template = template;
        this.descriptor = descriptor;
        this.renderStack = renderStack;
        this.renderWorld = renderWorld;
        this.renderEntity = renderEntity;
    }

    public TmtToolRenderDescriptor getDescriptor() {
        return descriptor;
    }

    public TmtGpuToolStackModel withRenderContext(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        return new TmtGpuToolStackModel(template, descriptor, stack, world, entity);
    }

    @Nullable
    public ItemStack getRenderStack() {
        return renderStack;
    }

    @Nullable
    public World getRenderWorld() {
        return renderWorld;
    }

    @Nullable
    public EntityLivingBase getRenderEntity() {
        return renderEntity;
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
        return NO_OVERRIDES;
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
        return PerspectiveMapWrapper.handlePerspective(this, descriptor.getDefinition().getTransforms(), type);
    }
}
