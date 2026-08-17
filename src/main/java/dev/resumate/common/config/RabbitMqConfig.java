package dev.resumate.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    @Value("${rabbitmq.embedding.exchange}")
    private String exchange;
    @Value("${rabbitmq.embedding.queue}")
    private String queue;
    @Value("${rabbitmq.embedding.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.dlq.exchange}")
    private String dlxName;
    @Value("${rabbitmq.dlq.routing-key}")
    private String dlRoutingKey;
    @Value("${rabbitmq.dlq.queue}")
    private String dlqName;

    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    //dlx를 먼저 생성해야함
    @Bean
    public DirectExchange dlx() {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue dlq() {
        return new Queue(dlqName);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlq()).to(dlx()).with(dlRoutingKey);
    }

    //exchange 빈 생성
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(exchange);
    }

    //큐 빈 생성
    @Bean
    public Queue queue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", 2000);  // ttl 2초로 설정
        arguments.put("x-dead-letter-exchange", dlxName);
        arguments.put("x-dead-letter-routing-key", dlRoutingKey);
        return new Queue(queue, true, false, false, arguments);  //dlq 정보를 등록
    }

    //바인딩 빈 생성 - 큐와 exchange를 바인딩
    @Bean
    public Binding binding() {
        return BindingBuilder
                .bind(queue())
                .to(exchange())
                .with(routingKey);
    }

    //rabbitTemplate 생성 - 실제 작업을 위한 빈
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }

    //Jackson 라이브러리를 통해 메시지 -> json으로 자동 변환해주는 빈
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
