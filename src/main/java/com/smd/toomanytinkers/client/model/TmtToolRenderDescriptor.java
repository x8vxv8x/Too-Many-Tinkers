package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.Arrays;
import java.util.Map;

public final class TmtToolRenderDescriptor {

    public static final class GeometryRef {
        private final ResourceLocation shapeTexture;
        private final TmtPartDefinition definition;

        private GeometryRef(ResourceLocation shapeTexture, TmtPartDefinition definition) {
            this.shapeTexture = shapeTexture;
            this.definition = definition;
        }

        public static GeometryRef sprite(ResourceLocation shapeTexture, TmtPartDefinition definition) {
            return new GeometryRef(shapeTexture, definition);
        }

        public ResourceLocation getShapeTexture() {
            return shapeTexture;
        }

        public TmtPartDefinition getDefinition() {
            return definition;
        }
    }

    public static final class Layer {
        private final GeometryRef geometry;
        private final TmtPartDefinition definition;
        private final ResourceLocation baseTexture;
        private final String materialId;
        private final Matrix4f transform;
        private final int flags;

        private Layer(TmtPartDefinition definition,
                      ResourceLocation baseTexture,
                      @Nullable String materialId,
                      Matrix4f transform,
                      int flags) {
            this.definition = definition;
            this.baseTexture = baseTexture;
            this.materialId = materialId;
            this.geometry = GeometryRef.sprite(baseTexture, definition);
            this.transform = new Matrix4f(transform);
            this.flags = flags;
        }

        public GeometryRef getGeometry() {
            return geometry;
        }

        public TmtPartDefinition getDefinition() {
            return definition;
        }

        public ResourceLocation getBaseTexture() {
            return baseTexture;
        }

        @Nullable
        public String getMaterialId() {
            return materialId;
        }

        public Matrix4f getTransform() {
            return new Matrix4f(transform);
        }

        public int getFlags() {
            return flags;
        }
    }

    private final TmtToolDefinition definition;
    private final ImmutableList<Layer> layers;

    private TmtToolRenderDescriptor(TmtToolDefinition definition, ImmutableList<Layer> layers) {
        this.definition = definition;
        this.layers = layers;
    }

    public static TmtToolRenderDescriptor create(TmtToolDefinition definition, ItemStack stack) {
        ImmutableList.Builder<Layer> builder = ImmutableList.builder();
        NBTTagList materials = TagUtil.getBaseMaterialsTagList(stack);
        boolean broken = ToolHelper.isBroken(stack);

        for (int i = 0; i < definition.getParts().size(); i++) {
            String materialId = i < materials.tagCount() ? materials.getStringTagAt(i) : "";
            TmtPartDefinition part = broken && definition.getBrokenParts().get(i) != null
                    ? definition.getBrokenParts().get(i)
                    : definition.getParts().get(i);
            addPartLayers(part, materialId, builder);
        }

        addModifierParts(definition, stack, builder);
        return new TmtToolRenderDescriptor(definition, builder.build());
    }

    public static ImmutableList<Layer> createPartLayers(TmtPartDefinition definition, @Nullable String materialId) {
        ImmutableList.Builder<Layer> builder = ImmutableList.builder();
        addPartLayers(definition, materialId, builder);
        return builder.build();
    }

    public TmtToolDefinition getDefinition() {
        return definition;
    }

    public ImmutableList<Layer> getLayers() {
        return layers;
    }

    private static void addModifierParts(TmtToolDefinition definition, ItemStack stack,
                                         ImmutableList.Builder<Layer> builder) {
        boolean incognito = false;
        NBTTagList modifiers = TagUtil.getBaseModifiersTagList(stack);
        if (modifiers.toString().contains("incognito")) {
            incognito = true;
        }

        Map<String, String> modifierTextures = definition.getModifierTextures();
        for (int i = 0; i < modifiers.tagCount(); i++) {
            String modId = modifiers.getStringTagAt(i);
            if (incognito && !modId.equals("incognito") && !Arrays.asList(Config.incognitoModBlacklist).contains(modId)) {
                continue;
            }
            String texture = modifierTextures.get(modId);
            if (texture != null) {
                addPartLayers(TmtPartDefinition.singleTexture(new ResourceLocation(texture), 0f), null, builder);
            }
        }
    }

    private static void addPartLayers(TmtPartDefinition definition,
                                      @Nullable String materialId,
                                      ImmutableList.Builder<Layer> builder) {
        String resolvedMaterial = materialId == null || materialId.isEmpty() ? null : materialId;
        Matrix4f transform = identity();
        for (ResourceLocation texture : definition.getTextures()) {
            builder.add(new Layer(definition, texture, resolvedMaterial, transform, 0));
        }
    }

    private static Matrix4f identity() {
        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        return matrix;
    }
}
