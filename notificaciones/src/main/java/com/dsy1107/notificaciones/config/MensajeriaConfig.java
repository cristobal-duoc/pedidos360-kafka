package com.dsy1107.notificaciones.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// La TOPOLOGIA de RabbitMQ de este microservicio: exchanges, colas, DLQ y bindings.
// RabbitAdmin los declara al conectarse (es idempotente: si ya existen iguales,
// no pasa nada). Los listeners solo escuchan; no declaran nada.
//
// Las tres rutas de mensajeria:
//
//   pedidos360.solicitudes (direct) --solicitud.creada-->   notificaciones.aprobador
//   pedidos360.solicitudes (direct) --solicitud.aprobada--> documentos.comprobante
//   pedidos360.eventos     (topic)  --solicitud.#-------->  auditoria.solicitudes
//
// Y la de los mensajes muertos: cada cola apunta al DLX con su propia routing key,
// y el DLX (direct) deja cada mensaje en la DLQ que le corresponde.
//
//   notificaciones.aprobador --nack--> pedidos360.dlx --notificaciones.aprobador.dlq--> notificaciones.aprobador.dlq
//
// Todas las colas son QUORUM: tienen una replica en cada nodo del cluster, asi
// que si cae un nodo la cola y sus mensajes siguen disponibles.
@Configuration
@EnableConfigurationProperties(MensajeriaProperties.class)
public class MensajeriaConfig {

    private final MensajeriaProperties props;

    public MensajeriaConfig(MensajeriaProperties props) {
        this.props = props;
    }

    // ---------- Exchanges ----------

    @Bean
    DirectExchange exchangeSolicitudes() {
        return ExchangeBuilder.directExchange(props.exchanges().solicitudes()).durable(true).build();
    }

    @Bean
    TopicExchange exchangeEventos() {
        return ExchangeBuilder.topicExchange(props.exchanges().eventos()).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().dlx()).durable(true).build();
    }

    // ---------- Caso de uso 1: avisar al aprobador (exchange direct) ----------

    @Bean
    Queue colaNotificaciones() {
        return colaConDlq(props.colas().notificaciones());
    }

    @Bean
    Queue dlqNotificaciones() {
        return QueueBuilder.durable(props.colas().notificaciones().dlq()).quorum().build();
    }

    @Bean
    Binding bindingNotificaciones() {
        return BindingBuilder.bind(colaNotificaciones()).to(exchangeSolicitudes())
                .with(props.colas().notificaciones().routingKey());
    }

    @Bean
    Binding bindingDlqNotificaciones() {
        return BindingBuilder.bind(dlqNotificaciones()).to(deadLetterExchange())
                .with(props.colas().notificaciones().dlq());
    }

    // ---------- Caso de uso 2: comprobante de la solicitud aprobada (exchange direct) ----------

    @Bean
    Queue colaComprobantes() {
        return colaConDlq(props.colas().comprobantes());
    }

    @Bean
    Queue dlqComprobantes() {
        return QueueBuilder.durable(props.colas().comprobantes().dlq()).quorum().build();
    }

    @Bean
    Binding bindingComprobantes() {
        return BindingBuilder.bind(colaComprobantes()).to(exchangeSolicitudes())
                .with(props.colas().comprobantes().routingKey());
    }

    @Bean
    Binding bindingDlqComprobantes() {
        return BindingBuilder.bind(dlqComprobantes()).to(deadLetterExchange())
                .with(props.colas().comprobantes().dlq());
    }

    // ---------- Caso de uso 3: auditoria de todo evento (exchange topic) ----------

    @Bean
    Queue colaAuditoria() {
        return colaConDlq(props.colas().auditoria());
    }

    @Bean
    Queue dlqAuditoria() {
        return QueueBuilder.durable(props.colas().auditoria().dlq()).quorum().build();
    }

    @Bean
    Binding bindingAuditoria() {
        return BindingBuilder.bind(colaAuditoria()).to(exchangeEventos())
                .with(props.colas().auditoria().routingKey());
    }

    @Bean
    Binding bindingDlqAuditoria() {
        return BindingBuilder.bind(dlqAuditoria()).to(deadLetterExchange())
                .with(props.colas().auditoria().dlq());
    }

    // Una cola principal: quorum, durable y con su salida a la DLQ.
    // x-dead-letter-exchange     -> a donde va un mensaje rechazado (basicNack, requeue=false)
    // x-dead-letter-routing-key  -> con que routing key llega al DLX (el nombre de su DLQ)
    private Queue colaConDlq(MensajeriaProperties.Ruta ruta) {
        return QueueBuilder.durable(ruta.nombre())
                .quorum()
                .deadLetterExchange(props.exchanges().dlx())
                .deadLetterRoutingKey(ruta.dlq())
                .build();
    }
}
