package com.dsy1107.rabbitadmin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// POST /api/rabbitmq/bindings  (y DELETE con los mismos datos)
// {"exchange": "demo.exchange", "cola": "demo.queue", "routingKey": "demo.#"}
public record BindingRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = Nombres.PATRON, message = Nombres.MENSAJE)
        String exchange,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = Nombres.PATRON, message = Nombres.MENSAJE)
        String cola,

        // Puede ir vacia (un exchange fanout la ignora), pero no ausente.
        @NotNull(message = "es obligatoria (puede ser \"\" para fanout)")
        @Size(max = 255, message = "maximo 255 caracteres")
        String routingKey) {
}
