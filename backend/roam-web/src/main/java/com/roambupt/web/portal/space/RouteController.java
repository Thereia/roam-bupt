package com.roambupt.web.portal.space;

import com.roambupt.api.model.dto.RouteQuery;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.api.model.vo.RoutePlanVO;
import com.roambupt.service.space.service.PostRouteService;
import com.roambupt.service.space.service.SpaceService;
import com.roambupt.web.global.OptionalAuth;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 路线接口。 */
@RestController
@RequestMapping("/api/v1")
public class RouteController {

    private final SpaceService spaceService;
    private final PostRouteService postRouteService;

    public RouteController(SpaceService spaceService, PostRouteService postRouteService) {
        this.spaceService = spaceService;
        this.postRouteService = postRouteService;
    }

    @OptionalAuth
    @PostMapping("/nav/route")
    public ResVo<RoutePlanVO> route(@Valid @RequestBody RouteQuery query) {
        return ResVo.ok(spaceService.plan(query)
                .orElseThrow(() -> new BusinessException(StatusEnum.NOT_FOUND, "两点之间没有可走的路")));
    }

    @OptionalAuth
    @PostMapping("/nav/routes")
    public ResVo<List<RoutePlanVO>> routes(@Valid @RequestBody RouteQuery query,
                                           @RequestParam(defaultValue = "3") int limit) {
        List<RoutePlanVO> plans = spaceService.alternatives(query, limit);
        if (plans.isEmpty()) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "两点之间没有可走的路");
        }
        return ResVo.ok(plans);
    }

    @OptionalAuth
    @GetMapping("/posts/{id}/route")
    public ResVo<RoutePlanVO> postRoute(@PathVariable long id,
                                        @RequestParam(defaultValue = "MIXED") String mode) {
        return ResVo.ok(postRouteService.routeOfPost(id, mode));
    }
}
