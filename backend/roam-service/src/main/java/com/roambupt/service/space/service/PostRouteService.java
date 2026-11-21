package com.roambupt.service.space.service;

import com.roambupt.api.model.dto.RouteQuery;
import com.roambupt.api.model.dto.RouteStopRequest;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.RoutePlanVO;
import com.roambupt.service.post.service.PostQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 帖子路线：按帖子里的地点顺序串段。 */
@Service
public class PostRouteService {

    private final PostQueryService postQueryService;
    private final SpaceService spaceService;

    public PostRouteService(PostQueryService postQueryService, SpaceService spaceService) {
        this.postQueryService = postQueryService;
        this.spaceService = spaceService;
    }

    public RoutePlanVO routeOfPost(long postId, String mode) {
        if (!postQueryService.existsOnline(postId)) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "帖子不存在");
        }
        List<Long> topicIds = postQueryService.findOrderedPlaceTopicIds(postId);
        if (topicIds.isEmpty()) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "这条帖子没有地点");
        }
        if (topicIds.size() < 2) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "这条帖子只有一个地点，没有路线");
        }
        RouteQuery query = new RouteQuery(topicIds.stream().map(RouteStopRequest::new).toList(), mode);
        return spaceService.plan(query)
                .orElseThrow(() -> new BusinessException(StatusEnum.NOT_FOUND, "两点之间没有可走的路"));
    }
}
