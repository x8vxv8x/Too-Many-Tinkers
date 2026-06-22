package com.smd.toomanytinkers.client.render;

public final class TmtRenderStats {

    private static int materialDescriptors;
    private static int registeredParamMaps;
    private static int materialMapSlots;
    private static int descriptorRebuilds;
    private static int partMeshes;
    private static int descriptorCacheHits;
    private static int descriptorCacheMisses;
    private static int descriptorCacheSize;
    private static int descriptorCacheInvalidations;
    private static int partMaterialCacheSize;
    private static int partMaterialCacheInvalidations;
    private static int instancedDrawCalls;
    private static int legacyDrawCalls;

    private TmtRenderStats() {
    }

    public static void setMaterialDescriptorCounts(int descriptors, int paramMaps, int materialSlots, int rebuilds) {
        materialDescriptors = descriptors;
        registeredParamMaps = paramMaps;
        materialMapSlots = materialSlots;
        descriptorRebuilds = rebuilds;
    }

    public static void setPartMeshes(int meshes) {
        partMeshes = meshes;
    }

    public static void descriptorCacheHit() {
        descriptorCacheHits++;
    }

    public static void descriptorCacheMiss() {
        descriptorCacheMisses++;
    }

    public static void setDescriptorCacheSize(int size) {
        descriptorCacheSize = size;
    }

    public static void descriptorCacheInvalidated() {
        descriptorCacheInvalidations++;
    }

    public static void setPartMaterialCacheSize(int size) {
        partMaterialCacheSize = size;
    }

    public static void partMaterialCacheInvalidated() {
        partMaterialCacheInvalidations++;
    }

    public static void instancedDrawCall() {
        instancedDrawCalls++;
    }

    public static void legacyDrawCall() {
        legacyDrawCalls++;
    }

    public static String snapshot() {
        return "TmtRenderStats{" +
                "materialDescriptors=" + materialDescriptors +
                ", registeredParamMaps=" + registeredParamMaps +
                ", materialMapSlots=" + materialMapSlots +
                ", descriptorRebuilds=" + descriptorRebuilds +
                ", partMeshes=" + partMeshes +
                ", descriptorCacheHits=" + descriptorCacheHits +
                ", descriptorCacheMisses=" + descriptorCacheMisses +
                ", descriptorCacheSize=" + descriptorCacheSize +
                ", descriptorCacheInvalidations=" + descriptorCacheInvalidations +
                ", partMaterialCacheSize=" + partMaterialCacheSize +
                ", partMaterialCacheInvalidations=" + partMaterialCacheInvalidations +
                ", instancedDrawCalls=" + instancedDrawCalls +
                ", legacyDrawCalls=" + legacyDrawCalls +
                '}';
    }
}
