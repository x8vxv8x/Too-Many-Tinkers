package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.TmtGpuItemModel;
import com.smd.toomanytinkers.client.model.TmtLayeredItemModel;
import com.smd.toomanytinkers.client.model.TmtToolRenderDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
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
            if (model instanceof TmtLayeredItemModel) {
                renderInstanced((TmtLayeredItemModel) model);
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

    private static void renderInstanced(TmtLayeredItemModel model) {
        RenderBuckets buckets = new RenderBuckets();
        collect(model, buckets);
        if (buckets.spriteInstanced.isEmpty()) {
            return;
        }

        List<TmtInstanceBuffer.InstanceData> instances = new ArrayList<>();
        List<DrawCall> drawCalls = new ArrayList<>();
        for (Map.Entry<PartSpriteMeshCache.PartMesh, List<TmtInstanceBuffer.InstanceData>> entry : buckets.spriteInstanced.entrySet()) {
            int offset = instances.size();
            instances.addAll(entry.getValue());
            drawCalls.add(new DrawCall(entry.getKey(), offset, entry.getValue().size()));
        }

        TmtInstancedPaletteShader.bind();
        try {
            INSTANCE_BUFFER.upload(instances);
            for (DrawCall drawCall : drawCalls) {
                drawCall.mesh.bind();
                TmtInstancedPaletteShader.setInstanceBase(drawCall.instanceOffset);
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES,
                        drawCall.mesh.getIndexCount(),
                        GL11.GL_UNSIGNED_INT,
                        0L,
                        drawCall.instanceCount);
                TmtRenderStats.instancedDrawCall();
            }
        } finally {
            GL30.glBindVertexArray(0);
            TmtInstancedPaletteShader.unbind();
        }
    }

    private static void collect(TmtLayeredItemModel model, RenderBuckets buckets) {
        for (TmtToolRenderDescriptor.Layer layer : model.getLayers()) {
            addSpriteLayer(layer, buckets);
        }
    }

    private static void addSpriteLayer(TmtToolRenderDescriptor.Layer layer, RenderBuckets buckets) {
        ResourceLocation baseTexture = layer.getBaseTexture();
        String materialId = layer.getMaterialId();
        ResourceLocation sampleTexture = materialId == null
                ? baseTexture
                : MaterialDescriptorRegistry.resolveParamMap(baseTexture, materialId);

        PartSpriteMeshCache.PartMesh mesh = PartSpriteMeshCache.get(
                layer.getGeometry().getShapeTexture(),
                layer.getGeometry().getDefinition());
        if (mesh == null) {
            return;
        }

        TextureAtlasSprite sprite = getSprite(sampleTexture);
        MaterialDescriptor descriptor = materialId == null ? null : MaterialDescriptorRegistry.get(materialId);
        int materialRow = descriptor == null ? -1 : descriptor.getRampRow();
        int sourceLayer = descriptor == null ? -1 : descriptor.getSourceLayer();
        int flags = descriptor == null ? layer.getFlags() : descriptor.getFlags() | layer.getFlags();
        buckets.spriteInstanced.computeIfAbsent(mesh, ignored -> new ArrayList<>())
                .add(new TmtInstanceBuffer.InstanceData(
                        layer.getTransform(),
                        sprite.getMinU(),
                        sprite.getMinV(),
                        sprite.getMaxU(),
                        sprite.getMaxV(),
                        materialRow,
                        sourceLayer,
                        flags));
    }

    private static TextureAtlasSprite getSprite(ResourceLocation texture) {
        TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
        TextureAtlasSprite sprite = map.getTextureExtry(texture.toString());
        return sprite == null ? map.getMissingSprite() : sprite;
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

    private static class RenderBuckets {
        private final Map<PartSpriteMeshCache.PartMesh, List<TmtInstanceBuffer.InstanceData>> spriteInstanced =
                new LinkedHashMap<>();
    }

    private static final class DrawCall {
        private final PartSpriteMeshCache.PartMesh mesh;
        private final int instanceOffset;
        private final int instanceCount;

        private DrawCall(PartSpriteMeshCache.PartMesh mesh, int instanceOffset, int instanceCount) {
            this.mesh = mesh;
            this.instanceOffset = instanceOffset;
            this.instanceCount = instanceCount;
        }
    }
}
