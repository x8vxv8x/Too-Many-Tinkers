package com.smd.toomanytinkers.client.render;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PartMeshCache {

    private static final int FLOATS_PER_VERTEX = 5;
    private static final Map<IBakedModel, PartMesh> MESHES = new IdentityHashMap<>();

    private PartMeshCache() {
    }

    @Nullable
    public static PartMesh get(IBakedModel model) {
        PartMesh existing = MESHES.get(model);
        if (existing != null) {
            return existing;
        }
        PartMesh mesh = build(model);
        if (mesh != null) {
            MESHES.put(model, mesh);
            TmtRenderStats.setPartMeshes(MESHES.size());
        }
        return mesh;
    }

    public static void clear() {
        for (PartMesh mesh : MESHES.values()) {
            mesh.delete();
        }
        MESHES.clear();
        TmtRenderStats.setPartMeshes(0);
    }

    @Nullable
    private static PartMesh build(IBakedModel model) {
        List<BakedQuad> quads = new ArrayList<>();
        quads.addAll(model.getQuads(null, null, 0));
        for (EnumFacing side : EnumFacing.VALUES) {
            quads.addAll(model.getQuads(null, side, 0));
        }
        if (quads.isEmpty()) {
            return null;
        }

        List<Float> vertices = new ArrayList<>(quads.size() * 4 * FLOATS_PER_VERTEX);
        List<Integer> indices = new ArrayList<>(quads.size() * 6);
        float[] unpacked = new float[4];

        for (BakedQuad quad : quads) {
            VertexFormat format = quad.getFormat();
            int positionElement = findElement(format, VertexFormatElement.EnumUsage.POSITION, 0);
            int uvElement = findElement(format, VertexFormatElement.EnumUsage.UV, 0);
            if (positionElement < 0 || uvElement < 0) {
                return null;
            }

            int baseVertex = vertices.size() / FLOATS_PER_VERTEX;
            for (int vertex = 0; vertex < 4; vertex++) {
                LightUtil.unpack(quad.getVertexData(), unpacked, format, vertex, positionElement);
                vertices.add(unpacked[0]);
                vertices.add(unpacked[1]);
                vertices.add(unpacked[2]);
                LightUtil.unpack(quad.getVertexData(), unpacked, format, vertex, uvElement);
                vertices.add(unpacked[0]);
                vertices.add(unpacked[1]);
            }
            indices.add(baseVertex);
            indices.add(baseVertex + 1);
            indices.add(baseVertex + 2);
            indices.add(baseVertex);
            indices.add(baseVertex + 2);
            indices.add(baseVertex + 3);
        }

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.size());
        for (Float value : vertices) {
            vertexBuffer.put(value);
        }
        vertexBuffer.flip();

        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.size());
        for (Integer value : indices) {
            indexBuffer.put(value);
        }
        indexBuffer.flip();

        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        int ebo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 3L * Float.BYTES);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        return new PartMesh(vao, vbo, ebo, indices.size());
    }

    private static int findElement(VertexFormat format, VertexFormatElement.EnumUsage usage, int usageIndex) {
        for (int i = 0; i < format.getElementCount(); i++) {
            VertexFormatElement element = format.getElement(i);
            if (element.getUsage() == usage && element.getIndex() == usageIndex) {
                return i;
            }
        }
        return -1;
    }

    public static final class PartMesh {
        private final int vao;
        private final int vbo;
        private final int ebo;
        private final int indexCount;

        private PartMesh(int vao, int vbo, int ebo, int indexCount) {
            this.vao = vao;
            this.vbo = vbo;
            this.ebo = ebo;
            this.indexCount = indexCount;
        }

        public void bind() {
            GL30.glBindVertexArray(vao);
        }

        public int getIndexCount() {
            return indexCount;
        }

        private void delete() {
            GL15.glDeleteBuffers(vbo);
            GL15.glDeleteBuffers(ebo);
            GL30.glDeleteVertexArrays(vao);
        }
    }

    @Mod.EventBusSubscriber(modid = "toomanytinkers", value = Side.CLIENT)
    public static class Events {
        @SubscribeEvent
        public static void onTextureStitch(TextureStitchEvent.Post event) {
            clear();
        }
    }
}
