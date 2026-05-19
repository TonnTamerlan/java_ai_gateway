package com.typedgoose.aigateway.kafka;

import com.typedgoose.contracts.summarization.SummarizationRequestMessage;
import com.typedgoose.contracts.summarization.SummarizationResponseMessage;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

    public static final String SUMMARIZATION_REQUESTS_TOPIC = "summarization-requests";
    public static final String SUMMARIZATION_RESPONSES_TOPIC = "summarization-responses";

    private final String bootstrapServers;
    private final String consumerGroupId;
    private final int maxPollRecords;
    private final int fetchMinBytes;
    private final int fetchMaxWaitMs;

    public KafkaConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:ai-gateway}") String consumerGroupId,
            @Value("${spring.kafka.consumer.max-poll-records:5}") int maxPollRecords,
            @Value("${spring.kafka.consumer.fetch-min-bytes:5000}") int fetchMinBytes,
            @Value("${spring.kafka.consumer.fetch-max-wait:180000}") int fetchMaxWaitMs) {
        this.bootstrapServers = bootstrapServers;
        this.consumerGroupId = consumerGroupId;
        this.maxPollRecords = maxPollRecords;
        this.fetchMinBytes = fetchMinBytes;
        this.fetchMaxWaitMs = fetchMaxWaitMs;
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        KafkaAdmin admin = new KafkaAdmin(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    @Bean
    public NewTopic summarizationResponsesTopic() {
        return TopicBuilder.name(SUMMARIZATION_RESPONSES_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, SummarizationResponseMessage> responseProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        props.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, SummarizationResponseMessage> responseKafkaTemplate(
            ProducerFactory<String, SummarizationResponseMessage> responseProducerFactory) {
        return new KafkaTemplate<>(responseProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, SummarizationRequestMessage> requestConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, fetchMinBytes);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);
        // request.timeout.ms must exceed fetch.max.wait.ms; otherwise the
        // consumer aborts each fetch at 30s (default) and re-issues, never
        // letting the broker reach its wait threshold. Pad by 20s.
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, fetchMaxWaitMs + 20000);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE,
                SummarizationRequestMessage.class.getName());
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SummarizationRequestMessage>
            batchListenerContainerFactory(
                    ConsumerFactory<String, SummarizationRequestMessage> requestConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, SummarizationRequestMessage>();
        factory.setConsumerFactory(requestConsumerFactory);
        factory.setBatchListener(true);
        return factory;
    }
}
