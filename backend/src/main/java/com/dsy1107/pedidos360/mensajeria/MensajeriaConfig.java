package com.dsy1107.pedidos360.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Configuracion de mensajeria del microservicio de solicitudes (PRODUCTOR).
//
// Este microservicio solo publica: declara los exchanges a los que envia y nada
// mas. Las colas, sus DLQ y los bindings los declara quien consume
// (microservicio notificaciones), porque son suyas.
@Configuration
@EnableConfigurationProperties(MensajeriaProperties.class)
public class MensajeriaConfig {

    // Exchange DIRECT: la routing key tiene que coincidir exacta con la del binding.
    // solicitud.creada  -> notificaciones.aprobador
    // solicitud.aprobada -> documentos.comprobante
    @Bean
    DirectExchange exchangeSolicitudes(MensajeriaProperties props) {
        return ExchangeBuilder.directExchange(props.exchanges().solicitudes()).durable(true).build();
    }

    // Exchange TOPIC: el binding usa patrones. solicitud.# -> auditoria.solicitudes
    @Bean
    TopicExchange exchangeEventos(MensajeriaProperties props) {
        return ExchangeBuilder.topicExchange(props.exchanges().eventos()).durable(true).build();
    }

    // Los eventos viajan como JSON, con las fechas en ISO-8601 (el ObjectMapper de Spring).
    @Bean
    MessageConverter conversorJson(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
