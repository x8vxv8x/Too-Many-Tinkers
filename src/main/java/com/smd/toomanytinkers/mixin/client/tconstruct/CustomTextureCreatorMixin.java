package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.smd.toomanytinkers.client.render.MaterialDescriptorRegistry;
import com.smd.toomanytinkers.client.render.TmtCustomTextureBridge;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.tools.IToolPart;

import java.util.Map;
import java.util.Set;

@Mixin(value = CustomTextureCreator.class, remap = false)
public class CustomTextureCreatorMixin {

    @Shadow @Final private static Set<ResourceLocation> baseTextures;
    @Shadow @Final private static Map<ResourceLocation, Set<IToolPart>> texturePartMapping;

    @Inject(method = "createMaterialTextures", at = @At("HEAD"), cancellable = true)
    private void tmt$buildMaterialDescriptors(TextureMap map, CallbackInfo ci) {
        TmtCustomTextureBridge.rebuild(baseTextures, texturePartMapping);
        MaterialDescriptorRegistry.rebuild(map, baseTextures);
        ci.cancel();
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void tmt$clearVirtualTextureBridge(CallbackInfo ci) {
        TmtCustomTextureBridge.clear();
    }
}
