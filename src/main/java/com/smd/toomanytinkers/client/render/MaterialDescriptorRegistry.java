package com.smd.toomanytinkers.client.render;

import com.google.common.collect.ImmutableList;
import com.smd.toomanytinkers.TooManyTinkers;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.client.CustomTextureCreator;
import slimeknights.tconstruct.library.client.MaterialRenderInfo;
import slimeknights.tconstruct.library.materials.Material;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class MaterialDescriptorRegistry {

    private static final Map<String, MaterialDescriptor> DESCRIPTORS = new LinkedHashMap<>();
    private static final Set<ResourceLocation> REGISTERED_PARAM_MAPS = new LinkedHashSet<>();

    private static int rebuilds;

    private MaterialDescriptorRegistry() {
    }

    public static void rebuild(TextureMap map, Set<ResourceLocation> baseTextures) {
        DESCRIPTORS.clear();
        REGISTERED_PARAM_MAPS.clear();
        int row = 0;
        Set<String> suffixes = new LinkedHashSet<>();
        for (Material material : TinkerRegistry.getAllMaterials()) {
            MaterialDescriptor descriptor = createDescriptor(material, row++);
            DESCRIPTORS.put(material.identifier, descriptor);
            if (descriptor.hasTextureSuffix()) {
                suffixes.add(descriptor.getTextureSuffix());
            }
            if (descriptor.getSourceTexture() != null) {
                MaterialSourceTextureManager.registerSource(material.identifier, descriptor.getSourceTexture());
            }
        }

        int registeredMasks = registerParamMaps(map, baseTextures, suffixes);
        rebuilds++;
        TmtRenderStats.setMaterialDescriptorCounts(DESCRIPTORS.size(), registeredMasks, rebuilds);
        TooManyTinkers.LOGGER.info("TMT material descriptors: materials={}, paramMaps={}, rebuild={}",
                DESCRIPTORS.size(), registeredMasks, rebuilds);

        MaterialLutManager.markDirty();
        MaterialSourceTextureManager.markDirty();
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
        MaterialDescriptor descriptor = get(materialId);
        if (descriptor == null || !descriptor.hasTextureSuffix()) {
            return baseTexture;
        }
        ResourceLocation suffixTexture = new ResourceLocation(baseTexture + "_" + descriptor.getTextureSuffix());
        return REGISTERED_PARAM_MAPS.contains(suffixTexture) ? suffixTexture : baseTexture;
    }

    private static MaterialDescriptor createDescriptor(Material material, int row) {
        MaterialRenderInfo info = material.renderInfo;
        String suffix = info == null ? null : info.getTextureSuffix();
        int flags = 0;
        if (info != null && info.useVertexColoring()) {
            flags |= MaterialDescriptor.FLAG_VERTEX_COLOR;
        }
        if (suffix != null) {
            flags |= MaterialDescriptor.FLAG_TEXTURE_SUFFIX;
        }
        ResourceLocation source = null;
        MaterialRenderMode mode = source == null ? MaterialRenderMode.RAMP : MaterialRenderMode.RAMP_SOURCE;
        return new MaterialDescriptor(material.identifier, row, -1, flags, mode, suffix, source);
    }

    private static int registerParamMaps(TextureMap map, Set<ResourceLocation> baseTextures, Set<String> suffixes) {
        int registered = 0;
        for (ResourceLocation baseTexture : baseTextures) {
            if ("minecraft:missingno".equals(baseTexture.toString())) {
                continue;
            }
            for (String suffix : suffixes) {
                ResourceLocation paramMap = new ResourceLocation(baseTexture + "_" + suffix);
                if (CustomTextureCreator.exists(paramMap.toString())) {
                    map.registerSprite(paramMap);
                    REGISTERED_PARAM_MAPS.add(paramMap);
                    registered++;
                }
            }
        }
        return registered;
    }
}
