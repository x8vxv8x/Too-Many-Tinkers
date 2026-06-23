package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.TmtGpuItemModel;
import com.smd.toomanytinkers.client.model.TmtGpuToolStackModel;
import com.smd.toomanytinkers.client.model.TmtLayeredItemModel;
import com.smd.toomanytinkers.client.model.TmtToolRenderDescriptor;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.model.TRSRTransformation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import slimeknights.tconstruct.library.client.model.format.AmmoPosition;
import slimeknights.tconstruct.library.tools.IAmmoUser;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;

public final class TmtGpuToolRenderer {

    private static final TmtInstanceBuffer INSTANCE_BUFFER = new TmtInstanceBuffer();
    private static final TmtIndirectCommandBuffer INDIRECT_BUFFER = new TmtIndirectCommandBuffer();
    private static final ThreadLocal<RenderState> RENDER_STATE = ThreadLocal.withInitial(RenderState::new);

    private TmtGpuToolRenderer() {
    }

    public static boolean render(TmtGpuItemModel model) {
        return render(model, true, true);
    }

    public static boolean render(TmtGpuItemModel model, boolean useLightmap) {
        return render(model, true, useLightmap);
    }

    public static boolean renderPreparedModel(TmtGpuItemModel model) {
        return render(model, false, true);
    }

    public static boolean renderPreparedModel(TmtGpuItemModel model, boolean useLightmap) {
        return render(model, false, useLightmap);
    }

    private static boolean render(TmtGpuItemModel model, boolean centerModel, boolean useLightmap) {
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
                rendered = renderInstanced((TmtLayeredItemModel) model, useLightmap);
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
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 2);
        mc.getTextureManager().bindTexture(TmtMaterialMapManager.getTextureLocation());
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static boolean renderInstanced(TmtLayeredItemModel model, boolean useLightmap) {
        List<TmtToolRenderDescriptor.Layer> layers = layersForRender(model);
        RenderState state = RENDER_STATE.get();
        state.reset();
        collectCounts(layers, state);
        if (state.isEmpty()) {
            return false;
        }
        state.finishCounts();

        bindTextures();
        TmtInstancedPaletteShader.bind(useLightmap);
        try {
            uploadInstances(layers, state);
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

    private static List<TmtToolRenderDescriptor.Layer> layersForRender(TmtLayeredItemModel model) {
        ImmutableList<TmtToolRenderDescriptor.Layer> baseLayers = model.getLayers();
        if (!(model instanceof TmtGpuToolStackModel)) {
            return baseLayers;
        }

        List<TmtToolRenderDescriptor.Layer> ammoLayers = ammoLayersFor((TmtGpuToolStackModel) model);
        if (ammoLayers.isEmpty()) {
            return baseLayers;
        }

        List<TmtToolRenderDescriptor.Layer> layers = new ArrayList<>(baseLayers.size() + ammoLayers.size());
        layers.addAll(baseLayers);
        layers.addAll(ammoLayers);
        return layers;
    }

    private static List<TmtToolRenderDescriptor.Layer> ammoLayersFor(TmtGpuToolStackModel model) {
        AmmoPosition position = model.getDescriptor().getDefinition().getAmmoPosition();
        ItemStack stack = model.getRenderStack();
        EntityLivingBase entity = model.getRenderEntity();
        if (position == null || stack == null || stack.isEmpty() || entity == null
                || !(stack.getItem() instanceof IAmmoUser)) {
            return ImmutableList.of();
        }

        ItemStack ammo = ((IAmmoUser) stack.getItem()).getAmmoToRender(stack, entity);
        if (ammo.isEmpty()) {
            return ImmutableList.of();
        }

        World world = model.getRenderWorld();
        if (world == null) {
            world = entity.getEntityWorld();
        }

        IBakedModel ammoModel = Minecraft.getMinecraft()
                .getRenderItem()
                .getItemModelWithOverrides(ammo, world, entity);
        if (!(ammoModel instanceof TmtLayeredItemModel)) {
            return ImmutableList.of();
        }

        Matrix4f transform = ammoTransform(position);
        ImmutableList<TmtToolRenderDescriptor.Layer> sourceLayers = ((TmtLayeredItemModel) ammoModel).getLayers();
        List<TmtToolRenderDescriptor.Layer> transformed = new ArrayList<>(sourceLayers.size());
        for (TmtToolRenderDescriptor.Layer layer : sourceLayers) {
            transformed.add(layer.withAdditionalTransform(transform));
        }
        return transformed;
    }

    private static Matrix4f ammoTransform(AmmoPosition position) {
        TRSRTransformation transform = new TRSRTransformation(
                new Vector3f(valueAt(position.pos, 0),
                        valueAt(position.pos, 1),
                        valueAt(position.pos, 2)),
                null,
                new Vector3f(1f, 1f, 1f),
                TRSRTransformation.quatFromXYZ(
                        radians(valueAt(position.rot, 0)),
                        radians(valueAt(position.rot, 1)),
                        radians(valueAt(position.rot, 2))));
        return TRSRTransformation.blockCenterToCorner(transform).getMatrix();
    }

    private static float valueAt(Float[] values, int index) {
        return values != null && values.length > index && values[index] != null ? values[index] : 0f;
    }

    private static float radians(float degrees) {
        return (degrees / 180f) * (float) Math.PI;
    }

    private static void collectCounts(List<TmtToolRenderDescriptor.Layer> layers, RenderState state) {
        for (TmtToolRenderDescriptor.Layer layer : layers) {
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
                layer.getCompositeOpaque());
    }

    private static void uploadInstances(List<TmtToolRenderDescriptor.Layer> layers, RenderState state) {
        INSTANCE_BUFFER.beginUpload(state.instanceCount);
        for (TmtToolRenderDescriptor.Layer layer : layers) {
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
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 2);
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
