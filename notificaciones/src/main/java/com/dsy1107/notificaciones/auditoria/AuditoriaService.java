package com.dsy1107.notificaciones.auditoria;

import com.dsy1107.notificaciones.comun.SolicitudEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Negocio: dejar registro de todo lo que le pasa a una solicitud.
// No sabe nada de RabbitMQ: recibe un evento ya leido.
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    public void registrar(SolicitudEvento evento) {
        log.info("[AUDITORIA] {} | solicitud #{} | {} | estado {} | {}",
                evento.tipo(), evento.id(), evento.solicitante(), evento.estado(), evento.fecha());
    }
}
