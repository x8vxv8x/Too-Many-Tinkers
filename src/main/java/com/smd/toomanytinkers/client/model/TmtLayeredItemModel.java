package com.smd.toomanytinkers.client.model;

import com.google.common.collect.ImmutableList;

public interface TmtLayeredItemModel extends TmtGpuItemModel {

    ImmutableList<TmtToolRenderDescriptor.Layer> getLayers();
}
