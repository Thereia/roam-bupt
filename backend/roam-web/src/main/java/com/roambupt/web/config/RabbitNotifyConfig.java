package com.roambupt.web.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import com.roambupt.service.notify.config.NotifyMqProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通知 MQ 拓扑：业务队列 + 死信交换机/队列。
 *
 * <p>只在 roam.notify.mq.enabled=true 时生效，本地不开 RabbitMQ 也能启动。</p>
 */
@Configuration
@EnableConfigurationProperties(NotifyMqProperties.class)
@ConditionalOnProperty(name = "roam.notify.mq.enabled", havingValue = "true")
public class RabbitNotifyConfig {

    @Bean
    public MessageConverter notifyMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange notifyExchange(NotifyMqProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue notifyQueue(NotifyMqProperties properties) {
        return QueueBuilder.durable(properties.getQueue())
                .deadLetterExchange(properties.getDlx())
                .deadLetterRoutingKey(properties.getDeadRoutingKey())
                .build();
    }

    @Bean
    public Binding notifyBinding(Queue notifyQueue, DirectExchange notifyExchange,
                                 NotifyMqProperties properties) {
        return BindingBuilder.bind(notifyQueue).to(notifyExchange).with(properties.getRoutingKey());
    }

    @Bean
    public DirectExchange notifyDeadExchange(NotifyMqProperties properties) {
        return new DirectExchange(properties.getDlx(), true, false);
    }

    @Bean
    public Queue notifyDeadQueue(NotifyMqProperties properties) {
        return QueueBuilder.durable(properties.getDeadQueue()).build();
    }

    @Bean
    public Binding notifyDeadBinding(Queue notifyDeadQueue, DirectExchange notifyDeadExchange,
                                     NotifyMqProperties properties) {
        return BindingBuilder.bind(notifyDeadQueue).to(notifyDeadExchange).with(properties.getDeadRoutingKey());
    }
}
