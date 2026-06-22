package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.TmtGpuItemModel;
import com.smd.toomanytinkers.client.model.TmtGpuToolStackModel;
import com.smd.toomanytinkers.client.model.TmtPartDefinition;
import com.smd.toomanytinkers.client.model.TmtResolvedPartModel;
import com.smd.toomanytinkers.client.model.TmtResolvedToolModel;
import com.smd.toomanytinkers.client.model.TmtToolRenderDescriptor;
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
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TmtGpuToolRenderer {

    private static final TmtInstanceBuffer INSTANCE_BUFFER = new TmtInstanceBuffer();

    private TmtGpuToolRenderer() {
    }

    public static boolean render(TmtGpuItemModel model) {
        return render(model, true);
    }

    public static boolean renderPreparedModel(TmtGpuItemModel model) {
        return render(model, false);
    }

    private static boolean render(TmtGpuItemModel model, boolean centerModel) {
        bindTextures();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        GlStateManager.pushMatrix();
        if (centerModel) {
            GlStateManager.translate(-0.5f, -0.5f, -0.5f);
        }
        try {
            if (!renderInstanced(model)) {
                renderLegacy(model);
            }
        } finally {
            GlStateManager.popMatrix();
            unbindExtraTextures();
        }
        return true;
    }

    private static void bindTextures() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
        mc.getTextureManager().bindTexture(MaterialLutManager.getTextureLocation());
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 2);
        mc.getTextureManager().bindTexture(MaterialSourceTextureManager.getTextureLocation());
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static boolean renderInstanced(TmtGpuItemModel model) {
        RenderBuckets buckets = new RenderBuckets();
        collect(model, buckets);
        if (buckets.hasLegacyModels || (buckets.instanced.isEmpty() && buckets.spriteInstanced.isEmpty())) {
            return false;
        }
        if (!TmtInstancedPaletteShader.bind()) {
            return false;
        }
        try {
            for (Map.Entry<PartMeshCache.PartMesh, List<TmtInstanceBuffer.InstanceData>> entry : buckets.instanced.entrySet()) {
                PartMeshCache.PartMesh mesh = entry.getKey();
                List<TmtInstanceBuffer.InstanceData> instances = entry.getValue();
                mesh.bind();
                int instanceCount = INSTANCE_BUFFER.upload(instances);
                INSTANCE_BUFFER.bindAttributes();
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, mesh.getIndexCount(), GL11.GL_UNSIGNED_INT, 0L, instanceCount);
                TmtRenderStats.instancedDrawCall();
            }
            for (Map.Entry<PartSpriteMeshCache.PartMesh, List<TmtInstanceBuffer.InstanceData>> entry : buckets.spriteInstanced.entrySet()) {
                PartSpriteMeshCache.PartMesh mesh = entry.getKey();
                List<TmtInstanceBuffer.InstanceData> instances = entry.getValue();
                mesh.bind();
                int instanceCount = INSTANCE_BUFFER.upload(instances);
                INSTANCE_BUFFER.bindAttributes();
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, mesh.getIndexCount(), GL11.GL_UNSIGNED_INT, 0L, instanceCount);
                TmtRenderStats.instancedDrawCall();
            }
        } finally {
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            TmtInstancedPaletteShader.unbind();
        }
        return true;
    }

    private static void collect(TmtGpuItemModel model, RenderBuckets buckets) {
        if (model instanceof TmtResolvedPartModel) {
            TmtResolvedPartModel part = (TmtResolvedPartModel) model;
            addInstance(part.getBaseModel(), part.getMaterialId(), buckets);
        } else if (model instanceof TmtResolvedToolModel) {
            TmtResolvedToolModel tool = (TmtResolvedToolModel) model;
            for (TmtResolvedToolModel.PartInstance part : tool.getParts()) {
                addInstance(part.getBaseModel(), part.getMaterialId(), buckets);
            }
            for (IBakedModel vanillaModel : tool.getVanillaModels()) {
                addInstance(vanillaModel, null, buckets);
            }
        } else if (model instanceof TmtGpuToolStackModel) {
            TmtGpuToolStackModel tool = (TmtGpuToolStackModel) model;
            for (TmtToolRenderDescriptor.PartInstance part : tool.getDescriptor().getParts()) {
                addSpritePart(part.getDefinition(), part.getMaterialId(), buckets);
            }
        }
    }

    private static void addSpritePart(TmtPartDefinition definition, String materialId, RenderBuckets buckets) {
        for (ResourceLocation baseTexture : definition.getTextures()) {
            ResourceLocation texture = materialId == null
                    ? baseTexture
                    : MaterialDescriptorRegistry.resolveParamMap(baseTexture, materialId);
            PartSpriteMeshCache.PartMesh mesh = PartSpriteMeshCache.get(texture, definition);
            if (mesh == null) {
                continue;
            }
            MaterialDescriptor descriptor = materialId == null ? null : MaterialDescriptorRegistry.get(materialId);
            int materialRow = descriptor == null ? -1 : descriptor.getRampRow();
            int sourceLayer = descriptor == null ? -1 : descriptor.getSourceLayer();
            int flags = descriptor == null ? 0 : descriptor.getFlags();
            buckets.spriteInstanced.computeIfAbsent(mesh, ignored -> new ArrayList<>())
                    .add(new TmtInstanceBuffer.InstanceData(materialRow, sourceLayer, flags));
        }
    }

    private static void addInstance(IBakedModel model, String materialId, RenderBuckets buckets) {
        PartMeshCache.PartMesh mesh = PartMeshCache.get(model);
        if (mesh == null) {
            buckets.hasLegacyModels = true;
            return;
        }
        MaterialDescriptor descriptor = materialId == null ? null : MaterialDescriptorRegistry.get(materialId);
        int materialRow = descriptor == null ? -1 : descriptor.getRampRow();
        int sourceLayer = descriptor == null ? -1 : descriptor.getSourceLayer();
        int flags = descriptor == null ? 0 : descriptor.getFlags();
        buckets.instanced.computeIfAbsent(mesh, ignored -> new ArrayList<>())
                .add(new TmtInstanceBuffer.InstanceData(materialRow, sourceLayer, flags));
    }

    private static void renderLegacy(TmtGpuItemModel model) {
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
        }
    }

    private static void unbindExtraTextures() {
        Minecraft mc = Minecraft.getMinecraft();
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
        GlStateManager.bindTexture(0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 2);
        GlStateManager.bindTexture(0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    private static void drawModel(IBakedModel model, String materialId) {
        MaterialDescriptor descriptor = materialId == null ? null : MaterialDescriptorRegistry.get(materialId);
        int materialRow = descriptor == null ? -1 : descriptor.getRampRow();
        int sourceLayer = descriptor == null ? -1 : descriptor.getSourceLayer();
        TmtPaletteShader.setMaterial(materialRow, sourceLayer);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        renderQuads(buffer, model.getQuads(null, null, 0));
        for (EnumFacing side : EnumFacing.VALUES) {
            renderQuads(buffer, model.getQuads(null, side, 0));
        }
        tessellator.draw();
        TmtRenderStats.legacyDrawCall();
    }

    private static void renderQuads(BufferBuilder buffer, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            LightUtil.renderQuadColor(buffer, quad, 0xffffffff);
        }
    }

    private static class RenderBuckets {
        private final Map<PartMeshCache.PartMesh, List<TmtInstanceBuffer.InstanceData>> instanced =
                new LinkedHashMap<>();
        private final Map<PartSpriteMeshCache.PartMesh, List<TmtInstanceBuffer.InstanceData>> spriteInstanced =
                new LinkedHashMap<>();
        private boolean hasLegacyModels;
    }
}
