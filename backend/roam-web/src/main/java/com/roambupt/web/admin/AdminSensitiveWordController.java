package com.roambupt.web.admin;

import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.api.model.vo.SensitiveWordVO;
import com.roambupt.core.permission.Permission;
import com.roambupt.service.moderation.service.SensitiveWordAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台敏感词维护。 */
@RestController
@RequestMapping("/api/v1/admin/sensitive-words")
@Permission("ADMIN")
public class AdminSensitiveWordController {

    private final SensitiveWordAdminService sensitiveWordAdminService;

    public AdminSensitiveWordController(SensitiveWordAdminService sensitiveWordAdminService) {
        this.sensitiveWordAdminService = sensitiveWordAdminService;
    }

    @GetMapping
    public ResVo<PageResult<SensitiveWordVO>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return ResVo.ok(sensitiveWordAdminService.page(page, pageSize));
    }

    @PostMapping
    public ResVo<Void> add(@Valid @RequestBody WordCommand command) {
        sensitiveWordAdminService.add(command.word());
        return ResVo.ok();
    }

    @DeleteMapping("/{id}")
    public ResVo<Void> remove(@PathVariable long id) {
        sensitiveWordAdminService.remove(id);
        return ResVo.ok();
    }

    /** 新增敏感词。 */
    public record WordCommand(@NotBlank @Size(max = 64) String word) {
    }
}
