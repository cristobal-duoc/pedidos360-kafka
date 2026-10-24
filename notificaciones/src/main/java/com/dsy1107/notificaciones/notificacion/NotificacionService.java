package com.dsy1107.notificaciones.notificacion;

import com.dsy1107.notificaciones.comun.ErrorTransitorioException;
import com.dsy1107.notificaciones.comun.SolicitudEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Negocio: avisar al aprobador que tiene una solicitud por revisar.
// No sabe nada de RabbitMQ: recibe un evento ya leido.
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final String marcaFallo;

    public NotificacionService(@Value("${simulacion.marca-fallo}") String marcaFallo) {
        this.marcaFallo = marcaFallo;
    }

    public void avisarAprobador(SolicitudEvento evento) {
        // Simulacion del servidor de correo caido (ver simulacion.marca-fallo).
        if (evento.motivo() != null && evento.motivo().contains(marcaFallo)) {
            throw new ErrorTransitorioException("El servidor de correo no respondio");
        }
        log.info("[CORREO] Para: aprobadores | Nueva solicitud #{} de {}: {} ({} dias)",
                evento.id(), evento.solicitante(), evento.motivo(), evento.dias());
    }
}
