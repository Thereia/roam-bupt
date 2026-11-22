package com.roambupt.api.model.vo;

import java.util.List;

/** 底图数据：节点 + 边，一次取回。 */
public record MapVO(List<MapNodeVO> nodes, List<MapEdgeVO> edges) {
}
