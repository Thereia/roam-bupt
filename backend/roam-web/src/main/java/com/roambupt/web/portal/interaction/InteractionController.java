package com.roambupt.web.portal.interaction;

import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.InteractionResultVO;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.interaction.service.InteractionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 点赞 / 收藏。全部幂等。 */
@RestController
@RequestMapping("/api/v1/posts/{postId}")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/like")
    public ResVo<InteractionResultVO> like(@PathVariable long postId) {
        return ResVo.ok(interactionService.like(postId, uid()));
    }

    @DeleteMapping("/like")
    public ResVo<InteractionResultVO> unlike(@PathVariable long postId) {
        return ResVo.ok(interactionService.unlike(postId, uid()));
    }

    @PostMapping("/collect")
    public ResVo<InteractionResultVO> collect(@PathVariable long postId) {
        return ResVo.ok(interactionService.collect(postId, uid()));
    }

    @DeleteMapping("/collect")
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
