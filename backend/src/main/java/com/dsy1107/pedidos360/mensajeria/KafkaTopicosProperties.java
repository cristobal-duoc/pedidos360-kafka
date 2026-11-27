package com.dsy1107.pedidos360.mensajeria;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Los topicos Kafka y sus parametros de administracion, leidos de application.yml
// (bloque "kafka-topicos").
@ConfigurationProperties(prefix = "kafka-topicos")
public record KafkaTopicosProperties(Topico creadas, Topico resueltas, int minInsyncReplicas) {

    public record Topico(String nombre, int particiones, int replicas, long retencionMs, String cleanupPolicy) {
    }
}
