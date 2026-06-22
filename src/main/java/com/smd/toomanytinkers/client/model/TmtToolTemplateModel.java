package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.smd.toomanytinkers.client.render.TmtRenderStats;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.model.TRSRTransformation;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;
import slimeknights.tconstruct.library.client.model.BakedToolModel;
import slimeknights.tconstruct.library.client.model.BakedToolModelOverride;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TmtToolTemplateModel extends BakedToolModel {

    private final ItemOverrideList overridesList = new TmtToolOverrideList(this);

    public TmtToolTemplateModel(IBakedModel parent,
                                BakedMaterialModel[] parts,
                                BakedMaterialModel[] brokenParts,
                                Map<String, IBakedModel> modifierParts,
                                ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transform,
                                ImmutableList<BakedToolModelOverride> overrides) {
        super(parent, parts, brokenParts, modifierParts, transform, overrides);
    }

    @Nonnull
    @Override
    public ItemOverrideList getOverrides() {
        return overridesList;
    }

    protected TmtToolTemplateModel resolveBase(ItemStack stack, World world, EntityLivingBase entity) {
        TmtToolTemplateModel original = this;
        for (BakedToolModelOverride override : original.overrides) {
            if (override.matches(stack, world, entity) && override.bakedToolModel instanceof TmtToolTemplateModel) {
                original = (TmtToolTemplateModel) override.bakedToolModel;
            }
        }
        return original;
    }

    protected void addExtraModels(ItemStack stack, World world, EntityLivingBase entity,
                                  ImmutableList.Builder<IBakedModel> models) {
    }

    protected CacheKey createDescriptorCacheKey(ItemStack stack, World world, EntityLivingBase entity) {
        return new CacheKey(this, stack);
    }

    protected TmtResolvedToolModel resolve(ItemStack stack, World world, EntityLivingBase entity) {
        TmtToolTemplateModel original = resolveBase(stack, world, entity);
        ImmutableList.Builder<TmtResolvedToolModel.PartInstance> partBuilder = ImmutableList.builder();
        ImmutableList.Builder<IBakedModel> vanillaBuilder = ImmutableList.builder();

        NBTTagList materials = TagUtil.getBaseMaterialsTagList(stack);
        boolean broken = ToolHelper.isBroken(stack);
        for (int i = 0; i < original.parts.length; i++) {
            String materialId = i < materials.tagCount() ? materials.getStringTagAt(i) : "";
            BakedMaterialModel materialModel = broken && original.brokenParts[i] != null
                    ? original.brokenParts[i]
                    : original.parts[i];
            if (materialModel instanceof TmtPartMaterialModel) {
                TmtResolvedPartModel resolvedPart = ((TmtPartMaterialModel) materialModel).getResolvedPart(materialId);
                partBuilder.add(new TmtResolvedToolModel.PartInstance(
                        resolvedPart.getBaseModel(), resolvedPart.getMaterialId()));
            } else if (materialModel != null) {
                vanillaBuilder.add(materialModel.getModelByIdentifier(materialId));
            }
        }

        addModifierModels(stack, original, vanillaBuilder);
        original.addExtraModels(stack, world, entity, vanillaBuilder);

        return new TmtResolvedToolModel(original, original.transforms, partBuilder.build(), vanillaBuilder.build());
    }

    private static void addModifierModels(ItemStack stack, TmtToolTemplateModel original,
                                          ImmutableList.Builder<IBakedModel> vanillaBuilder) {
        boolean incognito = false;
        NBTTagList modifiers = TagUtil.getBaseModifiersTagList(stack);
        if (modifiers.toString().contains("incognito")) {
            incognito = true;
        }
        for (int i = 0; i < modifiers.tagCount(); i++) {
            String modId = modifiers.getStringTagAt(i);
            if (incognito && !modId.equals("incognito") && !Arrays.asList(Config.incognitoModBlacklist).contains(modId)) {
                TConstruct.log.debug("Applying incognito for modifier {}", modId);
                continue;
            }
            IBakedModel modModel = original.modifierParts.get(modId);
            if (modModel != null) {
                vanillaBuilder.add(modModel);
            }
        }
    }

    private static class TmtToolOverrideList extends ItemOverrideList {
        private final Cache<CacheKey, IBakedModel> descriptorCache = CacheBuilder.newBuilder()
                .maximumSize(4096)
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .build();
        private final TmtToolTemplateModel owner;

        private TmtToolOverrideList(TmtToolTemplateModel owner) {
            super(ImmutableList.of());
            this.owner = owner;
        }

        @Nonnull
        @Override
        public IBakedModel handleItemState(@Nonnull IBakedModel originalModel, ItemStack stack,
                                           World world, EntityLivingBase entity) {
            if (TagUtil.getBaseTag(stack).isEmpty()) {
                return originalModel;
            }
            TmtToolTemplateModel resolvedBase = owner.resolveBase(stack, world, entity);
            CacheKey key = resolvedBase.createDescriptorCacheKey(stack, world, entity);
            IBakedModel cached = descriptorCache.getIfPresent(key);
            if (cached != null) {
                TmtRenderStats.descriptorCacheHit();
                return cached;
            }
            try {
                return descriptorCache.get(key, () -> {
                    TmtRenderStats.descriptorCacheMiss();
                    return resolvedBase.resolveResolvedBase(stack, world, entity);
                });
            } catch (ExecutionException e) {
                return owner.resolve(stack, world, entity);
            }
        }
    }

    private TmtResolvedToolModel resolveResolvedBase(ItemStack stack, World world, EntityLivingBase entity) {
        ImmutableList.Builder<TmtResolvedToolModel.PartInstance> partBuilder = ImmutableList.builder();
        ImmutableList.Builder<IBakedModel> vanillaBuilder = ImmutableList.builder();

        NBTTagList materials = TagUtil.getBaseMaterialsTagList(stack);
        boolean broken = ToolHelper.isBroken(stack);
        for (int i = 0; i < parts.length; i++) {
            String materialId = i < materials.tagCount() ? materials.getStringTagAt(i) : "";
            BakedMaterialModel materialModel = broken && brokenParts[i] != null
                    ? brokenParts[i]
                    : parts[i];
            if (materialModel instanceof TmtPartMaterialModel) {
                TmtResolvedPartModel resolvedPart = ((TmtPartMaterialModel) materialModel).getResolvedPart(materialId);
                partBuilder.add(new TmtResolvedToolModel.PartInstance(
                        resolvedPart.getBaseModel(), resolvedPart.getMaterialId()));
            } else if (materialModel != null) {
                vanillaBuilder.add(materialModel.getModelByIdentifier(materialId));
            }
        }

        addModifierModels(stack, this, vanillaBuilder);
        addExtraModels(stack, world, entity, vanillaBuilder);

        return new TmtResolvedToolModel(this, transforms, partBuilder.build(), vanillaBuilder.build());
    }

    protected static class CacheKey {
        private final TmtToolTemplateModel baseModel;
        private final NBTTagCompound data;

        protected CacheKey(TmtToolTemplateModel baseModel, ItemStack stack) {
            this.baseModel = baseModel;
            this.data = TagUtil.getTagSafe(stack).copy();
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
            return baseModel == other.baseModel && data.equals(other.data);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(baseModel) + data.hashCode();
        }
    }
}
