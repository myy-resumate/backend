package dev.resumate.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DlqConfig {

    @Value("${rabbitmq.dlq.exchange}")
    private String dlxName;
    @Value("${rabbitmq.dlq.queue}")
    private String dlqName;
    @Value("${rabbitmq.dlq.routing-key}")
    private String dlRoutingKey;


}