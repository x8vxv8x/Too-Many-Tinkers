package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import com.smd.toomanytinkers.client.render.MaterialDescriptor;
import com.smd.toomanytinkers.client.render.MaterialDescriptorRegistry;
import com.smd.toomanytinkers.client.render.TmtMaskBits;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TmtToolRenderDescriptor {

    private static final float MODIFIER_Z_BIAS = 1f / 1024f;

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
        private final ResourceLocation maskTexture;
        private final String materialId;
        private final int maskSlot;
        private final int materialType;
        private final int materialIndex;
        private final int sourceIndex;
        private final Matrix4f transform;
        private final int flags;
        private final TmtMaskBits sideOpaque;
        private final TmtMaskBits compositeOpaque;

        private Layer(TmtPartDefinition definition,
                      ResourceLocation baseTexture,
                      @Nullable String materialId,
                      Matrix4f transform,
                      int flags,
                      TmtMaskBits sideOpaque,
                      TmtMaskBits compositeOpaque) {
            this.definition = definition;
            this.baseTexture = baseTexture;
            this.materialId = materialId;
            this.maskTexture = MaterialDescriptorRegistry.resolveMaskTexture(baseTexture, materialId);
            MaterialDescriptor descriptor = materialId == null ? null : MaterialDescriptorRegistry.get(materialId);
            this.maskSlot = MaterialDescriptorRegistry.getMaskSlot(maskTexture);
            this.materialType = descriptor == null ? MaterialDescriptor.TYPE_DIRECT : descriptor.getMaterialType();
            this.materialIndex = descriptor == null ? 0 : descriptor.getMaterialIndex();
            this.sourceIndex = descriptor == null ? 0 : descriptor.getSourceIndex();
            this.flags = descriptor == null ? flags : descriptor.getFlags() | flags;
            this.geometry = GeometryRef.sprite(maskTexture, definition);
            this.transform = new Matrix4f(transform);
            this.sideOpaque = sideOpaque;
            this.compositeOpaque = compositeOpaque;
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

        public ResourceLocation getMaskTexture() {
            return maskTexture;
        }

        @Nullable
        public String getMaterialId() {
            return materialId;
        }

        public int getMaskSlot() {
            return maskSlot;
        }

        public int getMaterialType() {
            return materialType;
        }

        public int getMaterialIndex() {
            return materialIndex;
        }

        public int getSourceIndex() {
            return sourceIndex;
        }

        public Matrix4f getTransform() {
            return new Matrix4f(transform);
        }

        public Matrix4f getTransformForRender() {
            return transform;
        }

        public int getFlags() {
            return flags;
        }

        public TmtMaskBits getSideOpaque() {
            return sideOpaque;
        }

        public TmtMaskBits getCompositeOpaque() {
            return compositeOpaque;
        }
    }

    private static final class LayerInput {
        private final TmtPartDefinition definition;
        private final ResourceLocation baseTexture;
        @Nullable
        private final String materialId;
        private final Matrix4f transform;
        private final int flags;
        private ResourceLocation maskTexture;
        private TmtMaskBits opacity;

        private LayerInput(TmtPartDefinition definition,
                           ResourceLocation baseTexture,
                           @Nullable String materialId,
                           Matrix4f transform,
                           int flags) {
            this.definition = definition;
            this.baseTexture = baseTexture;
            this.materialId = materialId;
            this.transform = transform;
            this.flags = flags;
        }
    }

    private final TmtToolDefinition definition;
    private final ImmutableList<Layer> layers;

    private TmtToolRenderDescriptor(TmtToolDefinition definition, ImmutableList<Layer> layers) {
        this.definition = definition;
        this.layers = layers;
    }

    public static TmtToolRenderDescriptor create(TmtToolDefinition definition, ItemStack stack) {
        List<LayerInput> inputs = new ArrayList<>();
        NBTTagList materials = TagUtil.getBaseMaterialsTagList(stack);
        boolean broken = ToolHelper.isBroken(stack);

        for (int i = 0; i < definition.getParts().size(); i++) {
            String materialId = i < materials.tagCount() ? materials.getStringTagAt(i) : "";
            TmtPartDefinition part = broken && definition.getBrokenParts().get(i) != null
                    ? definition.getBrokenParts().get(i)
                    : definition.getParts().get(i);
            addPartLayerInputs(part, materialId, inputs);
        }

        addModifierParts(definition, stack, inputs);
        return new TmtToolRenderDescriptor(definition, buildLayers(inputs));
    }

    public static ImmutableList<Layer> createPartLayers(TmtPartDefinition definition, @Nullable String materialId) {
        List<LayerInput> inputs = new ArrayList<>();
        addPartLayerInputs(definition, materialId, inputs);
        return buildLayers(inputs);
    }

    public TmtToolDefinition getDefinition() {
        return definition;
    }

    public ImmutableList<Layer> getLayers() {
        return layers;
    }

    private static void addModifierParts(TmtToolDefinition definition, ItemStack stack,
                                         List<LayerInput> output) {
        boolean incognito = false;
        NBTTagList modifiers = TagUtil.getBaseModifiersTagList(stack);
        if (modifiers.toString().contains("incognito")) {
            incognito = true;
        }

        Map<String, String> modifierTextures = definition.getModifierTextures();
        int visibleModifierIndex = 0;
        for (int i = 0; i < modifiers.tagCount(); i++) {
            String modId = modifiers.getStringTagAt(i);
            if (incognito && !modId.equals("incognito") && !isIncognitoVisible(modId)) {
                continue;
            }
            String texture = modifierTextures.get(modId);
            if (texture != null) {
                float zBias = MODIFIER_Z_BIAS * ++visibleModifierIndex;
                addPartLayerInputs(TmtPartDefinition.singleTexture(new ResourceLocation(texture), zBias), null, output);
            }
        }
    }

    private static void addPartLayerInputs(TmtPartDefinition definition,
                                           @Nullable String materialId,
                                           List<LayerInput> output) {
        String resolvedMaterial = materialId == null || materialId.isEmpty() ? null : materialId;
        Matrix4f transform = identity();
        for (ResourceLocation texture : definition.getTextures()) {
            output.add(new LayerInput(definition, texture, resolvedMaterial, transform, 0));
        }
    }

    private static ImmutableList<Layer> buildLayers(List<LayerInput> inputs) {
        if (inputs.isEmpty()) {
            return ImmutableList.of();
        }

        TmtMaskBits.Builder composite = TmtMaskBits.builder();
        int[] owner = new int[16 * 16];
        java.util.Arrays.fill(owner, -1);

        for (int i = 0; i < inputs.size(); i++) {
            LayerInput input = inputs.get(i);
            input.maskTexture = MaterialDescriptorRegistry.resolveMaskTexture(input.baseTexture, input.materialId);
            input.opacity = MaterialDescriptorRegistry.getOpacity(input.maskTexture);
            for (int pixel = 0; pixel < TmtMaskBits.SIZE; pixel++) {
                if (input.opacity.get(pixel)) {
                    composite.set(pixel);
                    owner[pixel] = i;
                }
            }
        }

        TmtMaskBits compositeMask = composite.build();
        TmtMaskBits.Builder[] sides = new TmtMaskBits.Builder[inputs.size()];
        for (int i = 0; i < sides.length; i++) {
            sides[i] = TmtMaskBits.builder();
        }
        for (int pixel = 0; pixel < owner.length; pixel++) {
            int layer = owner[pixel];
            if (layer >= 0) {
                sides[layer].set(pixel);
            }
        }

        ImmutableList.Builder<Layer> builder = ImmutableList.builder();
        for (int i = 0; i < inputs.size(); i++) {
            LayerInput input = inputs.get(i);
            builder.add(new Layer(input.definition,
                    input.baseTexture,
                    input.materialId,
                    input.transform,
                    input.flags,
                    sides[i].build(),
                    compositeMask));
        }
        return builder.build();
    }

    private static boolean isIncognitoVisible(String modifier) {
        for (String allowed : Config.incognitoModBlacklist) {
            if (allowed.equals(modifier)) {
                return true;
            }
        }
        return false;
    }

    private static Matrix4f identity() {
        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        return matrix;
    }
}
