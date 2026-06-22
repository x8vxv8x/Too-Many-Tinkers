package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.TmtGpuItemModel;
import com.smd.toomanytinkers.client.model.TmtLayeredItemModel;
import com.smd.toomanytinkers.client.model.TmtToolRenderDescriptor;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

public final class TmtGpuToolRenderer {

    private static final TmtInstanceBuffer INSTANCE_BUFFER = new TmtInstanceBuffer();
    private static final TmtIndirectCommandBuffer INDIRECT_BUFFER = new TmtIndirectCommandBuffer();
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
        boolean rendered = false;
        try {
            if (model instanceof TmtLayeredItemModel) {
                rendered = renderInstanced((TmtLayeredItemModel) model);
            }
        } finally {
            GlStateManager.popMatrix();
            if (rendered) {
                unbindExtraTextures();
            }
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

    private static boolean renderInstanced(TmtLayeredItemModel model) {
        RenderState state = RENDER_STATE.get();
        state.reset();
        collectCounts(model, state);
        if (state.isEmpty()) {
            return false;
        }
        state.finishCounts();

        bindTextures();
        TmtInstancedPaletteShader.bind();
        try {
            uploadInstances(model, state);
            uploadCommands(state);
            PartSpriteMeshCache.bindGlobalMesh();
            GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,
                    GL11.GL_UNSIGNED_INT,
                    0L,
                    state.commandCount,
                    TmtIndirectCommandBuffer.getStride());
            TmtRenderStats.instancedDrawCall();
        } finally {
            GL30.glBindVertexArray(0);
            TmtIndirectCommandBuffer.unbind();
            TmtInstancedPaletteShader.unbind();
        }
        return true;
    }

    private static void collectCounts(TmtLayeredItemModel model, RenderState state) {
        for (TmtToolRenderDescriptor.Layer layer : model.getLayers()) {
            PartSpriteMeshCache.PartMesh mesh = meshFor(layer);
            if (mesh != null) {
                state.add(mesh);
            }
        }
    }

    private static PartSpriteMeshCache.PartMesh meshFor(TmtToolRenderDescriptor.Layer layer) {
        return PartSpriteMeshCache.get(
                layer.getMaskTexture(),
                layer.getGeometry().getDefinition(),
                layer.getSideOpaque(),
                layer.getCompositeOpaque(),
                layer.getSideHash(),
                layer.getCompositeHash());
    }

    private static void uploadInstances(TmtLayeredItemModel model, RenderState state) {
        INSTANCE_BUFFER.beginUpload(state.instanceCount);
        for (TmtToolRenderDescriptor.Layer layer : model.getLayers()) {
            PartSpriteMeshCache.PartMesh mesh = meshFor(layer);
            if (mesh == null) {
                continue;
            }
            int command = state.commandByMeshId.get(mesh.getId());
            if (command < 0) {
                continue;
            }
            uploadLayerAt(state.nextInstance(command), layer);
        }
        INSTANCE_BUFFER.finishUpload();
    }

    private static void uploadLayerAt(int instanceIndex, TmtToolRenderDescriptor.Layer layer) {
        INSTANCE_BUFFER.putInstanceAt(
                instanceIndex,
                layer.getTransformForRender(),
                layer.getMaskSlot(),
                layer.getMaterialType(),
                layer.getMaterialIndex(),
                layer.getSourceIndex(),
                layer.getFlags());
    }

    private static void uploadCommands(RenderState state) {
        INDIRECT_BUFFER.begin(state.commandCount);
        for (int i = 0; i < state.commandCount; i++) {
            PartSpriteMeshCache.PartMesh mesh = state.meshes[i];
            INDIRECT_BUFFER.putDraw(i,
                    mesh.getIndexCount(),
                    state.instanceCounts[i],
                    mesh.getFirstIndex(),
                    mesh.getBaseVertex(),
                    state.instanceOffsets[i]);
        }
        INDIRECT_BUFFER.uploadAndBind();
    }

    private static void unbindExtraTextures() {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
        GlStateManager.bindTexture(0);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(0);
    }

    private static final class RenderState {
        private final Int2IntOpenHashMap commandByMeshId = new Int2IntOpenHashMap();
        private PartSpriteMeshCache.PartMesh[] meshes = new PartSpriteMeshCache.PartMesh[16];
        private int[] instanceCounts = new int[16];
        private int[] instanceOffsets = new int[16];
        private int[] instanceCursors = new int[16];
        private int commandCount;
        private int instanceCount;

        private RenderState() {
            commandByMeshId.defaultReturnValue(-1);
        }

        private void reset() {
            commandByMeshId.clear();
            for (int i = 0; i < commandCount; i++) {
                meshes[i] = null;
                instanceCounts[i] = 0;
                instanceOffsets[i] = 0;
                instanceCursors[i] = 0;
            }
            commandCount = 0;
            instanceCount = 0;
        }

        private boolean isEmpty() {
            return instanceCount == 0;
        }

        private void add(PartSpriteMeshCache.PartMesh mesh) {
            int command = commandByMeshId.get(mesh.getId());
            if (command < 0) {
                command = commandCount++;
                ensureCommandCapacity(commandCount);
                meshes[command] = mesh;
                commandByMeshId.put(mesh.getId(), command);
            }
            instanceCounts[command]++;
            instanceCount++;
        }

        private void finishCounts() {
            int offset = 0;
            for (int i = 0; i < commandCount; i++) {
                instanceOffsets[i] = offset;
                instanceCursors[i] = offset;
                offset += instanceCounts[i];
            }
        }

        private int nextInstance(int command) {
            return instanceCursors[command]++;
        }

        private void ensureCommandCapacity(int required) {
            if (required <= meshes.length) {
                return;
            }
            int capacity = meshes.length << 1;
            while (capacity < required) {
                capacity <<= 1;
            }
            PartSpriteMeshCache.PartMesh[] grownMeshes = new PartSpriteMeshCache.PartMesh[capacity];
            int[] grownCounts = new int[capacity];
            int[] grownOffsets = new int[capacity];
            int[] grownCursors = new int[capacity];
            System.arraycopy(meshes, 0, grownMeshes, 0, meshes.length);
            System.arraycopy(instanceCounts, 0, grownCounts, 0, instanceCounts.length);
            System.arraycopy(instanceOffsets, 0, grownOffsets, 0, instanceOffsets.length);
            System.arraycopy(instanceCursors, 0, grownCursors, 0, instanceCursors.length);
            meshes = grownMeshes;
            instanceCounts = grownCounts;
            instanceOffsets = grownOffsets;
            instanceCursors = grownCursors;
        }
    }
}
