package com.dsy1107.notificaciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Los NOMBRES de la mensajeria, leidos de application.yml (bloque "mensajeria").
// Es la unica fuente: las clases de configuracion y los listeners los toman de aqui.
@ConfigurationProperties(prefix = "mensajeria")
public record MensajeriaProperties(Exchanges exchanges, Colas colas, Reintentos reintentos) {

    public record Exchanges(String solicitudes, String eventos, String dlx) {
    }

    // Cada caso de uso: la cola principal, su DLQ y la routing key que la alimenta.
    public record Ruta(String nombre, String dlq, String routingKey) {
    }

    public record Colas(Ruta notificaciones, Ruta comprobantes, Ruta auditoria) {
    }

    public record Reintentos(int maximo, long esperaInicialMs, double multiplicador) {
    }
}
