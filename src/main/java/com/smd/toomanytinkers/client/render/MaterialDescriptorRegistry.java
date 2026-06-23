package com.smd.toomanytinkers.client.render;

import com.google.common.collect.ImmutableList;
import com.smd.toomanytinkers.TooManyTinkers;
import com.smd.toomanytinkers.client.model.TmtGpuToolTemplateModel;
import com.smd.toomanytinkers.client.model.TmtPartMaterialModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.client.MaterialRenderInfo;
import slimeknights.tconstruct.library.materials.Material;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class MaterialDescriptorRegistry {

    private static final Map<String, MaterialDescriptor> DESCRIPTORS = new LinkedHashMap<>();
    private static final Set<ResourceLocation> REGISTERED_MASKS = new LinkedHashSet<>();

    private static int rebuilds;

    private MaterialDescriptorRegistry() {
    }

    public static void rebuild(TextureMap map, Set<ResourceLocation> baseTextures) {
        DESCRIPTORS.clear();
        REGISTERED_MASKS.clear();
        TmtPartMaskMapManager.rebuildStart();
        TmtMaterialMapManager.rebuildStart();
        Set<String> suffixes = new LinkedHashSet<>();
        for (Material material : TinkerRegistry.getAllMaterials()) {
            MaterialDescriptor descriptor = createDescriptor(material);
            DESCRIPTORS.put(material.identifier, descriptor);
            if (descriptor.hasTextureSuffix()) {
                suffixes.add(descriptor.getTextureSuffix());
            }
        }
        TmtMaterialMapManager.finishUpload();

        int registeredMasks = registerMasks(baseTextures, suffixes);
        rebuilds++;
        TmtRenderStats.setMaterialDescriptorCounts(DESCRIPTORS.size(),
                registeredMasks,
                TmtMaterialMapManager.getSlotCount(),
                rebuilds);
        TooManyTinkers.LOGGER.info("TMT material descriptors: materials={}, masks={}, materialSlots={}, rebuild={}",
                DESCRIPTORS.size(), registeredMasks, TmtMaterialMapManager.getSlotCount(), rebuilds);

        TmtGpuToolTemplateModel.invalidateCaches();
        TmtPartMaterialModel.invalidateCaches();
    }

    @Nullable
    public static MaterialDescriptor get(String materialId) {
        return DESCRIPTORS.get(materialId);
    }

    public static ImmutableList<MaterialDescriptor> getDescriptors() {
        return ImmutableList.copyOf(DESCRIPTORS.values());
    }

    public static int getRow(String materialId) {
        MaterialDescriptor descriptor = get(materialId);
        return descriptor == null ? -1 : descriptor.getRampRow();
    }

    public static ResourceLocation resolveParamMap(ResourceLocation baseTexture, String materialId) {
        return resolveMaskTexture(baseTexture, materialId);
    }

    public static ResourceLocation resolveMaskTexture(ResourceLocation baseTexture, String materialId) {
        MaterialDescriptor descriptor = get(materialId);
        if (descriptor == null || !descriptor.hasTextureSuffix()) {
            return baseTexture;
        }
        ResourceLocation suffixTexture = new ResourceLocation(baseTexture + "_" + descriptor.getTextureSuffix());
        return REGISTERED_MASKS.contains(suffixTexture) ? suffixTexture : baseTexture;
    }

    public static int getMaskSlot(ResourceLocation texture) {
        return TmtPartMaskMapManager.getSlot(texture);
    }

    public static TmtMaskBits getOpacity(ResourceLocation texture) {
        return TmtPartMaskMapManager.getOpacity(texture);
    }

    private static MaterialDescriptor createDescriptor(Material material) {
        MaterialRenderInfo info = material.renderInfo;
        String suffix = info == null ? null : info.getTextureSuffix();
        int flags = 0;
        if (info != null && info.useVertexColoring()) {
            flags |= MaterialDescriptor.FLAG_VERTEX_COLOR;
        }
        if (suffix != null) {
            flags |= MaterialDescriptor.FLAG_TEXTURE_SUFFIX;
        }
        ResourceLocation source = findSourceTexture(info);
        if (source != null) {
            flags |= MaterialDescriptor.FLAG_SOURCE_TEXTURE;
        }
        if (source != null && info.getClass().getName().contains("AnimatedTexture")) {
            flags |= MaterialDescriptor.FLAG_ANIMATED;
        }
        TmtMaterialMapManager.Allocation allocation = TmtMaterialMapManager.addMaterial(material, source);
        MaterialRenderMode mode = source == null ? MaterialRenderMode.RAMP : MaterialRenderMode.RAMP_SOURCE;
        return new MaterialDescriptor(material.identifier,
                allocation.getType(),
                allocation.getIndex(),
                allocation.getSourceIndex(),
                -1,
                flags,
                mode,
                suffix,
                source);
    }

    @Nullable
    private static ResourceLocation findSourceTexture(@Nullable MaterialRenderInfo info) {
        if (info == null) {
            return null;
        }
        Object texturePath = readField(info, "texturePath");
        if (texturePath instanceof ResourceLocation) {
            return (ResourceLocation) texturePath;
        }
        if (texturePath instanceof String) {
            return new ResourceLocation((String) texturePath);
        }
        Object extraTexture = readField(info, "extraTexture");
        return extraTexture instanceof ResourceLocation ? (ResourceLocation) extraTexture : null;
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

    private static int registerMasks(Set<ResourceLocation> baseTextures, Set<String> suffixes) {
        int registered = 0;
        for (ResourceLocation baseTexture : baseTextures) {
            if ("minecraft:missingno".equals(baseTexture.toString())) {
                continue;
            }
            TmtPartMaskMapManager.registerMask(baseTexture);
            REGISTERED_MASKS.add(baseTexture);
            registered++;
            for (String suffix : suffixes) {
                ResourceLocation paramMap = new ResourceLocation(baseTexture + "_" + suffix);
                if (CustomTextureCreator.exists(paramMap.toString())) {
                    TmtPartMaskMapManager.registerMask(paramMap);
                    REGISTERED_MASKS.add(paramMap);
                    registered++;
                }
            }
        }
        return registered;
    }
}
