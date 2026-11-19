package com.roambupt.web.portal.notify;

import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.NotificationVO;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.notify.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 站内通知接口。 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResVo<PageResult<NotificationVO>> list(@RequestParam(required = false) String type,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(notificationService.list(uid(), type, page, pageSize));
    }

    @GetMapping("/unread-count")
    public ResVo<Map<String, Long>> unreadCount() {
        return ResVo.ok(Map.of("count", notificationService.countUnread(uid())));
    }

    @PostMapping("/read")
    public ResVo<Void> markRead(@Valid @RequestBody MarkReadCommand command) {
        notificationService.markRead(uid(), command.ids());
        return ResVo.ok();
    }

    @PutMapping("/read-all")
    public ResVo<Void> markAllRead() {
        notificationService.markAllRead(uid());
        return ResVo.ok();
    }

    private long uid() {
        Long uid = CurrentUserContext.uid();
        if (uid == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        return uid;
    }

    /** 标记已读入参。 */
    public record MarkReadCommand(@NotEmpty List<Long> ids) {
    }
}
