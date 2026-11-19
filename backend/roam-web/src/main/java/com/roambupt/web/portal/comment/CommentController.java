package com.roambupt.web.portal.comment;

import com.roambupt.api.model.dto.CommentCreateCommand;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.CommentCreatedVO;
import com.roambupt.api.model.vo.CommentVO;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.ReqInfo;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.comment.service.CommentReadService;
import com.roambupt.service.comment.service.CommentWriteService;
import com.roambupt.web.global.OptionalAuth;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 评论接口。 */
@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentWriteService commentWriteService;
    private final CommentReadService commentReadService;

    public CommentController(CommentWriteService commentWriteService,
                             CommentReadService commentReadService) {
        this.commentWriteService = commentWriteService;
        this.commentReadService = commentReadService;
    }

    @PostMapping("/posts/{postId}/comments")
    public ResVo<CommentCreatedVO> create(@PathVariable long postId,
                                          @Valid @RequestBody CommentCreateCommand command) {
        Long uid = CurrentUserContext.uid();
        if (uid == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        return ResVo.ok(commentWriteService.create(postId, command, uid));
    }

    @OptionalAuth
    @GetMapping("/posts/{postId}/comments")
    public ResVo<PageResult<CommentVO>> listTop(@PathVariable long postId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(commentReadService.listTop(postId, page, pageSize));
    }

    @OptionalAuth
    @GetMapping("/comments/{id}/replies")
    public ResVo<PageResult<CommentVO>> listReplies(@PathVariable long id,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(commentReadService.listReplies(id, page, pageSize));
    }

    @DeleteMapping("/comments/{id}")
    public ResVo<Void> delete(@PathVariable long id) {
        ReqInfo info = CurrentUserContext.get();
        if (info == null || info.uid() == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        commentWriteService.delete(id, info.uid(), info.role());
        return ResVo.ok();
    }
}
