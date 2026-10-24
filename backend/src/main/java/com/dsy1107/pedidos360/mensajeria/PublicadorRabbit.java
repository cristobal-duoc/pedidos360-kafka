package com.dsy1107.pedidos360.mensajeria;

import com.dsy1107.pedidos360.Solicitud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// Implementacion con RabbitMQ. Dos exchanges, con dos propositos:
// - direct (solicitudes): el trabajo que alguien tiene que hacer. Solo la solicitud
//   creada (avisar al aprobador) y la aprobada (generar el comprobante) generan trabajo.
// - topic  (eventos)    : el registro de TODO lo que pasa, incluida la rechazada.
//
// Si RabbitMQ no responde, la solicitud YA quedo guardada en la base: el evento
// se pierde y queda en el log, pero el usuario no ve un error. La mensajeria
// es desacoplada: su caida no puede tumbar la logica existente.
@Component
class PublicadorRabbit implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorRabbit.class);

    private final RabbitTemplate rabbit;
    private final MensajeriaProperties props;

    PublicadorRabbit(RabbitTemplate rabbit, MensajeriaProperties props) {
        this.rabbit = rabbit;
        this.props = props;
    }

    @Override
    public void solicitudCreada(Solicitud solicitud) {
        publicar(props.routingKeys().creada(), solicitud, true);
    }

    @Override
    public void solicitudResuelta(Solicitud solicitud) {
        if (solicitud.getEstado() == Solicitud.Estado.APROBADA) {
            publicar(props.routingKeys().aprobada(), solicitud, true);
        } else {
            publicar(props.routingKeys().rechazada(), solicitud, false); // no genera trabajo
        }
    }

    private void publicar(String routingKey, Solicitud solicitud, boolean generaTrabajo) {
        SolicitudEvento evento = SolicitudEvento.de(routingKey, solicitud);
        try {
            if (generaTrabajo) {
                rabbit.convertAndSend(props.exchanges().solicitudes(), routingKey, evento);
            }
            rabbit.convertAndSend(props.exchanges().eventos(), routingKey, evento);
            log.info("Evento publicado: {} (solicitud {})", routingKey, solicitud.getId());
        } catch (AmqpException e) {
            log.warn("Evento NO publicado: {} (solicitud {}). La solicitud quedo guardada igual. Causa: {}",
                    routingKey, solicitud.getId(), e.getMessage());
        }
    }
}
