package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import slimeknights.tconstruct.library.client.model.MaterialModel;
import slimeknights.tconstruct.library.client.model.ModifierModel;
import slimeknights.tconstruct.library.client.model.format.AmmoPosition;
import slimeknights.tconstruct.library.client.model.format.ToolModelOverride;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TmtToolDefinition {

    private final ImmutableList<TmtPartDefinition> parts;
    private final List<TmtPartDefinition> brokenParts;
    private final ImmutableMap<String, String> modifierTextures;
    private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;
    private final ImmutableList<ToolModelOverride> overrides;
    private final AmmoPosition ammoPosition;
    private final Map<Integer, TmtToolDefinition> resolvedOverrideCache = new HashMap<>();

    public static final class Resolved {
        private final TmtToolDefinition definition;
        private final int signature;

        private Resolved(TmtToolDefinition definition, int signature) {
            this.definition = definition;
            this.signature = signature;
        }

        public TmtToolDefinition getDefinition() {
            return definition;
        }

        public int getSignature() {
            return signature;
        }
    }

    public TmtToolDefinition(List<MaterialModel> partModels,
                             List<MaterialModel> brokenPartModels,
                             Float[] layerRotations,
                             @Nullable ModifierModel modifiers,
                             IModelState state,
                             ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> extraTransforms,
                             ImmutableList<ToolModelOverride> overrides,
                             @Nullable AmmoPosition ammoPosition) {
        this(buildParts(partModels, layerRotations),
                buildBrokenParts(partModels.size(), brokenPartModels, layerRotations),
                modifiers == null ? ImmutableMap.of() : ImmutableMap.copyOf(modifiers.getModels()),
                mergeTransforms(state, extraTransforms),
                overrides,
                ammoPosition);
    }

    private TmtToolDefinition(ImmutableList<TmtPartDefinition> parts,
                              List<TmtPartDefinition> brokenParts,
                              ImmutableMap<String, String> modifierTextures,
                              ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms,
                              ImmutableList<ToolModelOverride> overrides,
                              @Nullable AmmoPosition ammoPosition) {
        this.parts = parts;
        this.brokenParts = new ArrayList<>(brokenParts);
        this.modifierTextures = modifierTextures;
        this.transforms = transforms;
        this.overrides = overrides;
        this.ammoPosition = ammoPosition;
    }

    public TmtToolDefinition resolveOverride(ItemStack stack, World world, EntityLivingBase entity) {
        return resolve(stack, world, entity).getDefinition();
    }

    public Resolved resolve(ItemStack stack, World world, EntityLivingBase entity) {
        int signature = 1;
        List<ToolModelOverride> matched = new ArrayList<>();
        for (int i = 0; i < overrides.size(); i++) {
            ToolModelOverride override = overrides.get(i);
            if (matches(override, stack, world, entity)) {
                signature = 31 * signature + i + 1;
                matched.add(override);
            }
        }
        if (matched.isEmpty()) {
            return new Resolved(this, 0);
        }
        TmtToolDefinition resolved = resolvedOverrideCache.get(signature);
        if (resolved == null) {
            TmtToolDefinition current = this;
            for (ToolModelOverride override : matched) {
                current = current.applyOverride(override);
            }
            resolved = current;
            resolvedOverrideCache.put(signature, resolved);
        }
        return new Resolved(resolved, signature);
    }

    public ImmutableList<TmtPartDefinition> getParts() {
        return parts;
    }

    public List<TmtPartDefinition> getBrokenParts() {
        return brokenParts;
    }

    public ImmutableMap<String, String> getModifierTextures() {
        return modifierTextures;
    }

    public ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> getTransforms() {
        return transforms;
    }

    @Nullable
    public AmmoPosition getAmmoPosition() {
        return ammoPosition;
    }

    private TmtToolDefinition applyOverride(ToolModelOverride override) {
        List<TmtPartDefinition> overriddenParts = new ArrayList<>(parts);
        applyPartOverrides(overriddenParts, override.partModelReplacement);

        List<TmtPartDefinition> overriddenBrokenParts = new ArrayList<>(brokenParts);
        applyPartOverrides(overriddenBrokenParts, override.brokenPartModelReplacement);

        Map<ItemCameraTransforms.TransformType, TRSRTransformation> transformBuilder = new LinkedHashMap<>(transforms);
        transformBuilder.putAll(override.transforms);

        ImmutableMap<String, String> modifiers = override.overrideModifierModel == null
                ? modifierTextures
                : ImmutableMap.copyOf(override.overrideModifierModel.getModels());

        AmmoPosition combinedAmmoPosition = ammoPosition;
        if (override.ammoPosition != null) {
            combinedAmmoPosition = ammoPosition == null ? override.ammoPosition : override.ammoPosition.combine(ammoPosition);
        }

        return new TmtToolDefinition(ImmutableList.copyOf(overriddenParts),
                overriddenBrokenParts,
                modifiers,
                ImmutableMap.copyOf(transformBuilder),
                ImmutableList.of(),
                combinedAmmoPosition);
    }

    private static void applyPartOverrides(List<TmtPartDefinition> output,
                                           TIntObjectHashMap<MaterialModel> replacements) {
        for (int key : replacements.keys()) {
            if (key >= 0 && key < output.size()) {
                output.set(key, TmtPartDefinition.fromMaterialModel(replacements.get(key), 0f));
            }
        }
    }

    private static ImmutableList<TmtPartDefinition> buildParts(List<MaterialModel> models, Float[] layerRotations) {
        ImmutableList.Builder<TmtPartDefinition> builder = ImmutableList.builder();
        for (int i = 0; i < models.size(); i++) {
            builder.add(TmtPartDefinition.fromMaterialModel(models.get(i), rotationAt(layerRotations, i)));
        }
        return builder.build();
    }

    private static List<TmtPartDefinition> buildBrokenParts(int size, List<MaterialModel> models, Float[] layerRotations) {
        List<TmtPartDefinition> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            MaterialModel model = i < models.size() ? models.get(i) : null;
            out.add(model == null ? null : TmtPartDefinition.fromMaterialModel(model, rotationAt(layerRotations, i)));
        }
        return out;
    }

    private static float rotationAt(Float[] rotations, int index) {
        return rotations != null && rotations.length > index && rotations[index] != null ? rotations[index] : 0f;
    }

    private static ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> mergeTransforms(
            IModelState state,
            ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> extraTransforms) {
        Map<ItemCameraTransforms.TransformType, TRSRTransformation> builder = new LinkedHashMap<>();
        builder.putAll(PerspectiveMapWrapper.getTransforms(state));
        builder.putAll(extraTransforms);
        return ImmutableMap.copyOf(builder);
    }

    private static boolean matches(ToolModelOverride override, ItemStack stack, World world, EntityLivingBase entity) {
        Item item = stack.getItem();
        for (Map.Entry<ResourceLocation, Float> entry : override.predicates.entrySet()) {
            IItemPropertyGetter getter = item.getPropertyGetter(entry.getKey());
            if (getter == null || getter.apply(stack, world, entity) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}
