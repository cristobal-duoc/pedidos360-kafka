package com.dsy1107.notificaciones.auditoria;

import com.dsy1107.notificaciones.comun.LectorEventos;
import com.dsy1107.notificaciones.comun.SolicitudEvento;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Consumidor Kafka de los DOS topicos (creadas y resueltas), en el grupo
// auditoria-solicitudes. Al ser otro grupo, recibe tambien las creadas que ya
// recibio notificaciones-aprobador: cada grupo lleva sus propios offsets.
// Solo lee el evento y se lo pasa al negocio (AuditoriaService).
@Component
public class AuditoriaKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaKafkaListener.class);

    private final LectorEventos lector;
    private final AuditoriaService servicio;

    public AuditoriaKafkaListener(LectorEventos lector, AuditoriaService servicio) {
        this.lector = lector;
        this.servicio = servicio;
    }

    @KafkaListener(
            topics = {"${kafka-eventos.topicos.creadas}", "${kafka-eventos.topicos.resueltas}"},
            groupId = "${kafka-eventos.grupos.auditoria}")
    public void recibir(ConsumerRecord<String, byte[]> registro) {
        log.info("[KAFKA] {} | particion {} offset {} | clave {}",
                registro.topic(), registro.partition(), registro.offset(), registro.key());
        SolicitudEvento evento = lector.leer(registro.value());
        servicio.registrar(evento);
    }
}
