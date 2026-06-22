package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.smd.toomanytinkers.client.render.MaterialDescriptorRegistry;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.client.CustomTextureCreator;

import java.util.Set;

@Mixin(value = CustomTextureCreator.class, remap = false)
public class CustomTextureCreatorMixin {

    @Shadow @Final private static Set<ResourceLocation> baseTextures;

    @Inject(method = "createMaterialTextures", at = @At("HEAD"), cancellable = true)
    private void tmt$buildMaterialDescriptors(TextureMap map, CallbackInfo ci) {
        MaterialDescriptorRegistry.rebuild(map, baseTextures);
        ci.cancel();
    }
}
