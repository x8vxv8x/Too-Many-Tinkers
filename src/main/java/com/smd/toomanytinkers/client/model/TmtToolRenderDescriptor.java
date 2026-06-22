package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.ToolHelper;

import java.util.Arrays;
import java.util.Map;

public final class TmtToolRenderDescriptor {

    public static final class PartInstance {
        private final TmtPartDefinition definition;
        private final String materialId;

        private PartInstance(TmtPartDefinition definition, String materialId) {
            this.definition = definition;
            this.materialId = materialId;
        }

        public TmtPartDefinition getDefinition() {
            return definition;
        }

        public String getMaterialId() {
            return materialId;
        }
    }

    private final TmtToolDefinition definition;
    private final ImmutableList<PartInstance> parts;

    private TmtToolRenderDescriptor(TmtToolDefinition definition, ImmutableList<PartInstance> parts) {
        this.definition = definition;
        this.parts = parts;
    }

    public static TmtToolRenderDescriptor create(TmtToolDefinition definition, ItemStack stack) {
        ImmutableList.Builder<PartInstance> builder = ImmutableList.builder();
        NBTTagList materials = TagUtil.getBaseMaterialsTagList(stack);
        boolean broken = ToolHelper.isBroken(stack);

        for (int i = 0; i < definition.getParts().size(); i++) {
            String materialId = i < materials.tagCount() ? materials.getStringTagAt(i) : "";
            TmtPartDefinition part = broken && definition.getBrokenParts().get(i) != null
                    ? definition.getBrokenParts().get(i)
                    : definition.getParts().get(i);
            builder.add(new PartInstance(part, materialId));
        }

        addModifierParts(definition, stack, builder);
        return new TmtToolRenderDescriptor(definition, builder.build());
    }

    public TmtToolDefinition getDefinition() {
        return definition;
    }

    public ImmutableList<PartInstance> getParts() {
        return parts;
    }

    private static void addModifierParts(TmtToolDefinition definition, ItemStack stack,
                                         ImmutableList.Builder<PartInstance> builder) {
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
                builder.add(new PartInstance(TmtPartDefinition.singleTexture(new ResourceLocation(texture), 0f), null));
            }
        }
    }
}
