package com.roambupt.api.model.vo;

/** 底图节点：x/y 是 0–1 的比例坐标。 */
public record MapNodeVO(Long id, String name, String type, double x, double y) {
}
