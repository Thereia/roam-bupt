package com.roambupt.service.space.service;

import com.roambupt.api.model.dto.RouteQuery;
import com.roambupt.api.model.vo.RoutePlanVO;

import java.util.List;

/** 路线规划。 */
public interface SpaceService {

    /** 单条最优方案；无路返回 empty。 */
    java.util.Optional<RoutePlanVO> plan(RouteQuery query);

    /** 2–3 条备选，按权重排序。 */
    List<RoutePlanVO> alternatives(RouteQuery query, int limit);
}
