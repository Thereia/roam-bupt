package com.roambupt.service.notify.mq;

/**
 * 通知消息：MQ 投递与消费共用。
 *
 * <p>字段与 {@code notification} 表的唯一键 (user_id, type, source_id, actor_id) 对齐，
 * 重复消费不会产生重复通知。</p>
 */
public record NotifyMessage(long recipientId,
                            long actorId,
                            String type,
                            String sourceType,
                            long sourceId,
                            Long postId,
                            String content) {
}
