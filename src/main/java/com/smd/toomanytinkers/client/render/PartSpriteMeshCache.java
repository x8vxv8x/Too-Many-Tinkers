package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.TmtPartDefinition;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Objects;

public final class PartSpriteMeshCache {

    private static final int FLOATS_PER_VERTEX = 5;
    private static final float FRONT_Z = 0.53125f;
    private static final float BACK_Z = 0.46875f;
    private static final Object2ObjectOpenHashMap<Key, PartMesh> MESHES = new Object2ObjectOpenHashMap<>();

    private static int vao;
    private static int vbo;
    private static int ebo;
    private static int vertexCapacityFloats;
    private static int indexCapacity;
    private static int vertexFloats;
    private static int indices;
    private static int nextMeshId;

    private PartSpriteMeshCache() {
    }

    @Nullable
    public static PartMesh get(ResourceLocation texture,
                               TmtPartDefinition definition,
                               boolean[] sideOpaque,
                               boolean[] compositeOpaque,
                               int sideHash,
                               int compositeHash) {
        Key key = new Key(texture,
                definition.getOffsetX(),
                definition.getOffsetY(),
                definition.getRotationDegrees(),
                definition.getZBias(),
                sideHash,
                compositeHash);
        PartMesh existing = MESHES.get(key);
        if (existing != null) {
            return existing;
        }
        PartMesh mesh = build(key, sideOpaque, compositeOpaque);
        if (mesh != null) {
            MESHES.put(key, mesh);
            TmtRenderStats.setPartMeshes(MESHES.size());
        }
        return mesh;
    }

    public static void bindGlobalMesh() {
        ensureObjects();
        GL30.glBindVertexArray(vao);
    }

