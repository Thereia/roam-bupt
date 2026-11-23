package com.roambupt.web.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通知 MQ 拓扑：业务队列 + 死信交换机/队列。
 *
 * <p>只在 roam.notify.mq.enabled=true 时生效，本地不开 RabbitMQ 也能启动。</p>
 */
@Configuration
@ConditionalOnProperty(name = "roam.notify.mq.enabled", havingValue = "true")
public class RabbitNotifyConfig {

    @Bean
    public MessageConverter notifyMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange notifyExchange(
            @Value("${roam.notify.mq.exchange:roam.notify.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue notifyQueue(
            @Value("${roam.notify.mq.queue:roam.notify.queue}") String queue,
            @Value("${roam.notify.mq.dlx:roam.notify.dlx}") String dlx,
            @Value("${roam.notify.mq.dead-routing-key:roam.notify.dead}") String deadRoutingKey) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }

    @Bean
    public Binding notifyBinding(
            Queue notifyQueue,
            DirectExchange notifyExchange,
            @Value("${roam.notify.mq.routing-key:roam.notify.created}") String routingKey) {
        return BindingBuilder.bind(notifyQueue).to(notifyExchange).with(routingKey);
    }

    @Bean
    public DirectExchange notifyDeadExchange(
            @Value("${roam.notify.mq.dlx:roam.notify.dlx}") String dlx) {
        return new DirectExchange(dlx, true, false);
    }

    @Bean
    public Queue notifyDeadQueue(
            @Value("${roam.notify.mq.dead-queue:roam.notify.dead.queue}") String deadQueue) {
        return QueueBuilder.durable(deadQueue).build();
    }

    @Bean
    public Binding notifyDeadBinding(
            Queue notifyDeadQueue,
            DirectExchange notifyDeadExchange,
            @Value("${roam.notify.mq.dead-routing-key:roam.notify.dead}") String deadRoutingKey) {
        return BindingBuilder.bind(notifyDeadQueue).to(notifyDeadExchange).with(deadRoutingKey);
    }
}
