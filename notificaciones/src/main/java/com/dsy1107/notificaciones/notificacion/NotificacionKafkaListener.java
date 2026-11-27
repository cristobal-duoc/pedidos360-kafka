package com.dsy1107.notificaciones.notificacion;

import com.dsy1107.notificaciones.comun.LectorEventos;
import com.dsy1107.notificaciones.comun.SolicitudEvento;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Consumidor Kafka del topico de solicitudes creadas, en el grupo notificaciones-aprobador.
// Solo lee el evento y se lo pasa al negocio (NotificacionService); los errores los
// maneja el DefaultErrorHandler de KafkaConfig.
@Component
public class NotificacionKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionKafkaListener.class);

    private final LectorEventos lector;
    private final NotificacionService servicio;

    public NotificacionKafkaListener(LectorEventos lector, NotificacionService servicio) {
        this.lector = lector;
        this.servicio = servicio;
    }

    @KafkaListener(
            topics = "${kafka-eventos.topicos.creadas}",
            groupId = "${kafka-eventos.grupos.notificaciones}")
    public void recibir(ConsumerRecord<String, byte[]> registro) {
        log.info("[KAFKA] {} | particion {} offset {} | clave {}",
                registro.topic(), registro.partition(), registro.offset(), registro.key());
        SolicitudEvento evento = lector.leer(registro.value());
        servicio.avisarAprobador(evento);
    }
}
