package com.smd.toomanytinkers.client.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import org.apache.commons.lang3.tuple.Pair;
import slimeknights.tconstruct.library.utils.TagUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class TmtGpuToolTemplateModel implements TmtGpuItemModel {

    private final TmtToolDefinition definition;
    private final ItemOverrideList overrides = new Overrides(this);

    public TmtGpuToolTemplateModel(TmtToolDefinition definition) {
        this.definition = definition;
    }

    public TmtToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        return ImmutableList.of();
    }

    @Override
    public boolean isAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return overrides;
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
        return PerspectiveMapWrapper.handlePerspective(this, definition.getTransforms(), type);
    }

    private static final class Overrides extends ItemOverrideList {
        private final Cache<CacheKey, TmtGpuToolStackModel> cache = CacheBuilder.newBuilder()
                .maximumSize(4096)
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .build();
        private final TmtGpuToolTemplateModel owner;

        private Overrides(TmtGpuToolTemplateModel owner) {
            super(ImmutableList.of());
            this.owner = owner;
        }

        @Nonnull
        @Override
        public TmtGpuItemModel handleItemState(@Nonnull net.minecraft.client.renderer.block.model.IBakedModel originalModel,
                                               ItemStack stack,
                                               net.minecraft.world.World world,
                                               net.minecraft.entity.EntityLivingBase entity) {
            TmtToolDefinition.Resolved resolved = owner.definition.resolve(stack, world, entity);
            TmtToolDefinition resolvedDefinition = resolved.getDefinition();
            CacheKey key = new CacheKey(owner, resolved.getSignature(), stack);
            try {
                return cache.get(key, () -> new TmtGpuToolStackModel(owner, TmtToolRenderDescriptor.create(resolvedDefinition, stack)));
            } catch (ExecutionException e) {
                return new TmtGpuToolStackModel(owner, TmtToolRenderDescriptor.create(resolvedDefinition, stack));
            }
        }
    }

    private static final class CacheKey {
        private final TmtGpuToolTemplateModel owner;
        private final int overrideSignature;
        private final NBTTagCompound tag;

        private CacheKey(TmtGpuToolTemplateModel owner, int overrideSignature, ItemStack stack) {
            this.owner = owner;
            this.overrideSignature = overrideSignature;
            this.tag = TagUtil.getTagSafe(stack).copy();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CacheKey)) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            return owner == other.owner && overrideSignature == other.overrideSignature && tag.equals(other.tag);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(owner);
            result = 31 * result + overrideSignature;
            result = 31 * result + tag.hashCode();
            return result;
        }
    }
}
