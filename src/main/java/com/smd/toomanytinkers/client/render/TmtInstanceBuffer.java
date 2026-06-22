package com.smd.toomanytinkers.client.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import javax.vecmath.Matrix4f;
import java.nio.ByteBuffer;

public final class TmtInstanceBuffer {

    private static final int BINDING_POINT = 3;
    private static final int BYTES_PER_INSTANCE = 96;
    private static final int MASK_BITS = 12;
    private static final int MATERIAL_BITS = 14;
    private static final int TYPE_BITS = 2;
    private static final int FLAGS_BITS = 4;
    private static final int MASK_LIMIT = (1 << MASK_BITS) - 1;
    private static final int MATERIAL_LIMIT = (1 << MATERIAL_BITS) - 1;
    private static final int TYPE_LIMIT = (1 << TYPE_BITS) - 1;
    private static final int FLAGS_LIMIT = (1 << FLAGS_BITS) - 1;

    private int ssbo;
    private int capacity;
    private ByteBuffer uploadBuffer;
    private int uploadingInstances;

    public void beginUpload(int instances) {
        ensureBuffer();
        ensureCapacity(instances);
        ensureUploadBuffer(instances);
        uploadingInstances = instances;
        uploadBuffer.clear();
        uploadBuffer.limit(Math.max(1, instances) * BYTES_PER_INSTANCE);
    }

    public void putInstance(Matrix4f model,
                            int maskSlot,
                            int materialType,
                            int materialIndex,
                            int sourceIndex,
                            int flags) {
        putMatrix(uploadBuffer, model);
        uploadBuffer.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
        uploadBuffer.putInt(pack(maskSlot, materialType, materialIndex, flags)).putInt(sourceIndex).putInt(0).putInt(0);
    }

    public void putInstanceAt(int index,
                              Matrix4f model,
                              int maskSlot,
                              int materialType,
                              int materialIndex,
                              int sourceIndex,
                              int flags) {
        int offset = index * BYTES_PER_INSTANCE;
        putMatrixAt(uploadBuffer, offset, model);
        uploadBuffer.putFloat(offset + 64, 0f);
        uploadBuffer.putFloat(offset + 68, 0f);
        uploadBuffer.putFloat(offset + 72, 0f);
        uploadBuffer.putFloat(offset + 76, 0f);
        uploadBuffer.putInt(offset + 80, pack(maskSlot, materialType, materialIndex, flags));
        uploadBuffer.putInt(offset + 84, Math.max(0, sourceIndex));
        uploadBuffer.putInt(offset + 88, 0);
        uploadBuffer.putInt(offset + 92, 0);
    }

    public int finishUpload() {
        int instances = uploadingInstances;
        uploadBuffer.position(0);
        uploadBuffer.limit(Math.max(1, instances) * BYTES_PER_INSTANCE);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, uploadBuffer);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_POINT, ssbo);
        return instances;
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

    private static int pack(int maskSlot, int materialType, int materialIndex, int flags) {
        if (maskSlot < 0 || maskSlot > MASK_LIMIT) {
            throw new IllegalArgumentException("TMT mask slot exceeds SSBO packing limit: " + maskSlot);
        }
        if (materialType < 0 || materialType > TYPE_LIMIT) {
            throw new IllegalArgumentException("TMT material type exceeds SSBO packing limit: " + materialType);
        }
        if (materialIndex < 0 || materialIndex > MATERIAL_LIMIT) {
            throw new IllegalArgumentException("TMT material index exceeds SSBO packing limit: " + materialIndex);
        }
        if (flags < 0 || flags > FLAGS_LIMIT) {
            throw new IllegalArgumentException("TMT material flags exceed SSBO packing limit: " + flags);
        }
        return maskSlot
                | (materialIndex << MASK_BITS)
                | (materialType << (MASK_BITS + MATERIAL_BITS))
                | (flags << (MASK_BITS + MATERIAL_BITS + TYPE_BITS));
    }

    private static void putMatrix(ByteBuffer buffer, Matrix4f matrix) {
        buffer.putFloat(matrix.m00).putFloat(matrix.m10).putFloat(matrix.m20).putFloat(matrix.m30);
        buffer.putFloat(matrix.m01).putFloat(matrix.m11).putFloat(matrix.m21).putFloat(matrix.m31);
        buffer.putFloat(matrix.m02).putFloat(matrix.m12).putFloat(matrix.m22).putFloat(matrix.m32);
        buffer.putFloat(matrix.m03).putFloat(matrix.m13).putFloat(matrix.m23).putFloat(matrix.m33);
    }

    private static void putMatrixAt(ByteBuffer buffer, int offset, Matrix4f matrix) {
        buffer.putFloat(offset, matrix.m00);
        buffer.putFloat(offset + 4, matrix.m10);
        buffer.putFloat(offset + 8, matrix.m20);
        buffer.putFloat(offset + 12, matrix.m30);
        buffer.putFloat(offset + 16, matrix.m01);
        buffer.putFloat(offset + 20, matrix.m11);
        buffer.putFloat(offset + 24, matrix.m21);
        buffer.putFloat(offset + 28, matrix.m31);
        buffer.putFloat(offset + 32, matrix.m02);
        buffer.putFloat(offset + 36, matrix.m12);
        buffer.putFloat(offset + 40, matrix.m22);
        buffer.putFloat(offset + 44, matrix.m32);
        buffer.putFloat(offset + 48, matrix.m03);
        buffer.putFloat(offset + 52, matrix.m13);
        buffer.putFloat(offset + 56, matrix.m23);
        buffer.putFloat(offset + 60, matrix.m33);
    }
}
