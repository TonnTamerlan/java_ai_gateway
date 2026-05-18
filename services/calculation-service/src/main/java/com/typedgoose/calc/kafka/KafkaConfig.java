package com.typedgoose.calc.kafka;

import com.typedgoose.contracts.summarization.SummarizationRequestMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
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

    private final String bootstrapServers;
    private final String consumerGroupId;

    public KafkaConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:calc-service}") String consumerGroupId) {
        this.bootstrapServers = bootstrapServers;
        this.consumerGroupId = consumerGroupId;
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        KafkaAdmin admin = new KafkaAdmin(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    @Bean
    public NewTopic summarizationRequestsTopic() {
        return TopicBuilder.name(SUMMARIZATION_REQUESTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, SummarizationRequestMessage> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        props.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, SummarizationRequestMessage> kafkaTemplate(
            ProducerFactory<String, SummarizationRequestMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, SummarizationRequestMessage> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
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
            kafkaListenerContainerFactory(
                    ConsumerFactory<String, SummarizationRequestMessage> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, SummarizationRequestMessage>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
