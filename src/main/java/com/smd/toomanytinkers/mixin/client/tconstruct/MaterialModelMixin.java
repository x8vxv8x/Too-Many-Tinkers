package com.smd.toomanytinkers.mixin.client.tconstruct;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.smd.toomanytinkers.client.model.TmtEmptyPartParentModel;
import com.smd.toomanytinkers.client.model.TmtPartDefinition;
import com.smd.toomanytinkers.client.model.TmtPartMaterialModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelStateComposition;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;
import slimeknights.tconstruct.library.client.model.MaterialModel;

import javax.vecmath.Vector3f;
import java.util.function.Function;

@Mixin(value = MaterialModel.class, remap = false)
public class MaterialModelMixin {

    @Shadow protected int offsetX;
    @Shadow protected int offsetY;
    @Shadow @Final private ImmutableList<ResourceLocation> textures;

    @Inject(method = "bakeIt", at = @At("HEAD"), cancellable = true)
    private void tmt$bakeSharedPartModel(IModelState state, VertexFormat format,
                                         Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter,
                                         CallbackInfoReturnable<BakedMaterialModel> cir) {
        if (offsetX != 0 || offsetY != 0) {
            state = new ModelStateComposition(state, TRSRTransformation
                    .blockCenterToCorner(new TRSRTransformation(
                            new Vector3f(offsetX / 16f, -offsetY / 16f, 0),
                            null, null, null)));
        }
        ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms =
                PerspectiveMapWrapper.getTransforms(state);
        TextureAtlasSprite particle = textures.isEmpty() ? null : bakedTextureGetter.apply(textures.get(0));
        TmtPartDefinition definition = new TmtPartDefinition(textures, offsetX, offsetY, 0f);
        cir.setReturnValue(new TmtPartMaterialModel(new TmtEmptyPartParentModel(particle), definition, transforms));
    }
}