    public static void clear() {
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (ebo != 0) {
            GL15.glDeleteBuffers(ebo);
            ebo = 0;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        MESHES.clear();
        vertexCapacityFloats = 0;
        indexCapacity = 0;
        vertexFloats = 0;
        indices = 0;
        nextMeshId = 0;
        TmtRenderStats.setPartMeshes(0);
    }

    @Nullable
    private static PartMesh build(Key key, boolean[] sideOpaque, boolean[] compositeOpaque) {
        int width = 16;
        int height = 16;
        boolean[] side = normalizedMask(sideOpaque);
        boolean[] composite = normalizedMask(compositeOpaque);

        FloatArrayList vertices = new FloatArrayList(256);
        IntArrayList indices = new IntArrayList(384);

        addQuad(vertices, indices, key,
                0f, 0f, FRONT_Z, 0f, 1f,
                1f, 0f, FRONT_Z, 1f, 1f,
                1f, 1f, FRONT_Z, 1f, 0f,
                0f, 1f, FRONT_Z, 0f, 0f);

        addQuad(vertices, indices, key,
                0f, 1f, BACK_Z, 0f, 0f,
                1f, 1f, BACK_Z, 1f, 0f,
                1f, 0f, BACK_Z, 1f, 1f,
                0f, 0f, BACK_Z, 0f, 1f);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!side[y * width + x]) {
                    continue;
                }
                float x0 = x / (float) width;
                float x1 = (x + 1) / (float) width;
                float y0 = 1f - (y + 1) / (float) height;
                float y1 = 1f - y / (float) height;

                float uc = (x + 0.5f) / width;
                float vc = (y + 0.5f) / height;

                if (isTransparent(composite, width, height, x - 1, y)) {
                    addQuad(vertices, indices, key,
                            x0, y0, BACK_Z, uc, vc,
                            x0, y0, FRONT_Z, uc, vc,
                            x0, y1, FRONT_Z, uc, vc,
                            x0, y1, BACK_Z, uc, vc);
                }
                if (isTransparent(composite, width, height, x + 1, y)) {
                    addQuad(vertices, indices, key,
                            x1, y1, BACK_Z, uc, vc,
                            x1, y1, FRONT_Z, uc, vc,
                            x1, y0, FRONT_Z, uc, vc,
                            x1, y0, BACK_Z, uc, vc);
                }
                if (isTransparent(composite, width, height, x, y - 1)) {
                    addQuad(vertices, indices, key,
                            x0, y1, BACK_Z, uc, vc,
                            x0, y1, FRONT_Z, uc, vc,
                            x1, y1, FRONT_Z, uc, vc,
                            x1, y1, BACK_Z, uc, vc);
                }
                if (isTransparent(composite, width, height, x, y + 1)) {
                    addQuad(vertices, indices, key,
                            x1, y0, BACK_Z, uc, vc,
                            x1, y0, FRONT_Z, uc, vc,
                            x0, y0, FRONT_Z, uc, vc,
                            x0, y0, BACK_Z, uc, vc);
                }
            }
        }

        if (indices.isEmpty()) {
            return null;
        }

        return appendMesh(vertices.toFloatArray(), indices.toIntArray());
    }

    private static PartMesh appendMesh(float[] meshVertices, int[] meshIndices) {
        ensureObjects();

        int baseVertex = vertexFloats / FLOATS_PER_VERTEX;
        int firstIndex = indices;

        ensureVertexCapacity(vertexFloats + meshVertices.length);
        ensureIndexCapacity(indices + meshIndices.length);

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(meshVertices.length);
        vertexBuffer.put(meshVertices);
        vertexBuffer.flip();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, vertexFloats * (long) Float.BYTES, vertexBuffer);

        IntBuffer indexBuffer = BufferUtils.createIntBuffer(meshIndices.length);
        indexBuffer.put(meshIndices);
        indexBuffer.flip();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices * (long) Integer.BYTES, indexBuffer);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        vertexFloats += meshVertices.length;
        indices += meshIndices.length;

        return new PartMesh(nextMeshId++, firstIndex, meshIndices.length, baseVertex);
    }

    private static void ensureObjects() {
        if (vao != 0) {
            return;
        }
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        ebo = GL15.glGenBuffers();
        bindVaoState();
    }

    private static void bindVaoState() {
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 3L * Float.BYTES);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static void ensureVertexCapacity(int requiredFloats) {
        if (requiredFloats <= vertexCapacityFloats) {
            return;
        }
        vertexCapacityFloats = nextPowerOfTwo(requiredFloats);
        vbo = resizeBuffer(vbo,
                vertexFloats * (long) Float.BYTES,
                vertexCapacityFloats * (long) Float.BYTES);
        bindVaoState();
    }

    private static void ensureIndexCapacity(int requiredIndices) {
        if (requiredIndices <= indexCapacity) {
            return;
        }
        indexCapacity = nextPowerOfTwo(requiredIndices);
        ebo = resizeBuffer(ebo,
                indices * (long) Integer.BYTES,
                indexCapacity * (long) Integer.BYTES);
        bindVaoState();
    }

    private static int resizeBuffer(int oldBuffer, long usedBytes, long newBytes) {
        int newBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, newBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, newBytes, GL15.GL_STATIC_DRAW);
        if (oldBuffer != 0 && usedBytes > 0L) {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, oldBuffer);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, newBuffer);
            GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0L, 0L, usedBytes);
            GL15.glDeleteBuffers(oldBuffer);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return newBuffer;
    }

    private static boolean[] normalizedMask(boolean[] mask) {
        return mask.length == 16 * 16 ? mask : Arrays.copyOf(mask, 16 * 16);
    }

    private static int nextPowerOfTwo(int value) {
        int out = 1;
        while (out < value) {
            out <<= 1;
        }
        return out;
    }

    private static boolean isTransparent(boolean[] opaque, int width, int height, int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height || !opaque[y * width + x];
    }

    private static void addQuad(FloatArrayList vertices, IntArrayList indices, Key key,
                                float x0, float y0, float z0, float u0, float v0,
                                float x1, float y1, float z1, float u1, float v1,
                                float x2, float y2, float z2, float u2, float v2,
                                float x3, float y3, float z3, float u3, float v3) {
        int base = vertices.size() / FLOATS_PER_VERTEX;
        addVertex(vertices, key, x0, y0, z0, u0, v0);
        addVertex(vertices, key, x1, y1, z1, u1, v1);
        addVertex(vertices, key, x2, y2, z2, u2, v2);
        addVertex(vertices, key, x3, y3, z3, u3, v3);
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);
    }

    private static void addVertex(FloatArrayList vertices, Key key, float x, float y, float z, float u, float v) {
        x += key.offsetX / 16f;
        y -= key.offsetY / 16f;
        z += key.zBias;
        if (key.rotationDegrees != 0f) {
            double radians = Math.toRadians(key.rotationDegrees);
            float cx = x - 0.5f;
            float cy = y - 0.5f;
            float rx = (float) (cx * Math.cos(radians) - cy * Math.sin(radians));
            float ry = (float) (cx * Math.sin(radians) + cy * Math.cos(radians));
            x = rx + 0.5f;
            y = ry + 0.5f;
        }
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(u);
        vertices.add(v);
    }

    private static final class Key {
        private final ResourceLocation texture;
        private final int offsetX;
        private final int offsetY;
        private final float rotationDegrees;
        private final float zBias;
        private final int sideHash;
        private final int compositeHash;

        private Key(ResourceLocation texture,
                    int offsetX,
                    int offsetY,
                    float rotationDegrees,
                    float zBias,
                    int sideHash,
                    int compositeHash) {
            this.texture = texture;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.rotationDegrees = rotationDegrees;
            this.zBias = zBias;
            this.sideHash = sideHash;
            this.compositeHash = compositeHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Key)) {
                return false;
            }
            Key other = (Key) obj;
            return offsetX == other.offsetX
                    && offsetY == other.offsetY
                    && Float.floatToIntBits(rotationDegrees) == Float.floatToIntBits(other.rotationDegrees)
                    && Float.floatToIntBits(zBias) == Float.floatToIntBits(other.zBias)
                    && sideHash == other.sideHash
                    && compositeHash == other.compositeHash
                    && texture.equals(other.texture);
        }

        @Override
        public int hashCode() {
            return Objects.hash(texture, offsetX, offsetY,
                    Float.floatToIntBits(rotationDegrees),
                    Float.floatToIntBits(zBias),
                    sideHash,
                    compositeHash);
        }
    }

    public static final class PartMesh {
        private final int id;
        private final int firstIndex;
        private final int indexCount;
        private final int baseVertex;

        private PartMesh(int id, int firstIndex, int indexCount, int baseVertex) {
            this.id = id;
            this.firstIndex = firstIndex;
            this.indexCount = indexCount;
            this.baseVertex = baseVertex;
        }

        public int getId() {
            return id;
        }

        public int getFirstIndex() {
            return firstIndex;
        }

        public int getIndexCount() {
            return indexCount;
        }

        public int getBaseVertex() {
            return baseVertex;
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
