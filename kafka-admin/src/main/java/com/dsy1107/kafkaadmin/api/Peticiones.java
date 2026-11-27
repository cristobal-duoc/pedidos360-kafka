package com.dsy1107.kafkaadmin.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

// Los cuerpos JSON que recibe la API, con sus validaciones.
public final class Peticiones {

    // Nombre de topico valido en Kafka: letras, numeros, '.', '_' y '-', maximo 249.
    public static final String PATRON_NOMBRE = "^[A-Za-z0-9._-]{1,249}$";
    public static final String MENSAJE_NOMBRE = "solo letras, numeros, '.', '_' y '-'; maximo 249";

    // POST /topics. configuraciones es opcional: p. ej. {"retention.ms": "604800000"}.
    public record CrearTopico(
            @NotNull @Pattern(regexp = PATRON_NOMBRE, message = MENSAJE_NOMBRE) String nombre,
            @NotNull @Min(1) Integer particiones,
            @NotNull @Min(1) @Max(3) Integer replicas, // hay tres brokers
            Map<String, String> configuraciones) {
    }

    // PUT /topics/{nombre}/configuraciones: {"retention.ms": "86400000", "cleanup.policy": "delete"}
    public record CambiarConfiguraciones(@NotEmpty Map<String, String> configuraciones) {
    }

    // PUT /topics/{nombre}/particiones: Kafka solo permite AUMENTARLAS.
    public record CambiarParticiones(@NotNull @Min(1) Integer particiones) {
    }

    private Peticiones() {
    }
}
