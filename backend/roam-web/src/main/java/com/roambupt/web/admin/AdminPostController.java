package com.roambupt.web.admin;

import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.permission.Permission;
import com.roambupt.service.post.service.PostAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台帖子审核。 */
@RestController
@RequestMapping("/api/v1/admin/posts")
@Permission("ADMIN")
public class AdminPostController {

    private final PostAdminService postAdminService;

    public AdminPostController(PostAdminService postAdminService) {
        this.postAdminService = postAdminService;
    }

    @GetMapping
    public ResVo<PageResult<PostVO>> list(@RequestParam(defaultValue = "PENDING") String status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(postAdminService.listByStatus(status, page, pageSize));
    }

    @PutMapping("/{id}/status")
    public ResVo<Void> changeStatus(@PathVariable long id,
                                    @Valid @RequestBody StatusCommand command) {
        postAdminService.changeStatus(id, command.status());
        return ResVo.ok();
    }

    /** 状态变更入参。 */
    public record StatusCommand(@NotBlank String status) {
    }
}
