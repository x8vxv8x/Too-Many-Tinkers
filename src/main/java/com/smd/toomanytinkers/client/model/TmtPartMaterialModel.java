package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableMap;
import com.smd.toomanytinkers.client.render.TmtRenderStats;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraftforge.common.model.TRSRTransformation;
import slimeknights.tconstruct.library.client.model.BakedMaterialModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class TmtPartMaterialModel extends BakedMaterialModel {

    private static final Set<TmtPartMaterialModel> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());

    private final IBakedModel baseModel;
    private final TmtPartDefinition definition;
    private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;
    private final Map<String, IBakedModel> materialModelCache = new HashMap<>();

    public TmtPartMaterialModel(IBakedModel base,
                                TmtPartDefinition definition,
                                ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms) {
        super(base, transforms);
        this.baseModel = base;
        this.definition = definition;
        this.transforms = transforms;
        synchronized (INSTANCES) {
            INSTANCES.add(this);
        }
    }

    public IBakedModel getBaseModel() {
        return baseModel;
    }

    public ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> getTransforms() {
        return transforms;
    }

    @Override
    public IBakedModel getModelByIdentifier(String identifier) {
        String key = identifier == null ? "" : identifier;
        IBakedModel model = materialModelCache.computeIfAbsent(key,
                materialId -> new TmtGpuPartStackModel(baseModel, transforms, definition, materialId.isEmpty() ? null : materialId));
        updateStats();
        return model;
    }

    public static void invalidateCaches() {
        synchronized (INSTANCES) {
            for (TmtPartMaterialModel model : INSTANCES) {
                model.materialModelCache.clear();
            }
        }
        TmtRenderStats.setPartMaterialCacheSize(0);
        TmtRenderStats.partMaterialCacheInvalidated();
    }

    private static void updateStats() {
        int size = 0;
        synchronized (INSTANCES) {
            for (TmtPartMaterialModel model : INSTANCES) {
                size += model.materialModelCache.size();
            }
        }
        TmtRenderStats.setPartMaterialCacheSize(size);
    }
}
