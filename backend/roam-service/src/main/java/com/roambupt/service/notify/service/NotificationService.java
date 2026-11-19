package com.roambupt.service.notify.service;

import com.roambupt.api.model.vo.NotificationVO;
import com.roambupt.api.model.vo.PageResult;

import java.util.Collection;

/** 站内通知。 */
public interface NotificationService {

    void send(long recipientId, long actorId, String type,
              String sourceType, long sourceId, Long postId, String content);

    PageResult<NotificationVO> list(long uid, String type, int page, int pageSize);

    long countUnread(long uid);

    void markRead(long uid, Collection<Long> ids);

    void markAllRead(long uid);
}
