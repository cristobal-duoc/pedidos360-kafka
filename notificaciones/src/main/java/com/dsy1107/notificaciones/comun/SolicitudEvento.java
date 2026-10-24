package com.dsy1107.notificaciones.comun;

import java.time.Instant;

// El evento que publica el microservicio de solicitudes: una foto de la solicitud.
// Cada microservicio tiene su propia copia del contrato: no comparten codigo, solo
// el formato JSON del mensaje.
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
}
