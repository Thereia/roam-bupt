package com.roambupt.service.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 通知 MQ 配置：开关、交换机/队列名、重投节奏。 */
@ConfigurationProperties(prefix = "roam.notify.mq")
public class NotifyMqProperties {

    private boolean enabled = false;
    private String exchange = "roam.notify.exchange";
    private String queue = "roam.notify.queue";
    private String routingKey = "roam.notify.created";
    private String dlx = "roam.notify.dlx";
    private String deadQueue = "roam.notify.dead.queue";
    private String deadRoutingKey = "roam.notify.dead";
    private long retryIntervalMs = 30_000L;
    private long retryInitialDelayMs = 30_000L;
    /** 发布后等 confirm 的超时，超过就当失败重投。 */
    private long confirmTimeoutMs = 60_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getDlx() {
        return dlx;
    }

    public void setDlx(String dlx) {
        this.dlx = dlx;
    }

    public String getDeadQueue() {
        return deadQueue;
    }

    public void setDeadQueue(String deadQueue) {
        this.deadQueue = deadQueue;
    }

    public String getDeadRoutingKey() {
        return deadRoutingKey;
    }

    public void setDeadRoutingKey(String deadRoutingKey) {
        this.deadRoutingKey = deadRoutingKey;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    public long getRetryInitialDelayMs() {
        return retryInitialDelayMs;
    }

    public void setRetryInitialDelayMs(long retryInitialDelayMs) {
        this.retryInitialDelayMs = retryInitialDelayMs;
    }

    public long getConfirmTimeoutMs() {
        return confirmTimeoutMs;
    }

    public void setConfirmTimeoutMs(long confirmTimeoutMs) {
        this.confirmTimeoutMs = confirmTimeoutMs;
    }
}
