package com.smd.toomanytinkers.client.render;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

public final class TmtInstancedPaletteShader {

    private static final String VERTEX = """
            #version 430 compatibility
            #extension GL_ARB_shader_draw_parameters : require
            in vec3 aPosition;
            in vec2 aTexCoord;
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
                InstanceData instance = uInstances[gl_BaseInstanceARB + gl_InstanceID];
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
            uniform int uMaterialWidth;
            uniform int uMaterialHeight;
            uniform int uUnitColumns;
            uniform int uSourceUnitBase;
            in vec2 vTex;
            in vec2 vLocalTex;
            flat in uvec4 vParams;
            out vec4 fragColor;

            vec2 unitOrigin(int unit) {
                int unitX = unit % uUnitColumns;
                int unitY = unit / uUnitColumns;
                return vec2(float(unitX * 256), float(unitY * 256));
            }

            vec2 spritePixel(vec2 localUv) {
                vec2 texel = floor(clamp(localUv, 0.0, 0.999999) * 16.0);
                return texel + vec2(0.5, 0.5);
            }

            vec4 sampleRamp(int rampIndex, float grey) {
                int unit = rampIndex / 256;
                int row = rampIndex - unit * 256;
                vec2 pixel = unitOrigin(unit) + vec2(clamp(grey, 0.0, 1.0) * 255.0 + 0.5, float(row) + 0.5);
                return texture(uMaterialMap, pixel / vec2(float(uMaterialWidth), float(uMaterialHeight)));
            }

            vec4 sampleSourceTile(int sourceIndex, vec2 localUv) {
                int sourceUnit = uSourceUnitBase + sourceIndex / 256;
                int local = sourceIndex - (sourceIndex / 256) * 256;
                int tileX = local % 16;
                int tileY = local / 16;
                vec2 pixel = unitOrigin(sourceUnit)
                        + vec2(float(tileX * 16), float(tileY * 16))
                        + spritePixel(localUv);
                return texture(uMaterialMap, pixel / vec2(float(uMaterialWidth), float(uMaterialHeight)));
            }

            void main() {
                uint layerParams = vParams.x;
                int maskSlot = int(layerParams & 0xfffu);
                int materialIndex = int((layerParams >> 12) & 0x3fffu);
                int materialType = int((layerParams >> 26) & 0x3u);
                int flags = int((layerParams >> 28) & 0xfu);
                int sourceIndex = int(vParams.y);

                vec2 maskPixel = spritePixel(vLocalTex);
                float maskX = maskPixel.x / 16.0;
                float maskY = (float(maskSlot) * 16.0 + maskPixel.y) / float(uMaskHeight);
                vec4 mask = texture(uMaskMap, vec2(maskX, maskY));
                if (mask.a < 0.1) discard;

                if (materialType == 0) {
                    fragColor = mask;
                    return;
                }

                vec4 mapped;
                if (materialType == 1) {
                    mapped = sampleRamp(materialIndex, mask.r);
                } else {
                    vec4 ramp = sampleRamp(materialIndex, mask.r);
                    vec4 source = sampleSourceTile(sourceIndex, vLocalTex);
                    mapped = ramp * source;
                }
                fragColor = vec4(mapped.rgb, mapped.a * mask.a);
            }
            """;

    private static int program;

    private TmtInstancedPaletteShader() {
    }

    public static boolean bind() {
        ensureProgram();
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uMaskMap"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uMaterialMap"), 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uMaskHeight"), TmtPartMaskMapManager.getHeight());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uMaterialWidth"), TmtMaterialMapManager.getWidth());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uMaterialHeight"), TmtMaterialMapManager.getHeight());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uUnitColumns"), TmtMaterialMapManager.getUnitColumns());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uSourceUnitBase"), TmtMaterialMapManager.getSourceUnitBase());
        return true;
    }

    public static void unbind() {
        GL20.glUseProgram(0);
    }

    private static void ensureProgram() {
        if (program != 0) {
            return;
        }
        if (!GLContext.getCapabilities().OpenGL43 || !GLContext.getCapabilities().GL_ARB_shader_draw_parameters) {
            throw new IllegalStateException("TMT GPU renderer requires OpenGL 4.3 + GL_ARB_shader_draw_parameters");
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
