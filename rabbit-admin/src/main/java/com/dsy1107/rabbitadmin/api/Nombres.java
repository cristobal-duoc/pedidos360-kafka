package com.dsy1107.rabbitadmin.api;

// La regla para el nombre de una cola o un exchange, en un solo lugar:
// - empieza con letra o numero, y solo usa letras, numeros, '.', '_' y '-';
// - maximo 255 caracteres (el limite de RabbitMQ);
// - no empieza con "amq.": RabbitMQ reserva ese prefijo y rechazaria la creacion.
final class Nombres {

    static final String PATRON = "^(?!amq\\.)[A-Za-z0-9][A-Za-z0-9._-]{0,254}$";
    static final String MENSAJE =
        "solo letras, numeros, '.', '_' y '-'; maximo 255; no puede empezar con 'amq.' (reservado)";

    private Nombres() {
    }
}
