package com.dsy1107.notificaciones.comun;

// Error NO recuperable: el mensaje esta mal formado o le faltan datos.
// Reintentarlo daria el mismo resultado, asi que va directo a la DLQ.
public class MensajeInvalidoException extends RuntimeException {

    public MensajeInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
