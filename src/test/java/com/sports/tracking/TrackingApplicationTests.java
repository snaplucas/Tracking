package com.sports.tracking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"sports.events.scores"})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class TrackingApplicationTests {

    @Test
    void contextLoads() {
    }

}
