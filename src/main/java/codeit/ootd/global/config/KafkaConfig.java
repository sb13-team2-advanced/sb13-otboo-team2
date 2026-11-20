package codeit.ootd.global.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** 기본 Kafka 리스너가 사용하는 공통 재시도 및 실패 메시지 보관 정책. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "otboo.kafka.enabled", havingValue = "true")
public class KafkaConfig {
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}
