package com.smd.toomanytinkers.client.render;

import com.smd.toomanytinkers.TooManyTinkers;
import org.lwjgl.opengl.GL20;

public final class TmtInstancedPaletteShader {

    private static final String VERTEX = """
            #version 150 compatibility
            in vec3 aPosition;
            in vec2 aTexCoord;
            in vec4 iModel0;
            in vec4 iModel1;
            in vec4 iModel2;
            in vec4 iModel3;
            in vec4 iParams;
            out vec2 vTex;
            flat out vec4 vParams;
            void main() {
                mat4 model = mat4(iModel0, iModel1, iModel2, iModel3);
                gl_Position = gl_ModelViewProjectionMatrix * model * vec4(aPosition, 1.0);
                vTex = aTexCoord;
                vParams = iParams;
            }
            """;

    private static final String FRAGMENT = """
            #version 150 compatibility
            uniform sampler2D uAtlas;
            uniform sampler2D uLut;
            uniform sampler2D uSource;
            uniform int uLutHeight;
            uniform int uSourceHeight;
            in vec2 vTex;
            flat in vec4 vParams;
            out vec4 fragColor;
            void main() {
                vec4 param = texture(uAtlas, vTex);
                if (param.a < 0.1) discard;
                int materialRow = int(vParams.x);
                int sourceLayer = int(vParams.y);
                if (materialRow < 0) {
                    fragColor = param;
                } else {
                    float x = clamp(param.r, 0.0, 1.0);
                    float y = (float(materialRow) + 0.5) / float(uLutHeight);
                    vec4 mapped = texture(uLut, vec2(x, y));
                    if (sourceLayer >= 0) {
                        float sx = (clamp(param.g, 0.0, 1.0) * 15.0 + 0.5) / 16.0;
                        float sy = (float(sourceLayer) * 16.0 + clamp(param.b, 0.0, 1.0) * 15.0 + 0.5) / float(uSourceHeight);
                        vec4 source = texture(uSource, vec2(sx, sy));
                        mapped = vec4(mapped.rgb * source.rgb, mapped.a * source.a);
                    }
                    fragColor = vec4(mapped.rgb, mapped.a * param.a);
                }
            }
            """;

    private static int program;
    private static boolean disabled;

    private TmtInstancedPaletteShader() {
    }

    public static boolean bind() {
        if (disabled) {
            return false;
        }
        try {
            ensureProgram();
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uAtlas"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uLut"), 1);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uSource"), 2);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uLutHeight"), MaterialLutManager.getHeight());
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uSourceHeight"), MaterialSourceTextureManager.getHeight());
            return true;
        } catch (RuntimeException e) {
            disabled = true;
            TooManyTinkers.LOGGER.warn("TMT instanced shader disabled, falling back to legacy GPU path", e);
            return false;
        }
    }

    public static void unbind() {
        GL20.glUseProgram(0);
    }

    private static void ensureProgram() {
        if (program != 0) {
            return;
        }
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        program = GL20.glCreateProgram();
        bindAttributes(program);
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 32768));
        }
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
    }

    private static void bindAttributes(int targetProgram) {
        GL20.glBindAttribLocation(targetProgram, 0, "aPosition");
        GL20.glBindAttribLocation(targetProgram, 1, "aTexCoord");
        GL20.glBindAttribLocation(targetProgram, 2, "iModel0");
        GL20.glBindAttribLocation(targetProgram, 3, "iModel1");
        GL20.glBindAttribLocation(targetProgram, 4, "iModel2");
        GL20.glBindAttribLocation(targetProgram, 5, "iModel3");
        GL20.glBindAttribLocation(targetProgram, 6, "iParams");
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 32768));
        }
        return shader;
    }
}
