package com.dsy1107.notificaciones.comun;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Convierte el cuerpo del mensaje en un SolicitudEvento y valida que traiga lo minimo.
//
// Se lee a mano y no con un MessageConverter a proposito: si la conversion fallara
// antes de entrar al listener, Spring AMQP lo rechazaria por su cuenta, sin pasar
// por este codigo ni dejar la marca [DLQ]. Leyendolo dentro, un JSON roto es un
// error mas que se maneja explicitamente: basicNack y a la DLQ.
@Component
public class LectorEventos {

    private final ObjectMapper json;

    public LectorEventos(ObjectMapper json) {
        this.json = json;
    }

    public SolicitudEvento leer(Message mensaje) {
        SolicitudEvento evento;
        try {
            evento = json.readValue(mensaje.getBody(), SolicitudEvento.class);
        } catch (JacksonException e) {
            throw new MensajeInvalidoException("El cuerpo no es un evento JSON valido", e);
        }
        if (evento == null || evento.id() == null || evento.tipo() == null) {
            throw new MensajeInvalidoException("Al evento le falta el id o el tipo", null);
        }
        return evento;
    }
}
