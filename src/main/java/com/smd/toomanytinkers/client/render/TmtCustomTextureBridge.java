package com.smd.toomanytinkers.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.client.MaterialRenderInfo;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.tools.IToolPart;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immediate material texture bridge. It keeps TiC texture registration metadata
 * but never stores generated image data long-term.
 */
public final class TmtCustomTextureBridge {

    private static final Map<ResourceLocation, Set<IToolPart>> TEXTURE_PARTS = new HashMap<>();

    private TmtCustomTextureBridge() {
    }

    public static void rebuild(Set<ResourceLocation> baseTextures,
                               Map<ResourceLocation, Set<IToolPart>> texturePartMapping) {
        TEXTURE_PARTS.clear();
        CustomTextureCreator.sprites.clear();
        for (ResourceLocation base : baseTextures) {
            Set<IToolPart> parts = texturePartMapping.get(base);
            if (parts != null && !parts.isEmpty()) {
                TEXTURE_PARTS.put(base, new LinkedHashSet<>(parts));
            }
        }
    }

    public static void clear() {
        TEXTURE_PARTS.clear();
        CustomTextureCreator.sprites.clear();
    }

    @Nullable
    public static BufferedImage createMaterialImage(String baseTextureName, Material material) {
        String materialTexture = baseTextureName + "_" + material.identifier;
        BufferedImage direct = loadImage(materialTexture);
        if (direct != null) {
            return direct;
        }

        ResourceLocation baseTexture = new ResourceLocation(baseTextureName);
        MaterialRenderInfo info = material.renderInfo;
        if (info != null && info.getTextureSuffix() != null) {
            String suffixed = baseTextureName + "_" + info.getTextureSuffix();
            if (textureExists(suffixed)) {
                baseTexture = new ResourceLocation(suffixed);
            }
        }

        BufferedImage base = loadImage(baseTexture);
        if (base == null) {
            return null;
        }
        if (info == null || info.useVertexColoring()) {
            return base;
        }

        BufferedImage extra = resolveExtraImage(info);
        BufferedImage output = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        boolean metal = info.getClass().getName().contains("Metal");
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int argb = base.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                if (alpha == 0) {
                    output.setRGB(x, y, argb);
                    continue;
                }

                int source = argb;
                if (extra != null) {
                    int texel = extra.getRGB(x % extra.getWidth(), y % extra.getHeight());
                    int r = mult(mult((texel >> 16) & 0xff, (argb >> 16) & 0xff), (argb >> 16) & 0xff);
                    int g = mult(mult((texel >> 8) & 0xff, (argb >> 8) & 0xff), (argb >> 8) & 0xff);
                    int b = mult(mult(texel & 0xff, argb & 0xff), argb & 0xff);
                    source = (alpha << 24) | (r << 16) | (g << 8) | b;
                    if (!metal) {
                        output.setRGB(x, y, source);
                        continue;
                    }
                }

                int gray = perceptualBrightness(source);
                int colored = MaterialColorEvaluator.evaluate(material, gray / 255f);
                output.setRGB(x, y, (alpha << 24) | (colored & 0x00ffffff));
            }
        }
        return output;
    }

    @Nullable
    public static BufferedImage createTextureImage(String textureName) {
        return loadImage(textureName);
    }

    public static boolean textureExists(String textureName) {
        ResourceLocation file = normalizeTextureFile(new ResourceLocation(textureName));
        try {
            java.util.List<IResource> resources = Minecraft.getMinecraft().getResourceManager().getAllResources(file);
            for (IResource resource : resources) {
                try {
                    resource.close();
                } catch (IOException ignored) {
                }
            }
            return !resources.isEmpty();
        } catch (IOException ignored) {
            return false;
        }
    }

    @Nullable
    private static BufferedImage loadImage(String textureName) {
        return loadImage(new ResourceLocation(textureName));
    }

    @Nullable
    private static BufferedImage loadImage(ResourceLocation texture) {
        ResourceLocation file = normalizeTextureFile(texture);
        try (InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(file).getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return null;
            }
            BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
            Graphics graphics = copy.getGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
            return copy;
        } catch (IOException ignored) {
            return null;
        }
    }

    @Nullable
    private static BufferedImage resolveExtraImage(MaterialRenderInfo info) {
        ResourceLocation texture = readResourceLocation(info, "texturePath");
        if (texture == null) {
            texture = readResourceLocation(info, "extraTexture");
        }
        return texture == null ? null : loadImage(texture);
    }

    private static ResourceLocation normalizeTextureFile(ResourceLocation texture) {
        String path = texture.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path += ".png";
        }
        return new ResourceLocation(texture.getNamespace(), path);
    }

    private static int perceptualBrightness(int argb) {
        double r = ((argb >> 16) & 0xff) / 255.0;
        double g = ((argb >> 8) & 0xff) / 255.0;
        double b = (argb & 0xff) / 255.0;
        return clamp255((int) (Math.sqrt(0.241 * r * r + 0.691 * g * g + 0.068 * b * b) * 255));
    }

    private static int mult(int a, int b) {
        return clamp255((int) (a * (b / 255f)));
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Nullable
    private static ResourceLocation readResourceLocation(Object instance, String name) {
        Object value = readField(instance, name);
        if (value instanceof ResourceLocation) {
            return (ResourceLocation) value;
        }
        if (value instanceof String) {
            return new ResourceLocation(((String) value).toLowerCase(Locale.ROOT));
        }
        return null;
    }

    @Nullable
    private static Object readField(Object instance, String name) {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }
}
