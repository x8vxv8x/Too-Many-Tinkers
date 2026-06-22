package com.smd.toomanytinkers.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class TmtPaletteShader {

    private static final String VERTEX = """
            #version 120
            varying vec2 vTex;
            void main() {
                gl_Position = ftransform();
                vTex = gl_MultiTexCoord0.st;
            }
            """;

    private static final String FRAGMENT = """
            #version 120
            uniform sampler2D uAtlas;
            uniform sampler2D uLut;
            uniform sampler2D uSource;
            uniform int uLutHeight;
            uniform int uSourceHeight;
            uniform int uMaterialRow;
            uniform int uSourceLayer;
            varying vec2 vTex;
            void main() {
                vec4 param = texture2D(uAtlas, vTex);
                if (param.a < 0.1) discard;
                if (uMaterialRow < 0) {
                    gl_FragColor = param;
                } else {
                    float x = clamp(param.r, 0.0, 1.0);
                    float y = (float(uMaterialRow) + 0.5) / float(uLutHeight);
                    vec4 mapped = texture2D(uLut, vec2(x, y));
                    if (uSourceLayer >= 0) {
                        float sx = (clamp(param.g, 0.0, 1.0) * 15.0 + 0.5) / 16.0;
                        float sy = (float(uSourceLayer) * 16.0 + clamp(param.b, 0.0, 1.0) * 15.0 + 0.5) / float(uSourceHeight);
                        vec4 source = texture2D(uSource, vec2(sx, sy));
                        mapped = vec4(mapped.rgb * source.rgb, mapped.a * source.a);
                    }
                    gl_FragColor = vec4(mapped.rgb, mapped.a * param.a);
                }
            }
            """;

    private static int program;
    private static int materialRowUniform;
    private static int lutHeightUniform;
    private static int sourceHeightUniform;
    private static int sourceLayerUniform;

    private TmtPaletteShader() {
    }

    public static void bind() {
        ensureProgram();
        OpenGlHelper.glUseProgram(program);
        OpenGlHelper.glUniform1i(OpenGlHelper.glGetUniformLocation(program, "uAtlas"), 0);
        OpenGlHelper.glUniform1i(OpenGlHelper.glGetUniformLocation(program, "uLut"), 1);
        OpenGlHelper.glUniform1i(OpenGlHelper.glGetUniformLocation(program, "uSource"), 2);
        OpenGlHelper.glUniform1i(lutHeightUniform, MaterialLutManager.getHeight());
        OpenGlHelper.glUniform1i(sourceHeightUniform, MaterialSourceTextureManager.getHeight());
    }

    public static void setMaterial(int row, int sourceLayer) {
        ensureProgram();
        OpenGlHelper.glUniform1i(materialRowUniform, row);
        OpenGlHelper.glUniform1i(sourceLayerUniform, sourceLayer);
    }

    public static void unbind() {
        OpenGlHelper.glUseProgram(0);
    }

    private static void ensureProgram() {
        if (program != 0) {
            return;
        }
        int vertex = compile(OpenGlHelper.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(OpenGlHelper.GL_FRAGMENT_SHADER, FRAGMENT);
        program = OpenGlHelper.glCreateProgram();
        OpenGlHelper.glAttachShader(program, vertex);
        OpenGlHelper.glAttachShader(program, fragment);
        OpenGlHelper.glLinkProgram(program);
        if (OpenGlHelper.glGetProgrami(program, OpenGlHelper.GL_LINK_STATUS) == 0) {
            throw new IllegalStateException(OpenGlHelper.glGetProgramInfoLog(program, 32768));
        }
        OpenGlHelper.glDeleteShader(vertex);
        OpenGlHelper.glDeleteShader(fragment);
        materialRowUniform = OpenGlHelper.glGetUniformLocation(program, "uMaterialRow");
        lutHeightUniform = OpenGlHelper.glGetUniformLocation(program, "uLutHeight");
        sourceHeightUniform = OpenGlHelper.glGetUniformLocation(program, "uSourceHeight");
        sourceLayerUniform = OpenGlHelper.glGetUniformLocation(program, "uSourceLayer");
    }

    private static int compile(int shaderType, String source) {
        int shader = OpenGlHelper.glCreateShader(shaderType);
        OpenGlHelper.glShaderSource(shader, toBuffer(source));
        OpenGlHelper.glCompileShader(shader);
        if (OpenGlHelper.glGetShaderi(shader, OpenGlHelper.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException(OpenGlHelper.glGetShaderInfoLog(shader, 32768));
        }
        return shader;
    }

    private static ByteBuffer toBuffer(String source) {
        byte[] bytes = (source + "\0").getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }
}
