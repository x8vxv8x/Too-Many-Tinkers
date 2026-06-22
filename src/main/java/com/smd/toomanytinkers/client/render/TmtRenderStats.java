package com.smd.toomanytinkers.client.render;

public final class TmtRenderStats {

    private static int materialDescriptors;
    private static int registeredParamMaps;
    private static int descriptorRebuilds;
    private static int partMeshes;
    private static int descriptorCacheHits;
    private static int descriptorCacheMisses;
    private static int instancedDrawCalls;
    private static int legacyDrawCalls;

    private TmtRenderStats() {
    }

    public static void setMaterialDescriptorCounts(int descriptors, int paramMaps, int rebuilds) {
        materialDescriptors = descriptors;
        registeredParamMaps = paramMaps;
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
                ", descriptorRebuilds=" + descriptorRebuilds +
                ", partMeshes=" + partMeshes +
                ", descriptorCacheHits=" + descriptorCacheHits +
                ", descriptorCacheMisses=" + descriptorCacheMisses +
                ", instancedDrawCalls=" + instancedDrawCalls +
                ", legacyDrawCalls=" + legacyDrawCalls +
                '}';
    }
}
