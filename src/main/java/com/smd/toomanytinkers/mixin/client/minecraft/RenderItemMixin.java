package com.smd.toomanytinkers.mixin.client.minecraft;

import com.smd.toomanytinkers.client.model.TmtGpuItemModel;
import com.smd.toomanytinkers.client.render.TmtGpuToolRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public class RenderItemMixin {

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At("HEAD"), cancellable = true)
    private void tmt$renderGpuDescriptor(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        if (model instanceof TmtGpuItemModel) {
            TmtGpuToolRenderer.render((TmtGpuItemModel) model);
            ci.cancel();
        }
    }
}
