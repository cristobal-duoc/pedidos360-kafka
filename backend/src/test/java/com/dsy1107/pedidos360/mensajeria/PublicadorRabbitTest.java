package com.dsy1107.pedidos360.mensajeria;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dsy1107.pedidos360.Solicitud;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

// Verifica a donde va cada evento y que una caida de RabbitMQ no rompa el negocio.
class PublicadorRabbitTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final MensajeriaProperties props = new MensajeriaProperties(
        new MensajeriaProperties.Exchanges("pedidos360.solicitudes", "pedidos360.eventos"),
        new MensajeriaProperties.RoutingKeys("solicitud.creada", "solicitud.aprobada", "solicitud.rechazada"));
    private final PublicadorRabbit publicador = new PublicadorRabbit(rabbit, props);

    private static Solicitud solicitud(Solicitud.Estado estado) {
        Solicitud s = new Solicitud("alumno@duocuc.cl", "Vacaciones", 5);
        s.setEstado(estado);
        return s;
    }

    @Test
    void creada_vaALosDosExchangesConSuRoutingKey() {
        publicador.solicitudCreada(solicitud(Solicitud.Estado.PENDIENTE));
        verify(rabbit).convertAndSend(eq("pedidos360.solicitudes"), eq("solicitud.creada"), any(SolicitudEvento.class));
        verify(rabbit).convertAndSend(eq("pedidos360.eventos"), eq("solicitud.creada"), any(SolicitudEvento.class));
    }

    @Test
    void aprobada_vaALosDosExchanges() {
        publicador.solicitudResuelta(solicitud(Solicitud.Estado.APROBADA));
        verify(rabbit).convertAndSend(eq("pedidos360.solicitudes"), eq("solicitud.aprobada"), any(SolicitudEvento.class));
        verify(rabbit).convertAndSend(eq("pedidos360.eventos"), eq("solicitud.aprobada"), any(SolicitudEvento.class));
    }

    // La rechazada no genera trabajo: solo queda registrada en el topic (auditoria).
    @Test
    void rechazada_vaSoloAlTopic() {
        publicador.solicitudResuelta(solicitud(Solicitud.Estado.RECHAZADA));
        verify(rabbit, never()).convertAndSend(eq("pedidos360.solicitudes"), anyString(), any(SolicitudEvento.class));
        verify(rabbit).convertAndSend(eq("pedidos360.eventos"), eq("solicitud.rechazada"), any(SolicitudEvento.class));
    }

    @Test
    void rabbitCaido_noLanzaExcepcion() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
            .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatCode(() -> publicador.solicitudCreada(solicitud(Solicitud.Estado.PENDIENTE)))
            .doesNotThrowAnyException();
    }
}
