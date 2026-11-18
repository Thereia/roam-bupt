package com.roambupt.api.model.event;

/**
 * 登录结果事件，供审计/风控消费。纯数据，不继承 Spring 类型。
 */
public record LoginEvent(Long uid, String username, boolean success, String reason, String ip) {
}
