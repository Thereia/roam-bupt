package com.roambupt.web.portal.search;

import com.roambupt.api.model.dto.SearchQuery;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.search.service.SearchService;
import com.roambupt.web.global.OptionalAuth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 搜索接口。 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @OptionalAuth
    @GetMapping
    public ResVo<PageResult<PostVO>> search(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Long topicId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(searchService.search(new SearchQuery(q, topicId, page, pageSize),
                CurrentUserContext.uid()));
    }
}
