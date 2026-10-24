package com.dsy1107.notificaciones.comun;

// Error RECUPERABLE: algo externo no respondio (el servidor de correo, el de
// documentos). Vale la pena reintentar; si sigue fallando, va a la DLQ.
public class ErrorTransitorioException extends RuntimeException {

    public ErrorTransitorioException(String mensaje) {
        super(mensaje);
    }
}
