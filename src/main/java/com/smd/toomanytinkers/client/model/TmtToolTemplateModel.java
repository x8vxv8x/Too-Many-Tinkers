package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
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
            return owner.resolve(stack, world, entity);
        }
    }
}
