package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.client.model.TmtPartDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PartSpriteMeshCache {

    private static final int FLOATS_PER_VERTEX = 5;
    private static final float FRONT_Z = 0.53125f;
    private static final float BACK_Z = 0.46875f;
    private static final Map<Key, PartMesh> MESHES = new HashMap<>();

    private PartSpriteMeshCache() {
    }

    @Nullable
    public static PartMesh get(ResourceLocation texture, TmtPartDefinition definition) {
        Key key = new Key(texture, definition.getOffsetX(), definition.getOffsetY(), definition.getRotationDegrees());
        PartMesh existing = MESHES.get(key);
        if (existing != null) {
            return existing;
        }
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getTextureExtry(texture.toString());
        if (sprite == null) {
            sprite = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
        }
        PartMesh mesh = build(key, sprite);
        if (mesh != null) {
            MESHES.put(key, mesh);
        }
        return mesh;
    }

    public static void clear() {
        for (PartMesh mesh : MESHES.values()) {
            mesh.delete();
        }
        MESHES.clear();
    }

    @Nullable
    private static PartMesh build(Key key, TextureAtlasSprite sprite) {
        int width = Math.max(1, sprite.getIconWidth());
        int height = Math.max(1, sprite.getIconHeight());
        boolean[] opaque = readOpacity(sprite, width, height);

        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        addQuad(vertices, indices, key,
                0f, 0f, FRONT_Z, sprite.getInterpolatedU(0), sprite.getInterpolatedV(16),
                1f, 0f, FRONT_Z, sprite.getInterpolatedU(16), sprite.getInterpolatedV(16),
                1f, 1f, FRONT_Z, sprite.getInterpolatedU(16), sprite.getInterpolatedV(0),
                0f, 1f, FRONT_Z, sprite.getInterpolatedU(0), sprite.getInterpolatedV(0));

        addQuad(vertices, indices, key,
                0f, 1f, BACK_Z, sprite.getInterpolatedU(0), sprite.getInterpolatedV(0),
                1f, 1f, BACK_Z, sprite.getInterpolatedU(16), sprite.getInterpolatedV(0),
                1f, 0f, BACK_Z, sprite.getInterpolatedU(16), sprite.getInterpolatedV(16),
                0f, 0f, BACK_Z, sprite.getInterpolatedU(0), sprite.getInterpolatedV(16));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!opaque[y * width + x]) {
                    continue;
                }
                float x0 = x / (float) width;
                float x1 = (x + 1) / (float) width;
                float y0 = 1f - (y + 1) / (float) height;
                float y1 = 1f - y / (float) height;

                float u0 = sprite.getInterpolatedU(x * 16.0 / width);
                float u1 = sprite.getInterpolatedU((x + 1) * 16.0 / width);
                float v0 = sprite.getInterpolatedV((y + 1) * 16.0 / height);
                float v1 = sprite.getInterpolatedV(y * 16.0 / height);
                float uc = sprite.getInterpolatedU((x + 0.5) * 16.0 / width);
                float vc = sprite.getInterpolatedV((y + 0.5) * 16.0 / height);

                if (isTransparent(opaque, width, height, x - 1, y)) {
                    addQuad(vertices, indices, key,
                            x0, y0, BACK_Z, uc, vc,
                            x0, y0, FRONT_Z, uc, vc,
                            x0, y1, FRONT_Z, uc, vc,
                            x0, y1, BACK_Z, uc, vc);
                }
                if (isTransparent(opaque, width, height, x + 1, y)) {
                    addQuad(vertices, indices, key,
                            x1, y1, BACK_Z, uc, vc,
                            x1, y1, FRONT_Z, uc, vc,
                            x1, y0, FRONT_Z, uc, vc,
                            x1, y0, BACK_Z, uc, vc);
                }
                if (isTransparent(opaque, width, height, x, y - 1)) {
                    addQuad(vertices, indices, key,
                            x0, y1, BACK_Z, uc, vc,
                            x0, y1, FRONT_Z, uc, vc,
                            x1, y1, FRONT_Z, uc, vc,
                            x1, y1, BACK_Z, uc, vc);
                }
                if (isTransparent(opaque, width, height, x, y + 1)) {
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

    private static boolean[] readOpacity(TextureAtlasSprite sprite, int width, int height) {
        boolean[] opaque = new boolean[width * height];
        int[][] frames = sprite.getFrameTextureData(0);
        if (frames == null || frames.length == 0 || frames[0] == null) {
            java.util.Arrays.fill(opaque, true);
            return opaque;
        }
        int[] pixels = frames[0];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = Math.min(pixels.length - 1, y * width + x);
                opaque[y * width + x] = ((pixels[index] >>> 24) & 0xff) > 16;
            }
        }
        return opaque;
    }

    private static boolean isTransparent(boolean[] opaque, int width, int height, int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height || !opaque[y * width + x];
    }

    private static void addQuad(List<Float> vertices, List<Integer> indices, Key key,
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

    private static void addVertex(List<Float> vertices, Key key, float x, float y, float z, float u, float v) {
        x += key.offsetX / 16f;
        y -= key.offsetY / 16f;
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

        private Key(ResourceLocation texture, int offsetX, int offsetY, float rotationDegrees) {
            this.texture = texture;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.rotationDegrees = rotationDegrees;
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
                    && texture.equals(other.texture);
        }

        @Override
        public int hashCode() {
            return Objects.hash(texture, offsetX, offsetY, Float.floatToIntBits(rotationDegrees));
        }
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
