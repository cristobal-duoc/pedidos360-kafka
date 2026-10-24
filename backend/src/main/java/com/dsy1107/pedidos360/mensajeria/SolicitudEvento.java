package com.dsy1107.pedidos360.mensajeria;

import com.dsy1107.pedidos360.Solicitud;
import java.time.Instant;

// El mensaje que viaja por RabbitMQ: una foto de la solicitud en el momento del evento.
public record SolicitudEvento(
        String tipo,
        Long id,
        String solicitante,
        String motivo,
        Integer dias,
        String estado,
        String comentario,
        String resueltaPor,
        Instant fecha) {

    static SolicitudEvento de(String tipo, Solicitud s) {
        return new SolicitudEvento(tipo, s.getId(), s.getSolicitante(), s.getMotivo(), s.getDias(),
                s.getEstado().name(), s.getComentario(), s.getResueltaPor(), Instant.now());
    }
}
