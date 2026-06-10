package com.familyagent.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration.
 */
@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_QUESTION_GENERATE = "fa.question.generate";
    public static final String QUEUE_ASSESSMENT_UPDATE = "fa.assessment.update";
    public static final String QUEUE_CHAT_SAVE = "fa.chat.save";

    public static final String EXCHANGE_AI = "fa.ai.exchange";
    public static final String EXCHANGE_BUSINESS = "fa.business.exchange";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
