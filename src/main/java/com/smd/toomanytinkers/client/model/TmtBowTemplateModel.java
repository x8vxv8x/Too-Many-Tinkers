package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.model.TRSRTransformation;
import slimeknights.mantle.client.model.TRSRBakedModel;
import slimeknights.tconstruct.library.client.model.BakedBowModel;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;
import slimeknights.tconstruct.library.client.model.BakedToolModelOverride;
import slimeknights.tconstruct.library.client.model.ModelHelper;
import slimeknights.tconstruct.library.client.model.format.AmmoPosition;
import slimeknights.tconstruct.library.tools.IAmmoUser;
import slimeknights.tconstruct.library.utils.TagUtil;

import java.util.Map;

public class TmtBowTemplateModel extends TmtToolTemplateModel {

    private final AmmoPosition ammoPosition;

    public TmtBowTemplateModel(IBakedModel parent,
                               BakedMaterialModel[] parts,
                               BakedMaterialModel[] brokenParts,
                               Map<String, IBakedModel> modifierParts,
                               ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transform,
                               ImmutableList<BakedToolModelOverride> overrides,
                               AmmoPosition ammoPosition) {
        super(parent, parts, brokenParts, modifierParts, transform, overrides);
        this.ammoPosition = ammoPosition;
    }

    @Override
    protected void addExtraModels(ItemStack stack, World world, EntityLivingBase entity,
                                  ImmutableList.Builder<IBakedModel> models) {
        if (stack.getItem() instanceof IAmmoUser) {
            ItemStack ammo = ((IAmmoUser) stack.getItem()).getAmmoToRender(stack, entity);
            if (!ammo.isEmpty()) {
                IBakedModel ammoModel = ModelHelper.getBakedModelForItem(ammo, world, entity);
                models.add(new TRSRBakedModel(ammoModel,
                        ammoPosition.pos[0], ammoPosition.pos[1], ammoPosition.pos[2],
                        (ammoPosition.rot[0] / 180f) * (float) Math.PI,
                        (ammoPosition.rot[1] / 180f) * (float) Math.PI,
                        (ammoPosition.rot[2] / 180f) * (float) Math.PI,
                        1f));
            }
        }
    }

    @Override
    protected CacheKey createDescriptorCacheKey(ItemStack stack, World world, EntityLivingBase entity) {
        if (stack.getItem() instanceof IAmmoUser) {
            ItemStack ammo = ((IAmmoUser) stack.getItem()).getAmmoToRender(stack, entity);
            if (!ammo.isEmpty()) {
                return new AmmoCacheKey(this, stack, ammo);
            }
        }
        return super.createDescriptorCacheKey(stack, world, entity);
    }

    private static class AmmoCacheKey extends CacheKey {
        private final Item ammoItem;
        private final int ammoMeta;
        private final NBTTagCompound ammoData;

        private AmmoCacheKey(TmtToolTemplateModel baseModel, ItemStack stack, ItemStack ammo) {
            super(baseModel, stack);
            this.ammoItem = ammo.getItem();
            this.ammoMeta = ammo.getMetadata();
            this.ammoData = TagUtil.getTagSafe(ammo).copy();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AmmoCacheKey) || !super.equals(obj)) {
                return false;
            }
            AmmoCacheKey other = (AmmoCacheKey) obj;
            return ammoMeta == other.ammoMeta
                    && ammoItem == other.ammoItem
                    && ammoData.equals(other.ammoData);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (ammoItem == null ? 0 : ammoItem.hashCode());
            result = 31 * result + ammoMeta;
            result = 31 * result + ammoData.hashCode();
            return result;
        }
    }
}
