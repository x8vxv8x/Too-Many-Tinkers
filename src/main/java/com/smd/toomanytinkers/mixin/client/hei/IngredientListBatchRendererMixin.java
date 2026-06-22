package com.smd.toomanytinkers.mixin.client.hei;

import com.smd.toomanytinkers.client.model.TmtGpuItemModel;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.render.IngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = IngredientListBatchRenderer.class, remap = false)
public class IngredientListBatchRendererMixin {

    @Shadow @Final private List<IngredientRenderer> renderOther;

    @Inject(method = "set(Lmezz/jei/render/IngredientListSlot;Lmezz/jei/gui/ingredients/IIngredientListElement;)V",
            at = @At("HEAD"), cancellable = true)
    private <V> void tmt$renderGpuModelsSlowly(IngredientListSlot slot,
                                               IIngredientListElement<V> element,
                                               CallbackInfo ci) {
        Object ingredient = element.getIngredient();
        if (!(ingredient instanceof ItemStack)) {
            return;
        }

        IBakedModel model = Minecraft.getMinecraft()
                .getRenderItem()
                .getItemModelWithOverrides((ItemStack) ingredient, null, null);
        if (!(model instanceof TmtGpuItemModel)) {
            return;
        }

        slot.clear();
        IngredientRenderer<V> renderer = new IngredientRenderer<>(element);
        slot.setIngredientRenderer(renderer);
        renderOther.add(renderer);
        ci.cancel();
    }
}
