package com.roambupt.service.notify.mq;

/** 通知发布通道：MQ 打开时用 RabbitMQ，关闭时退化为同步写库。 */
public interface NotifyPublisher {

    void publish(NotifyMessage message);
}
