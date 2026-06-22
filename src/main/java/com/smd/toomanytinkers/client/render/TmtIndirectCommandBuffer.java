package com.smd.toomanytinkers.client.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL40;

import java.nio.ByteBuffer;

public final class TmtIndirectCommandBuffer {

    private static final int BYTES_PER_COMMAND = 20;

    private int buffer;
    private int capacity;
    private ByteBuffer uploadBuffer;
    private int commandCount;

    public void begin(int commands) {
        ensureBuffer();
        ensureCapacity(commands);
        ensureUploadBuffer(commands);
        commandCount = commands;
        uploadBuffer.clear();
        uploadBuffer.limit(Math.max(1, commands) * BYTES_PER_COMMAND);
    }

    public void putDraw(int index,
                        int indexCount,
                        int instanceCount,
                        int firstIndex,
                        int baseVertex,
                        int baseInstance) {
        int offset = index * BYTES_PER_COMMAND;
        uploadBuffer.putInt(offset, indexCount);
        uploadBuffer.putInt(offset + 4, instanceCount);
        uploadBuffer.putInt(offset + 8, firstIndex);
        uploadBuffer.putInt(offset + 12, baseVertex);
        uploadBuffer.putInt(offset + 16, baseInstance);
    }

    public int uploadAndBind() {
        uploadBuffer.position(0);
        uploadBuffer.limit(Math.max(1, commandCount) * BYTES_PER_COMMAND);
        GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, buffer);
        GL15.glBufferSubData(GL40.GL_DRAW_INDIRECT_BUFFER, 0L, uploadBuffer);
        return commandCount;
    }

    public static int getStride() {
        return BYTES_PER_COMMAND;
    }

    public static void unbind() {
        GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    private void ensureBuffer() {
        if (buffer == 0) {
            buffer = GL15.glGenBuffers();
        }
    }

    private void ensureCapacity(int commands) {
        if (commands <= capacity) {
            return;
        }
        capacity = nextPowerOfTwo(commands);
        GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, buffer);
        GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER,
                Math.max(1, capacity) * (long) BYTES_PER_COMMAND,
                GL15.GL_STREAM_DRAW);
    }

    private void ensureUploadBuffer(int commands) {
        int bytes = Math.max(1, commands) * BYTES_PER_COMMAND;
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
}
