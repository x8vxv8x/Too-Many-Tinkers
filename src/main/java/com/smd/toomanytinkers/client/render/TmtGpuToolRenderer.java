package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.TmtGpuItemModel;
import com.smd.toomanytinkers.client.model.TmtResolvedPartModel;
import com.smd.toomanytinkers.client.model.TmtResolvedToolModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.lwjgl.opengl.GL11;

import java.util.List;

public final class TmtGpuToolRenderer {

    private TmtGpuToolRenderer() {
    }

    public static boolean render(TmtGpuItemModel model) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
        mc.getTextureManager().bindTexture(MaterialLutManager.getTextureLocation());
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 2);
        mc.getTextureManager().bindTexture(MaterialSourceTextureManager.getTextureLocation());
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        GlStateManager.pushMatrix();
        GlStateManager.translate(-0.5f, -0.5f, -0.5f);
        TmtPaletteShader.bind();
        try {
            if (model instanceof TmtResolvedPartModel) {
                TmtResolvedPartModel part = (TmtResolvedPartModel) model;
                drawModel(part.getBaseModel(), part.getMaterialId());
            } else if (model instanceof TmtResolvedToolModel) {
                TmtResolvedToolModel tool = (TmtResolvedToolModel) model;
                for (TmtResolvedToolModel.PartInstance part : tool.getParts()) {
                    drawModel(part.getBaseModel(), part.getMaterialId());
                }
                for (IBakedModel vanillaModel : tool.getVanillaModels()) {
                    drawModel(vanillaModel, null);
                }
            }
        } finally {
            TmtPaletteShader.unbind();
            GlStateManager.popMatrix();
        }

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
        GlStateManager.bindTexture(0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 2);
        GlStateManager.bindTexture(0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        return true;
    }

    private static void drawModel(IBakedModel model, String materialId) {
        int materialRow = materialId == null ? -1 : MaterialLutManager.getMaterialRow(materialId);
        int sourceLayer = materialId == null ? -1 : MaterialSourceTextureManager.getSourceLayer(materialId);
        TmtPaletteShader.setMaterial(materialRow, sourceLayer);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        renderQuads(buffer, model.getQuads(null, null, 0));
        for (EnumFacing side : EnumFacing.VALUES) {
            renderQuads(buffer, model.getQuads(null, side, 0));
        }
        tessellator.draw();
    }

    private static void renderQuads(BufferBuilder buffer, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            LightUtil.renderQuadColor(buffer, quad, 0xffffffff);
        }
    }
}
