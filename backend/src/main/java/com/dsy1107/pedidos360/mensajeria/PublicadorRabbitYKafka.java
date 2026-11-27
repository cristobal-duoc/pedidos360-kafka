package com.dsy1107.pedidos360.mensajeria;

import com.dsy1107.pedidos360.Solicitud;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

// Lo que usa el controlador: avisa cada evento a RabbitMQ y a Kafka.
// RabbitMQ sigue igual que antes; Kafka se suma sin cambiar nada de lo existente.
@Primary
@Component
class PublicadorRabbitYKafka implements PublicadorEventos {

    private final PublicadorRabbit rabbit;
    private final PublicadorKafka kafka;

    PublicadorRabbitYKafka(PublicadorRabbit rabbit, PublicadorKafka kafka) {
        this.rabbit = rabbit;
        this.kafka = kafka;
    }

    @Override
    public void solicitudCreada(Solicitud solicitud) {
        rabbit.solicitudCreada(solicitud);
        kafka.solicitudCreada(solicitud);
    }

    @Override
    public void solicitudResuelta(Solicitud solicitud) {
        rabbit.solicitudResuelta(solicitud);
        kafka.solicitudResuelta(solicitud);
    }
}
