package com.dsy1107.rabbitadmin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// POST /api/rabbitmq/exchanges
// {"nombre": "demo.exchange", "tipo": "TOPIC"}
public record CrearExchangeRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = Nombres.PATRON, message = Nombres.MENSAJE)
        String nombre,

        @NotNull(message = "es obligatorio: DIRECT, TOPIC o FANOUT")
        TipoExchange tipo) {

    public enum TipoExchange { DIRECT, TOPIC, FANOUT }
}
