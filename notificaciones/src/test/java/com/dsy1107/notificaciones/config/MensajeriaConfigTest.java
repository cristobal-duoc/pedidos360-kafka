package com.dsy1107.notificaciones.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

// La topologia declarada: cada cola es quorum y sale a SU DLQ por el DLX.
class MensajeriaConfigTest {

    private final MensajeriaProperties props = new MensajeriaProperties(
        new MensajeriaProperties.Exchanges("pedidos360.solicitudes", "pedidos360.eventos", "pedidos360.dlx"),
        new MensajeriaProperties.Colas(
            new MensajeriaProperties.Ruta("notificaciones.aprobador", "notificaciones.aprobador.dlq", "solicitud.creada"),
            new MensajeriaProperties.Ruta("documentos.comprobante", "documentos.comprobante.dlq", "solicitud.aprobada"),
            new MensajeriaProperties.Ruta("auditoria.solicitudes", "auditoria.solicitudes.dlq", "solicitud.#")),
        new MensajeriaProperties.Reintentos(3, 1000, 2));
    private final MensajeriaConfig config = new MensajeriaConfig(props);

    @Test
    void cadaColaEsQuorumYApuntaASuDlq() {
        for (Queue cola : new Queue[] {config.colaNotificaciones(), config.colaComprobantes(), config.colaAuditoria()}) {
            assertThat(cola.isDurable()).isTrue();
            assertThat(cola.getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-dead-letter-exchange", "pedidos360.dlx")
                .containsEntry("x-dead-letter-routing-key", cola.getName() + ".dlq");
        }
    }

    @Test
    void lasRutasUsanElExchangeYLaRoutingKeyCorrectos() {
        Binding notif = config.bindingNotificaciones();
        assertThat(notif.getExchange()).isEqualTo("pedidos360.solicitudes");
        assertThat(notif.getRoutingKey()).isEqualTo("solicitud.creada");

        Binding comp = config.bindingComprobantes();
        assertThat(comp.getExchange()).isEqualTo("pedidos360.solicitudes");
        assertThat(comp.getRoutingKey()).isEqualTo("solicitud.aprobada");

        Binding aud = config.bindingAuditoria();
        assertThat(aud.getExchange()).isEqualTo("pedidos360.eventos");
        assertThat(aud.getRoutingKey()).isEqualTo("solicitud.#");
    }

    @Test
    void cadaDlqRecibeDelDlxConSuPropioNombre() {
        Binding dlq = config.bindingDlqAuditoria();
        assertThat(dlq.getExchange()).isEqualTo("pedidos360.dlx");
        assertThat(dlq.getDestination()).isEqualTo("auditoria.solicitudes.dlq");
        assertThat(dlq.getRoutingKey()).isEqualTo("auditoria.solicitudes.dlq");
    }
}
