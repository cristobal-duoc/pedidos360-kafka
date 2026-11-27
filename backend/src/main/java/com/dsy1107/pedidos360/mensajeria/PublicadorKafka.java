package com.dsy1107.pedidos360.mensajeria;

import com.dsy1107.pedidos360.Solicitud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Implementacion con Kafka. Dos topicos, uno por flujo:
// - creadas  : cada solicitud nueva.
// - resueltas: cada aprobacion o rechazo.
//
// La clave es el id de la solicitud: sus eventos van siempre a la misma particion
// y se leen en orden. Si Kafka no responde, la solicitud YA quedo guardada: el
// evento se pierde y queda en el log, pero el usuario no ve un error.
@Component
class PublicadorKafka implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorKafka.class);

    private final KafkaTemplate<String, SolicitudEvento> kafka;
    private final KafkaTopicosProperties topicos;
    private final MensajeriaProperties props;

    PublicadorKafka(KafkaTemplate<String, SolicitudEvento> kafka, KafkaTopicosProperties topicos,
                    MensajeriaProperties props) {
        this.kafka = kafka;
        this.topicos = topicos;
        this.props = props;
    }

    @Override
    public void solicitudCreada(Solicitud solicitud) {
        publicar(topicos.creadas().nombre(), SolicitudEvento.de(props.routingKeys().creada(), solicitud));
    }

    @Override
    public void solicitudResuelta(Solicitud solicitud) {
        String tipo = solicitud.getEstado() == Solicitud.Estado.APROBADA
            ? props.routingKeys().aprobada()
            : props.routingKeys().rechazada();
        publicar(topicos.resueltas().nombre(), SolicitudEvento.de(tipo, solicitud));
    }

    private void publicar(String topico, SolicitudEvento evento) {
        String clave = String.valueOf(evento.id());
        try {
            kafka.send(topico, clave, evento).whenComplete((resultado, error) -> {
                if (error == null) {
                    log.info("Evento Kafka publicado: {} (solicitud {}) -> {} particion {} offset {}",
                        evento.tipo(), evento.id(), topico,
                        resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset());
                } else {
                    log.warn("Evento Kafka NO publicado: {} (solicitud {}). Causa: {}",
                        evento.tipo(), evento.id(), error.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.warn("Evento Kafka NO publicado: {} (solicitud {}). La solicitud quedo guardada igual. Causa: {}",
                evento.tipo(), evento.id(), e.getMessage());
        }
    }
}
