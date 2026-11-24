package com.roambupt.web.portal.topic;

import com.roambupt.api.model.dto.TopicQuery;
import com.roambupt.api.model.vo.CategoryCountVO;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.api.model.vo.TopicVO;
import com.roambupt.service.post.service.PostReadService;
import com.roambupt.service.rating.service.RatingService;
import com.roambupt.service.topic.service.TopicService;
import com.roambupt.web.global.OptionalAuth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主题（地点/分类）接口。 */
@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;
    private final PostReadService postReadService;
    private final RatingService ratingService;

    public TopicController(TopicService topicService, PostReadService postReadService,
                           RatingService ratingService) {
        this.topicService = topicService;
        this.postReadService = postReadService;
        this.ratingService = ratingService;
    }

    @OptionalAuth
    @GetMapping
    public ResVo<PageResult<TopicVO>> list(@RequestParam(required = false) String type,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<TopicVO> result = topicService.list(new TopicQuery(type, category, q, page, pageSize));
        java.util.List<Long> placeIds = result.list().stream()
                .filter(item -> "PLACE".equals(item.type()))
                .map(TopicVO::id)
                .toList();
        if (placeIds.isEmpty()) {
            return ResVo.ok(result);
        }
        var ratings = ratingService.ratingsOf(placeIds);
        java.util.List<TopicVO> filled = result.list().stream()
                .map(item -> ratings.containsKey(item.id())
                        ? new TopicVO(item.id(), item.type(), item.name(), item.category(), item.cover(),
                                item.description(), item.heat(), item.nodeId(), item.postCount(),
                                item.todayRouteCount(), ratings.get(item.id()))
                        : item)
                .toList();
        return ResVo.ok(PageResult.of(result.total(), result.page(), result.pageSize(), filled));
    }

    @OptionalAuth
    @GetMapping("/suggest")
    public ResVo<java.util.List<com.roambupt.api.model.vo.TopicBriefVO>> suggest(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResVo.ok(topicService.suggest(q, limit));
    }

    @OptionalAuth
    @GetMapping("/stats/posts")
    public ResVo<com.roambupt.api.model.vo.PostStatsVO> postStats() {
        return ResVo.ok(topicService.postStats());
    }

    @OptionalAuth
    @GetMapping("/stats")
    public ResVo<java.util.List<CategoryCountVO>> stats() {
        return ResVo.ok(topicService.stats());
    }

    @OptionalAuth
    @GetMapping("/{id}")
    public ResVo<TopicVO> detail(@PathVariable long id) {
        TopicVO topic = topicService.detail(id);
        if ("PLACE".equals(topic.type())) {
            topic = new TopicVO(topic.id(), topic.type(), topic.name(), topic.category(),
                    topic.cover(), topic.description(), topic.heat(), topic.nodeId(),
                    topic.postCount(), topic.todayRouteCount(), ratingService.ratingOf(id));
        }
        return ResVo.ok(topic);
    }

    @OptionalAuth
    @GetMapping("/{id}/posts")
    public ResVo<PageResult<PostVO>> posts(@PathVariable long id,
                                           @RequestParam(defaultValue = "latest") String sort,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(postReadService.byTopic(id, sort, page, pageSize));
    }
}
