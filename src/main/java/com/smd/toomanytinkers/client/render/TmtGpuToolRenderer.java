package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.TmtGpuItemModel;
import com.smd.toomanytinkers.client.model.TmtLayeredItemModel;
import com.smd.toomanytinkers.client.model.TmtToolRenderDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
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
    private static final ThreadLocal<RenderState> RENDER_STATE = ThreadLocal.withInitial(RenderState::new);

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
        mc.getTextureManager().bindTexture(TmtPartMaskMapManager.getTextureLocation());
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
        mc.getTextureManager().bindTexture(TmtMaterialMapManager.getTextureLocation());
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static void renderInstanced(TmtLayeredItemModel model) {
        RenderState state = RENDER_STATE.get();
        state.reset();
        collect(model, state);
        if (state.isEmpty()) {
            return;
        }

        TmtInstancedPaletteShader.bind();
        try {
            uploadInstances(state);
            for (int i = 0; i < state.usedBuckets; i++) {
                Bucket bucket = state.bucketPool.get(i);
                bucket.mesh.bind();
                TmtInstancedPaletteShader.setInstanceBase(bucket.instanceOffset);
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES,
                        bucket.mesh.getIndexCount(),
                        GL11.GL_UNSIGNED_INT,
                        0L,
                        bucket.layers.size());
                TmtRenderStats.instancedDrawCall();
            }
        } finally {
            GL30.glBindVertexArray(0);
            TmtInstancedPaletteShader.unbind();
        }
    }

    private static void collect(TmtLayeredItemModel model, RenderState state) {
        for (TmtToolRenderDescriptor.Layer layer : model.getLayers()) {
            addSpriteLayer(layer, state);
        }
    }

    private static void addSpriteLayer(TmtToolRenderDescriptor.Layer layer, RenderState state) {
        ResourceLocation maskTexture = MaterialDescriptorRegistry.resolveMaskTexture(layer.getBaseTexture(), layer.getMaterialId());
        PartSpriteMeshCache.PartMesh mesh = PartSpriteMeshCache.get(
                maskTexture,
                layer.getGeometry().getDefinition());
        if (mesh == null) {
            return;
        }

        state.bucketFor(mesh).layers.add(layer);
        state.instanceCount++;
    }

    private static void uploadInstances(RenderState state) {
        INSTANCE_BUFFER.beginUpload(state.instanceCount);
        int offset = 0;
        for (int i = 0; i < state.usedBuckets; i++) {
            Bucket bucket = state.bucketPool.get(i);
            bucket.instanceOffset = offset;
            for (TmtToolRenderDescriptor.Layer layer : bucket.layers) {
                uploadLayer(layer);
            }
            offset += bucket.layers.size();
        }
        INSTANCE_BUFFER.finishUpload();
    }

    private static void uploadLayer(TmtToolRenderDescriptor.Layer layer) {
        ResourceLocation baseTexture = layer.getBaseTexture();
        String materialId = layer.getMaterialId();
        ResourceLocation maskTexture = MaterialDescriptorRegistry.resolveMaskTexture(baseTexture, materialId);
        MaterialDescriptor descriptor = materialId == null ? null : MaterialDescriptorRegistry.get(materialId);
        int maskSlot = MaterialDescriptorRegistry.getMaskSlot(maskTexture);
        int materialType = descriptor == null ? MaterialDescriptor.TYPE_DIRECT : descriptor.getMaterialType();
        int materialIndex = descriptor == null ? 0 : descriptor.getMaterialIndex();
        int flags = descriptor == null ? layer.getFlags() : descriptor.getFlags() | layer.getFlags();
        INSTANCE_BUFFER.putInstance(
                layer.getTransformForRender(),
                maskSlot,
                materialType,
                materialIndex,
                flags);
    }

    private static void unbindExtraTextures() {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
        GlStateManager.bindTexture(0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(0);
    }

    private static final class RenderState {
        private final Map<PartSpriteMeshCache.PartMesh, Bucket> buckets = new LinkedHashMap<>();
        private final List<Bucket> bucketPool = new ArrayList<>();
        private int usedBuckets;
        private int instanceCount;

        private void reset() {
            buckets.clear();
            for (int i = 0; i < usedBuckets; i++) {
                bucketPool.get(i).clear();
            }
            usedBuckets = 0;
            instanceCount = 0;
        }

        private boolean isEmpty() {
            return instanceCount == 0;
        }

        private Bucket bucketFor(PartSpriteMeshCache.PartMesh mesh) {
            Bucket bucket = buckets.get(mesh);
            if (bucket != null) {
                return bucket;
            }
            if (usedBuckets == bucketPool.size()) {
                bucketPool.add(new Bucket());
            }
            bucket = bucketPool.get(usedBuckets++);
            bucket.reset(mesh);
            buckets.put(mesh, bucket);
            return bucket;
        }
    }

    private static final class Bucket {
        private final List<TmtToolRenderDescriptor.Layer> layers = new ArrayList<>();
        private PartSpriteMeshCache.PartMesh mesh;
        private int instanceOffset;

        private void reset(PartSpriteMeshCache.PartMesh mesh) {
            this.mesh = mesh;
            this.instanceOffset = 0;
            this.layers.clear();
        }

        private void clear() {
            mesh = null;
            instanceOffset = 0;
            layers.clear();
        }
    }
}
