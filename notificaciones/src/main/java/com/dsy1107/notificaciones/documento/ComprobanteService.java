package com.dsy1107.notificaciones.documento;

import com.dsy1107.notificaciones.comun.ErrorTransitorioException;
import com.dsy1107.notificaciones.comun.SolicitudEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Negocio: generar el comprobante de unas vacaciones aprobadas.
// No sabe nada de RabbitMQ: recibe un evento ya leido.
@Service
public class ComprobanteService {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteService.class);

    private final String marcaFallo;

    public ComprobanteService(@Value("${simulacion.marca-fallo}") String marcaFallo) {
        this.marcaFallo = marcaFallo;
    }

    public void generar(SolicitudEvento evento) {
        // Simulacion del servicio de documentos caido (ver simulacion.marca-fallo).
        if (evento.motivo() != null && evento.motivo().contains(marcaFallo)) {
            throw new ErrorTransitorioException("El servicio de documentos no respondio");
        }
        log.info("[COMPROBANTE] COMP-{} | {} | {} dias aprobados por {} | \"{}\"",
                evento.id(), evento.solicitante(), evento.dias(), evento.resueltaPor(), evento.comentario());
    }
}
