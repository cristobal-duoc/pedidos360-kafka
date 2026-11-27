package com.dsy1107.kafkaadmin.servicio;

import org.springframework.http.HttpStatus;

// Un error de Kafka ya traducido a lo que significa para quien llama a la API.
public class ErrorKafka extends RuntimeException {

    private final HttpStatus estado;

    public ErrorKafka(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus estado() {
        return estado;
    }
}
