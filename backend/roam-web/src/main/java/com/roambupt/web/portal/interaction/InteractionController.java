package com.roambupt.web.portal.interaction;

import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.InteractionResultVO;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.service.post.service.PostReadService;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.interaction.service.InteractionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 点赞 / 收藏。全部幂等。 */
@RestController
@RequestMapping("/api/v1")
public class InteractionController {

    private final InteractionService interactionService;
    private final PostReadService postReadService;

    public InteractionController(InteractionService interactionService,
                                 PostReadService postReadService) {
        this.interactionService = interactionService;
        this.postReadService = postReadService;
    }

    @GetMapping("/me/collections")
    public ResVo<PageResult<PostVO>> myCollections(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int pageSize) {
        long uid = uid();
        java.util.List<Long> ids = interactionService.collectedPostIds(uid, page, pageSize);
        long total = interactionService.countCollected(uid);
        return ResVo.ok(PageResult.of(total, Math.max(1, page), pageSize,
                postReadService.byIds(ids, uid)));
    }

    @PostMapping("/posts/{postId}/like")
    public ResVo<InteractionResultVO> like(@PathVariable long postId) {
        return ResVo.ok(interactionService.like(postId, uid()));
    }

    @DeleteMapping("/posts/{postId}/like")
    public ResVo<InteractionResultVO> unlike(@PathVariable long postId) {
        return ResVo.ok(interactionService.unlike(postId, uid()));
    }

    @PostMapping("/posts/{postId}/collect")
    public ResVo<InteractionResultVO> collect(@PathVariable long postId) {
        return ResVo.ok(interactionService.collect(postId, uid()));
    }

    @DeleteMapping("/posts/{postId}/collect")
    public ResVo<InteractionResultVO> uncollect(@PathVariable long postId) {
        return ResVo.ok(interactionService.uncollect(postId, uid()));
    }

    private long uid() {
        Long uid = CurrentUserContext.uid();
        if (uid == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        return uid;
    }
}
