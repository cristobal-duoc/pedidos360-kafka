package com.dsy1107.pedidos360.mensajeria;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Configuracion Kafka del microservicio de solicitudes (PRODUCTOR).
//
// Declara los dos topicos a los que publica. Al arrancar, Spring los crea si no
// existen, con sus particiones, replicas, retencion y cleanup policy.
@Configuration
@EnableConfigurationProperties(KafkaTopicosProperties.class)
public class KafkaConfig {

    @Bean
    NewTopic topicoCreadas(KafkaTopicosProperties props) {
        return topico(props.creadas(), props.minInsyncReplicas());
    }

    @Bean
    NewTopic topicoResueltas(KafkaTopicosProperties props) {
        return topico(props.resueltas(), props.minInsyncReplicas());
    }

    private static NewTopic topico(KafkaTopicosProperties.Topico t, int minInsync) {
        return TopicBuilder.name(t.nombre())
            .partitions(t.particiones())
            .replicas(t.replicas())
            .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(t.retencionMs()))
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, t.cleanupPolicy())
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(minInsync))
            .build();
    }
}
