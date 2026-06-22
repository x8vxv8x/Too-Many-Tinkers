package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.model.TRSRTransformation;
import slimeknights.mantle.client.model.TRSRBakedModel;
import slimeknights.tconstruct.library.client.model.BakedBowModel;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;
import slimeknights.tconstruct.library.client.model.BakedToolModelOverride;
import slimeknights.tconstruct.library.client.model.ModelHelper;
import slimeknights.tconstruct.library.client.model.format.AmmoPosition;
import slimeknights.tconstruct.library.tools.IAmmoUser;

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
}
