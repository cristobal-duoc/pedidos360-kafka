package com.dsy1107.pedidos360.mensajeria;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Los NOMBRES de la mensajeria, leidos de application.yml (bloque "mensajeria").
// Ninguna clase escribe un nombre de exchange o routing key a mano: todas lo
// toman de aqui.
@ConfigurationProperties(prefix = "mensajeria")
public record MensajeriaProperties(Exchanges exchanges, RoutingKeys routingKeys) {

    // direct: trabajo dirigido a una cola concreta (avisar, generar el comprobante).
    // topic : difusion de eventos a quien quiera escucharlos (auditoria).
    public record Exchanges(String solicitudes, String eventos) {
    }

    public record RoutingKeys(String creada, String aprobada, String rechazada) {
    }
}
