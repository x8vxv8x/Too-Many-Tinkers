package com.smd.toomanytinkers.client.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import javax.vecmath.Matrix4f;
import java.nio.ByteBuffer;
import java.util.List;

public final class TmtInstanceBuffer {

    private static final int BINDING_POINT = 3;
    private static final int BYTES_PER_INSTANCE = 96;
    private static final int MATERIAL_BITS = 16;
    private static final int SOURCE_BITS = 12;
    private static final int FLAGS_BITS = 4;
    private static final int MATERIAL_LIMIT = (1 << MATERIAL_BITS) - 2;
    private static final int SOURCE_LIMIT = (1 << SOURCE_BITS) - 2;
    private static final int FLAGS_LIMIT = (1 << FLAGS_BITS) - 1;

    private int ssbo;
    private int capacity;
    private ByteBuffer uploadBuffer;

    public int upload(List<InstanceData> instances) {
        ensureBuffer();
        ensureCapacity(instances.size());
        ensureUploadBuffer(instances.size());

        uploadBuffer.clear();
        for (InstanceData instance : instances) {
            instance.write(uploadBuffer);
        }
        uploadBuffer.flip();

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, uploadBuffer);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_POINT, ssbo);
        return instances.size();
    }

    private void ensureBuffer() {
        if (ssbo == 0) {
            ssbo = GL15.glGenBuffers();
        }
    }

    private void ensureCapacity(int instances) {
        if (instances > capacity) {
            capacity = nextPowerOfTwo(instances);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                    Math.max(1, capacity) * (long) BYTES_PER_INSTANCE,
                    GL15.GL_STREAM_DRAW);
        }
    }

    private void ensureUploadBuffer(int instances) {
        int bytes = Math.max(1, instances) * BYTES_PER_INSTANCE;
        if (uploadBuffer == null || uploadBuffer.capacity() < bytes) {
            uploadBuffer = BufferUtils.createByteBuffer(bytes);
        }
    }

    private static int nextPowerOfTwo(int value) {
        int out = 1;
        while (out < value) {
            out <<= 1;
        }
        return out;
    }

    public static final class InstanceData {
        private final Matrix4f model;
        private final float minU;
        private final float minV;
        private final float maxU;
        private final float maxV;
        private final int packed;

        public InstanceData(Matrix4f model,
                            float minU,
                            float minV,
                            float maxU,
                            float maxV,
                            int materialRow,
                            int sourceLayer,
                            int flags) {
            this.model = new Matrix4f(model);
            this.minU = minU;
            this.minV = minV;
            this.maxU = maxU;
            this.maxV = maxV;
            this.packed = pack(materialRow, sourceLayer, flags);
        }

        private static int pack(int materialRow, int sourceLayer, int flags) {
            if (materialRow > MATERIAL_LIMIT) {
                throw new IllegalArgumentException("TMT material row exceeds SSBO packing limit: " + materialRow);
            }
            if (sourceLayer > SOURCE_LIMIT) {
                throw new IllegalArgumentException("TMT source layer exceeds SSBO packing limit: " + sourceLayer);
            }
            if (flags < 0 || flags > FLAGS_LIMIT) {
                throw new IllegalArgumentException("TMT material flags exceed SSBO packing limit: " + flags);
            }
            int material = materialRow < 0 ? 0 : materialRow + 1;
            int source = sourceLayer < 0 ? 0 : sourceLayer + 1;
            return material | (source << MATERIAL_BITS) | (flags << (MATERIAL_BITS + SOURCE_BITS));
        }

        private void write(ByteBuffer buffer) {
            putMatrix(buffer, model);
            buffer.putFloat(minU).putFloat(minV).putFloat(maxU).putFloat(maxV);
            buffer.putInt(packed).putInt(0).putInt(0).putInt(0);
        }

        private static void putMatrix(ByteBuffer buffer, Matrix4f matrix) {
            buffer.putFloat(matrix.m00).putFloat(matrix.m10).putFloat(matrix.m20).putFloat(matrix.m30);
            buffer.putFloat(matrix.m01).putFloat(matrix.m11).putFloat(matrix.m21).putFloat(matrix.m31);
            buffer.putFloat(matrix.m02).putFloat(matrix.m12).putFloat(matrix.m22).putFloat(matrix.m32);
            buffer.putFloat(matrix.m03).putFloat(matrix.m13).putFloat(matrix.m23).putFloat(matrix.m33);
        }
    }
}
