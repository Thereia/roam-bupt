package com.roambupt.web.portal.post;

import com.roambupt.api.model.dto.FeedQuery;
import com.roambupt.api.model.dto.PostCreateCommand;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostCreatedVO;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.api.model.vo.ReqInfo;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.post.service.PostReadService;
import com.roambupt.service.post.service.PostWriteService;
import com.roambupt.web.global.OptionalAuth;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 帖子接口。 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostWriteService postWriteService;
    private final PostReadService postReadService;

    public PostController(PostWriteService postWriteService, PostReadService postReadService) {
        this.postWriteService = postWriteService;
        this.postReadService = postReadService;
    }

    @PostMapping
    public ResVo<PostCreatedVO> create(@Valid @RequestBody PostCreateCommand command) {
        Long uid = CurrentUserContext.uid();
        if (uid == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        return ResVo.ok(postWriteService.create(command, uid));
    }

    @PutMapping("/{id}")
    public ResVo<PostCreatedVO> update(@PathVariable long id,
                                       @Valid @RequestBody PostCreateCommand command) {
        ReqInfo info = CurrentUserContext.get();
        if (info == null || info.uid() == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        return ResVo.ok(postWriteService.update(id, command, info.uid()));
    }

    @DeleteMapping("/{id}")
    public ResVo<Void> delete(@PathVariable long id) {
        ReqInfo info = CurrentUserContext.get();
        if (info == null || info.uid() == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        postWriteService.delete(id, info.uid(), info.role());
        return ResVo.ok();
    }

    @PutMapping("/{id}/publish")
    public ResVo<PostCreatedVO> publishDraft(@PathVariable long id) {
        Long uid = CurrentUserContext.uid();
        if (uid == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        return ResVo.ok(postWriteService.publishDraft(id, uid));
    }

    @OptionalAuth
    @GetMapping("/{id}")
    public ResVo<PostVO> detail(@PathVariable long id) {
        var info = CurrentUserContext.get();
        return ResVo.ok(postReadService.detail(id,
                info == null ? null : info.uid(),
                info == null ? null : info.role()));
    }

    @OptionalAuth
    @GetMapping
    public ResVo<PageResult<PostVO>> feed(@RequestParam(required = false) String feed,
                                          @RequestParam(required = false) Long topicId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        Long uid = CurrentUserContext.uid();
        return ResVo.ok(postReadService.feed(new FeedQuery(feed, topicId, page, pageSize), uid));
    }
}
