package com.smd.toomanytinkers.mixin.client.tconstruct;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.materials.Material;

import java.util.Set;

@Mixin(value = CustomTextureCreator.class, remap = false)
public class CustomTextureCreatorMixin {

    @Shadow @Final private static Set<ResourceLocation> baseTextures;

    @Inject(method = "createMaterialTextures", at = @At("HEAD"), cancellable = true)
    private void tmt$registerOnlyGrayMaterialMasks(TextureMap map, CallbackInfo ci) {
        for (ResourceLocation baseTexture : baseTextures) {
            for (Material material : TinkerRegistry.getAllMaterials()) {
                if (material.renderInfo == null || material.renderInfo.getTextureSuffix() == null) {
                    continue;
                }
                String grayMask = baseTexture + "_" + material.renderInfo.getTextureSuffix();
                if (CustomTextureCreator.exists(grayMask)) {
                    map.registerSprite(new ResourceLocation(grayMask));
                }
            }
        }
        ci.cancel();
    }
}
