package com.dsy1107.kafkaadmin.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

// El cliente de administracion de Kafka, con la misma configuracion de application.yml
// (los tres brokers y los timeouts). Spring lo cierra al apagar la aplicacion.
@Configuration
public class KafkaAdminConfig {

    @Bean(destroyMethod = "close")
    AdminClient adminClient(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }
}
