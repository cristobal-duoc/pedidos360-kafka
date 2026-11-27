package com.dsy1107.notificaciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Los topicos y consumer groups de Kafka, leidos de application.yml (bloque "kafka-eventos").
@ConfigurationProperties(prefix = "kafka-eventos")
public record KafkaEventosProperties(Topicos topicos, Grupos grupos) {

    public record Topicos(String creadas, String resueltas) {
    }

    public record Grupos(String notificaciones, String auditoria) {
    }
}
