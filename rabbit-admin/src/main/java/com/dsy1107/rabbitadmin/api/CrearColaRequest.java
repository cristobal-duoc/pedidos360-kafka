package com.dsy1107.rabbitadmin.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// POST /api/rabbitmq/queues   (la cola siempre se crea QUORUM)
// {"nombre": "demo.queue", "deadLetterExchange": "pedidos360.dlx",
//  "deadLetterRoutingKey": "demo.queue.dlq"}
public record CrearColaRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = Nombres.PATRON, message = Nombres.MENSAJE)
        String nombre,

        // Opcional: a que exchange van los mensajes rechazados de esta cola. Tiene que existir.
        @Pattern(regexp = Nombres.PATRON, message = Nombres.MENSAJE)
        String deadLetterExchange,

        @Size(max = 255, message = "maximo 255 caracteres")
        String deadLetterRoutingKey) {

    // Una routing key de DLQ sin exchange de DLQ no sirve de nada: se rechaza.
    @AssertTrue(message = "deadLetterRoutingKey requiere deadLetterExchange")
    public boolean isDeadLetterCoherente() {
        return deadLetterRoutingKey == null || deadLetterExchange != null;
    }
}
