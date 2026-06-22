package com.smd.toomanytinkers.client.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.smd.toomanytinkers.client.render.TmtRenderStats;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import org.apache.commons.lang3.tuple.Pair;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.WeakHashMap;

public final class TmtGpuToolTemplateModel implements TmtGpuItemModel {

    private static final int DESCRIPTOR_CACHE_SIZE = 1024;
    private static final Set<Overrides> OVERRIDE_CACHES = Collections.newSetFromMap(new WeakHashMap<>());

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

    public static void invalidateCaches() {
        synchronized (OVERRIDE_CACHES) {
            for (Overrides overrides : OVERRIDE_CACHES) {
                overrides.cache.invalidateAll();
            }
        }
        TmtRenderStats.setDescriptorCacheSize(0);
        TmtRenderStats.descriptorCacheInvalidated();
    }

    private static void updateCacheStats() {
        long size = 0;
        synchronized (OVERRIDE_CACHES) {
            for (Overrides overrides : OVERRIDE_CACHES) {
                overrides.cache.cleanUp();
                size += overrides.cache.size();
            }
        }
        TmtRenderStats.setDescriptorCacheSize((int) Math.min(Integer.MAX_VALUE, size));
    }

    private static final class Overrides extends ItemOverrideList {
        private final Cache<CacheKey, TmtGpuToolStackModel> cache = CacheBuilder.newBuilder()
                .maximumSize(DESCRIPTOR_CACHE_SIZE)
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .build();
        private final TmtGpuToolTemplateModel owner;

        private Overrides(TmtGpuToolTemplateModel owner) {
            super(ImmutableList.of());
            this.owner = owner;
            synchronized (OVERRIDE_CACHES) {
                OVERRIDE_CACHES.add(this);
            }
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
            TmtGpuToolStackModel cached = cache.getIfPresent(key);
            if (cached != null) {
                TmtRenderStats.descriptorCacheHit();
                updateCacheStats();
                return cached;
            }
            TmtRenderStats.descriptorCacheMiss();
            TmtGpuToolStackModel created = new TmtGpuToolStackModel(owner, TmtToolRenderDescriptor.create(resolvedDefinition, stack));
            cache.put(key, created);
            updateCacheStats();
            return created;
        }
    }

    private static final class CacheKey {
        private final TmtGpuToolTemplateModel owner;
        private final int overrideSignature;
        private final boolean broken;
        private final String[] materials;
        private final String[] modifiers;
        private final int hash;

        private CacheKey(TmtGpuToolTemplateModel owner, int overrideSignature, ItemStack stack) {
            this.owner = owner;
            this.overrideSignature = overrideSignature;
            this.broken = ToolHelper.isBroken(stack);
            this.materials = copyStrings(TagUtil.getBaseMaterialsTagList(stack));
            this.modifiers = copyStrings(TagUtil.getBaseModifiersTagList(stack));
            this.hash = computeHash();
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
            return owner == other.owner
                    && overrideSignature == other.overrideSignature
                    && broken == other.broken
                    && Arrays.equals(materials, other.materials)
                    && Arrays.equals(modifiers, other.modifiers);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        private int computeHash() {
            int result = System.identityHashCode(owner);
            result = 31 * result + overrideSignature;
            result = 31 * result + Boolean.hashCode(broken);
            result = 31 * result + Arrays.hashCode(materials);
            result = 31 * result + Arrays.hashCode(modifiers);
            return result;
        }

        private static String[] copyStrings(NBTTagList list) {
            String[] out = new String[list.tagCount()];
            for (int i = 0; i < out.length; i++) {
                out[i] = list.getStringTagAt(i);
            }
            return out;
        }
    }
}
