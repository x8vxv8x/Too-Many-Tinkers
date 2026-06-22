package com.smd.toomanytinkers.client.render;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

public final class TmtInstancedPaletteShader {

    private static final String VERTEX = """
            #version 430 compatibility
            in vec3 aPosition;
            in vec2 aTexCoord;
            uniform int uInstanceBase;
            struct InstanceData {
                mat4 model;
                vec4 reserved;
                uvec4 params;
            };
            layout(std430, binding = 3) readonly buffer TmtInstances {
                InstanceData uInstances[];
            };
            out vec2 vTex;
            out vec2 vLocalTex;
            flat out uvec4 vParams;
            void main() {
                InstanceData instance = uInstances[uInstanceBase + gl_InstanceID];
                gl_Position = gl_ModelViewProjectionMatrix * instance.model * vec4(aPosition, 1.0);
                vTex = aTexCoord;
                vLocalTex = aTexCoord;
                vParams = instance.params;
            }
            """;

    private static final String FRAGMENT = """
            #version 430 compatibility
            uniform sampler2D uMaskMap;
            uniform sampler2D uMaterialMap;
            uniform int uMaskHeight;
            uniform int uMaterialHeight;
            uniform int uSolidRows;
            uniform int uTextureBaseY;
            in vec2 vTex;
            in vec2 vLocalTex;
            flat in uvec4 vParams;
            out vec4 fragColor;
            void main() {
                uint layerParams = vParams.x;
                int maskSlot = int(layerParams & 0xfffu);
                int materialIndex = int((layerParams >> 12) & 0x3fffu);
                int materialType = int((layerParams >> 26) & 0x3u);
                int flags = int((layerParams >> 28) & 0xfu);

                float maskX = (clamp(vLocalTex.x, 0.0, 1.0) * 15.0 + 0.5) / 16.0;
                float maskY = (float(maskSlot) * 16.0 + clamp(vLocalTex.y, 0.0, 1.0) * 15.0 + 0.5) / float(uMaskHeight);
                vec4 mask = texture(uMaskMap, vec2(maskX, maskY));
                if (mask.a < 0.1) discard;

                if (materialType == 0) {
                    fragColor = mask;
                    return;
                }

                vec4 mapped;
                if (materialType == 1) {
                    float solidX = (float(materialIndex % 256) + 0.5) / 256.0;
                    float solidY = (float(materialIndex / 256) + 0.5) / float(uMaterialHeight);
                    vec4 solid = texture(uMaterialMap, vec2(solidX, solidY));
                    mapped = vec4(solid.rgb * mask.r, solid.a);
                } else if (materialType == 2) {
                    float rampX = clamp(mask.r, 0.0, 1.0);
                    float rampY = (float(uSolidRows + materialIndex) + 0.5) / float(uMaterialHeight);
                    mapped = texture(uMaterialMap, vec2(rampX, rampY));
                } else {
                    float grey = clamp(mask.r, 0.0, 1.0) * 255.0;
                    float greyFloor = floor(grey + 0.5);
                    float localX = clamp(vLocalTex.x, 0.0, 1.0) * 15.0;
                    float localY = clamp(vLocalTex.y, 0.0, 1.0) * 15.0;
                    float sourceX = (floor(greyFloor / 16.0) * 16.0 + localX + 0.5) / 256.0;
                    float sourceY = (float(uTextureBaseY + materialIndex * 256)
                            + mod(greyFloor, 16.0) * 16.0 + localY + 0.5) / float(uMaterialHeight);
                    mapped = texture(uMaterialMap, vec2(sourceX, sourceY));
                }
                fragColor = vec4(mapped.rgb, mapped.a * mask.a);
            }
            """;

    private static int program;
    private static int instanceBaseUniform;

    private TmtInstancedPaletteShader() {
    }

    public static boolean bind() {
        ensureProgram();
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uMaskMap"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uMaterialMap"), 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uMaskHeight"), TmtPartMaskMapManager.getHeight());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uMaterialHeight"), TmtMaterialMapManager.getHeight());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uSolidRows"), TmtMaterialMapManager.getSolidRows());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uTextureBaseY"), TmtMaterialMapManager.getTextureBaseY());
        return true;
    }

    public static void setInstanceBase(int instanceBase) {
        GL20.glUniform1i(instanceBaseUniform, instanceBase);
    }

    public static void unbind() {
        GL20.glUseProgram(0);
    }

    private static void ensureProgram() {
        if (program != 0) {
            return;
        }
        if (!GLContext.getCapabilities().OpenGL43) {
            throw new IllegalStateException("TMT GPU renderer requires OpenGL 4.3 / shader storage buffer objects");
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
        instanceBaseUniform = GL20.glGetUniformLocation(program, "uInstanceBase");
    }

    private static void bindAttributes(int targetProgram) {
        GL20.glBindAttribLocation(targetProgram, 0, "aPosition");
        GL20.glBindAttribLocation(targetProgram, 1, "aTexCoord");
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
