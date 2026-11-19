package com.roambupt.web.admin;

import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.api.model.vo.AdminUserVO;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.core.permission.Permission;
import com.roambupt.service.user.service.UserAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台用户管理。 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Permission("ADMIN")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ResVo<PageResult<AdminUserVO>> list(@RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(userAdminService.page(q, page, pageSize));
    }

    @PutMapping("/{id}/status")
    public ResVo<Void> changeStatus(@PathVariable long id, @Valid @RequestBody StatusCommand command) {
        Long current = CurrentUserContext.uid();
        if (current != null && current == id && command.status() == 0) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "不能封禁自己");
        }
        userAdminService.changeStatus(id, command.status());
        return ResVo.ok();
    }

    /** 1 正常 / 0 封禁。 */
    public record StatusCommand(@Min(0) @Max(1) int status) {
    }
}
