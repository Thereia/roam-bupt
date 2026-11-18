package com.roambupt.web.portal.topic;

import com.roambupt.api.model.dto.TopicQuery;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.api.model.vo.TopicVO;
import com.roambupt.service.post.service.PostReadService;
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

    public TopicController(TopicService topicService, PostReadService postReadService) {
        this.topicService = topicService;
        this.postReadService = postReadService;
    }

    @OptionalAuth
    @GetMapping
    public ResVo<PageResult<TopicVO>> list(@RequestParam(required = false) String type,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(topicService.list(new TopicQuery(type, category, q, page, pageSize)));
    }

    @OptionalAuth
    @GetMapping("/{id}")
    public ResVo<TopicVO> detail(@PathVariable long id) {
        return ResVo.ok(topicService.detail(id));
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
