package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.smd.toomanytinkers.client.model.TmtBowTemplateModel;
import com.smd.toomanytinkers.client.model.TmtToolTemplateModel;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraftforge.common.model.TRSRTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;
import slimeknights.tconstruct.library.client.model.BakedToolModel;
import slimeknights.tconstruct.library.client.model.BakedToolModelOverride;
import slimeknights.tconstruct.library.client.model.ToolModel;
import slimeknights.tconstruct.library.client.model.format.AmmoPosition;

import java.util.Map;

@Mixin(value = ToolModel.class, remap = false)
public class ToolModelMixin {

    @Shadow private AmmoPosition ammoPosition;

    @Inject(method = "getBakedToolModel", at = @At("HEAD"), cancellable = true)
    private void tmt$createDescriptorTemplate(IBakedModel base,
                                              BakedMaterialModel[] partModels,
                                              BakedMaterialModel[] brokenPartModels,
                                              Map<String, IBakedModel> modifierModels,
                                              ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transform,
                                              ImmutableList<BakedToolModelOverride> overrides,
                                              AmmoPosition overrideAmmoPosition,
                                              CallbackInfoReturnable<BakedToolModel> cir) {
        if (overrideAmmoPosition != null) {
            AmmoPosition combined = overrideAmmoPosition.combine(this.ammoPosition);
            cir.setReturnValue(new TmtBowTemplateModel(base, partModels, brokenPartModels,
                    modifierModels, transform, overrides, combined));
            return;
        }
        cir.setReturnValue(new TmtToolTemplateModel(base, partModels, brokenPartModels,
                modifierModels, transform, overrides));
    }
}
