package com.smd.toomanytinkers.mixin.client.conarm;

import c4.conarm.lib.client.DynamicTextureHelper;
import c4.conarm.lib.client.IArmorMaterialTexture;
import c4.conarm.lib.modifiers.IArmorModelModifier;
import c4.conarm.lib.tinkering.TinkersArmor;
import com.smd.toomanytinkers.client.render.TmtCustomTextureBridge;
import com.smd.toomanytinkers.client.render.TmtOneShotTexture;
import com.google.common.cache.Cache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.modifiers.IModifier;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.TinkerUtil;
import slimeknights.tconstruct.tools.modifiers.ModIncognito;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = DynamicTextureHelper.class, remap = false)
public abstract class DynamicTextureHelperMixin {

    private static final Logger TMT_CONARM_LOGGER = LogManager.getLogger("conarm");
    private static final AtomicInteger TMT_ARMOR_TEXTURE_ID = new AtomicInteger();

    @Shadow
    private static Cache<DynamicTextureHelper.CacheKey, ResourceLocation> dynamicTextureCache;

    @Inject(method = "getCachedTexture", at = @At("HEAD"), cancellable = true)
    private static void tmt$getCachedTextureWithThrowable(ItemStack stack,
                                                          CallbackInfoReturnable<ResourceLocation> cir) {
        if (!(stack.getItem() instanceof TinkersArmor)) {
            return;
        }

        try {
            DynamicTextureHelper.CacheKey key = DynamicTextureHelper.getCacheKey(stack);
            Callable<ResourceLocation> loader = () -> tmt$createCombinedTexture(stack, (TinkersArmor) stack.getItem());
            ResourceLocation texture = dynamicTextureCache.get(
                    key,
                    loader);
            cir.setReturnValue(texture);
        } catch (ExecutionException | UncheckedExecutionException e) {
            TMT_CONARM_LOGGER.error("TMT bridge failed while fetching armor texture from cache", e);
            cir.setReturnValue(null);
        }
    }

    private static ResourceLocation tmt$createCombinedTexture(ItemStack stack, TinkersArmor armor) {
        List<BufferedImage> bufferedImages = new ArrayList<>();
        List<Material> materials = TinkerUtil.getMaterialsFromTagList(TagUtil.getBaseMaterialsTagList(stack));
        int textureHeight = 0;
        int textureWidth = 0;

        for (int i = 0; i < materials.size(); i++) {
            Material material = materials.get(i);
            String partIn = tmt$getArmorPartName(i, materials.size());
            String loc = armor.getArmorModelTexture(stack, partIn);

            BufferedImage image = TmtCustomTextureBridge.createMaterialImage(loc, material);
            if (image == null) {
                continue;
            }

            if (material.renderInfo != null
                    && material.renderInfo.useVertexColoring()
                    && !CustomTextureCreator.exists(loc + "_" + material.identifier)) {
                tmt$applyVertexColor(image, material.renderInfo.getVertexColor());
            }

            textureWidth = Math.max(textureWidth, image.getWidth());
            textureHeight = Math.max(textureHeight, image.getHeight());
            bufferedImages.add(image);
        }

        if (!tmt$hasIncognito(stack)) {
            tmt$appendModifierTextures(stack, bufferedImages);
            for (BufferedImage image : bufferedImages) {
                textureWidth = Math.max(textureWidth, image.getWidth());
                textureHeight = Math.max(textureHeight, image.getHeight());
            }
        }

        if (textureWidth <= 0 || textureHeight <= 0) {
            textureWidth = 64;
            textureHeight = 64;
        }

        BufferedImage combined = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D graphics = combined.createGraphics();
        for (BufferedImage image : bufferedImages) {
            graphics.drawImage(image, 0, 0, null);
        }
        graphics.dispose();

        int[] pixels = new int[textureWidth * textureHeight];
        combined.getRGB(0, 0, textureWidth, textureHeight, pixels, 0, textureWidth);
        ResourceLocation location = new ResourceLocation(
                "toomanytinkers",
                "dynamic/conarm_armor_" + TMT_ARMOR_TEXTURE_ID.incrementAndGet());
        Minecraft.getMinecraft().getTextureManager()
                .loadTexture(location, new TmtOneShotTexture(pixels, textureWidth, textureHeight));
        return location;
    }

    private static String tmt$getArmorPartName(int index, int materialCount) {
        switch (index) {
            case 0:
                return "core";
            case 1:
                return "plates";
            case 2:
                return materialCount > 3 ? "plates" : "trim";
            default:
                return "trim";
        }
    }

    private static void tmt$applyVertexColor(BufferedImage image, int color) {
        int a = color >>> 24;
        if (a == 0) {
            a = 255;
        }
        float r = ((color >> 16) & 0xff) / 255f;
        float g = ((color >> 8) & 0xff) / 255f;
        float b = (color & 0xff) / 255f;
        float alpha = a / 255f;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int outA = clamp255((int) (((argb >>> 24) & 0xff) * alpha));
                int outR = clamp255((int) (((argb >> 16) & 0xff) * r));
                int outG = clamp255((int) (((argb >> 8) & 0xff) * g));
                int outB = clamp255((int) ((argb & 0xff) * b));
                image.setRGB(x, y, (outA << 24) | (outR << 16) | (outG << 8) | outB);
            }
        }
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static boolean tmt$hasIncognito(ItemStack stack) {
        for (IModifier modifier : TinkerUtil.getModifiers(stack)) {
            if (modifier instanceof ModIncognito) {
                return true;
            }
        }
        return false;
    }

    private static void tmt$appendModifierTextures(ItemStack stack, List<BufferedImage> output) {
        for (IModifier modifier : TinkerUtil.getModifiers(stack)) {
            String loc = tmt$getModifierTextureLocation(modifier);
            if (loc == null) {
                continue;
            }

            BufferedImage image = TmtCustomTextureBridge.createTextureImage(loc);
            if (image != null) {
                output.add(image);
            }
        }
    }

    private static String tmt$getModifierTextureLocation(IModifier modifier) {
        if (modifier instanceof IArmorMaterialTexture) {
            return ((IArmorMaterialTexture) modifier).getBaseTexture();
        }

        if (modifier instanceof IArmorModelModifier) {
            return ((IArmorModelModifier) modifier).getModelTextureLocation() + "_" + modifier.getIdentifier();
        }
        return null;
    }
}
