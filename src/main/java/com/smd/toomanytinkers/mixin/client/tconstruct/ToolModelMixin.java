package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.smd.toomanytinkers.client.model.TmtGpuToolTemplateModel;
import com.smd.toomanytinkers.client.model.TmtToolDefinition;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.client.model.MaterialModel;
import slimeknights.tconstruct.library.client.model.ModifierModel;
import slimeknights.tconstruct.library.client.model.ToolModel;
import slimeknights.tconstruct.library.client.model.format.AmmoPosition;
import slimeknights.tconstruct.library.client.model.format.ToolModelOverride;

import java.util.List;
import java.util.function.Function;

@Mixin(value = ToolModel.class, remap = false)
public class ToolModelMixin {

    @Shadow @Final private List<MaterialModel> partBlocks;
    @Shadow @Final private List<MaterialModel> brokenPartBlocks;
    @Shadow @Final private Float[] layerRotations;
    @Shadow @Final private ModifierModel modifiers;
    @Shadow @Final private ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;
    @Shadow @Final private ImmutableList<ToolModelOverride> overrides;
    @Shadow @Final private AmmoPosition ammoPosition;

    @Inject(method = "bake", at = @At("HEAD"), cancellable = true)
    private void tmt$createGpuToolTemplate(IModelState state,
                                           VertexFormat format,
                                           Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter,
                                           CallbackInfoReturnable<IBakedModel> cir) {
        TmtToolDefinition definition = new TmtToolDefinition(
                partBlocks,
                brokenPartBlocks,
                layerRotations,
                modifiers,
                state,
                transforms,
                overrides,
                ammoPosition);
        cir.setReturnValue(new TmtGpuToolTemplateModel(definition));
    }
}
