package com.smd.toomanytinkers.client.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;

import java.nio.FloatBuffer;
import java.util.List;

public final class TmtInstanceBuffer {

    private static final int FLOATS_PER_INSTANCE = 20;
    private static final int BYTES_PER_INSTANCE = FLOATS_PER_INSTANCE * Float.BYTES;

    private int vbo;
    private int capacity;

    public int upload(List<InstanceData> instances) {
        ensureBuffer();
        ensureCapacity(instances.size());

        FloatBuffer buffer = BufferUtils.createFloatBuffer(instances.size() * FLOATS_PER_INSTANCE);
        for (InstanceData instance : instances) {
            instance.write(buffer);
        }
        buffer.flip();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, Math.max(capacity, instances.size()) * (long) BYTES_PER_INSTANCE, GL15.GL_STREAM_DRAW);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, buffer);
        return instances.size();
    }

    public void bindAttributes() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        for (int i = 0; i < 4; i++) {
            int index = 2 + i;
            GL20.glEnableVertexAttribArray(index);
            GL20.glVertexAttribPointer(index, 4, GL11.GL_FLOAT, false, BYTES_PER_INSTANCE, (long) i * 4L * Float.BYTES);
            GL33.glVertexAttribDivisor(index, 1);
        }
        GL20.glEnableVertexAttribArray(6);
        GL20.glVertexAttribPointer(6, 4, GL11.GL_FLOAT, false, BYTES_PER_INSTANCE, 16L * Float.BYTES);
        GL33.glVertexAttribDivisor(6, 1);
    }

    private void ensureBuffer() {
        if (vbo == 0) {
            vbo = GL15.glGenBuffers();
        }
    }

    private void ensureCapacity(int instances) {
        if (instances > capacity) {
            capacity = nextPowerOfTwo(instances);
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
        private final int materialRow;
        private final int sourceLayer;
        private final int flags;

        public InstanceData(int materialRow, int sourceLayer, int flags) {
            this.materialRow = materialRow;
            this.sourceLayer = sourceLayer;
            this.flags = flags;
        }

        private void write(FloatBuffer buffer) {
            buffer.put(1f).put(0f).put(0f).put(0f);
            buffer.put(0f).put(1f).put(0f).put(0f);
            buffer.put(0f).put(0f).put(1f).put(0f);
            buffer.put(0f).put(0f).put(0f).put(1f);
            buffer.put(materialRow).put(sourceLayer).put(flags).put(0f);
        }
    }
}
