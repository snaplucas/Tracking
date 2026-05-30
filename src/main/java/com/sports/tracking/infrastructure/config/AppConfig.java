package com.sports.tracking.infrastructure.config;

import com.sports.tracking.infrastructure.messaging.ScoreMessage;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Infrastructure beans: the HTTP client used to call the external scores API,
 * the thread pool that runs the per-event polling tasks, and the Kafka topic
 * and producer.
 */
@Configuration
public class AppConfig {

    /** HTTP client pointed at the external scores API. */
    @Bean
    RestClient externalApiClient(@Value("${tracking.external.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Dedicated scheduler for the per-event polling tasks so that slow or
     * stuck polls do not starve the rest of the application.
     */
    @Bean
    TaskScheduler pollingTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("event-poller-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }

    /** Auto-create the destination topic on startup (single broker => RF 1). */
    @Bean
    NewTopic scoresTopic(@Value("${tracking.kafka.topic}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Producer factory for String keys and JSON-serialized {@link ScoreMessage}
     * values. Defined explicitly so the {@link KafkaTemplate} is strongly typed
     * and unambiguous to inject.
     */
    @Bean
    ProducerFactory<String, ScoreMessage> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        config.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, ScoreMessage> kafkaTemplate(ProducerFactory<String, ScoreMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
