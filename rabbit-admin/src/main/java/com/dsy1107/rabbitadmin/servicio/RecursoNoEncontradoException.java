package com.dsy1107.rabbitadmin.servicio;

// La cola o el exchange pedido no existe en RabbitMQ. El controlador lo traduce a 404.
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
