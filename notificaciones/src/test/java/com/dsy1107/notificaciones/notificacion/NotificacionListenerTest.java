package com.dsy1107.notificaciones.notificacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dsy1107.notificaciones.comun.ErrorTransitorioException;
import com.dsy1107.notificaciones.comun.LectorEventos;
import com.dsy1107.notificaciones.comun.Reintentos;
import com.dsy1107.notificaciones.config.MensajeriaProperties;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

// Las decisiones de confirmacion del consumidor, una por una.
class NotificacionListenerTest {

    private static final long TAG = 7L;
    private static final String EVENTO_OK =
        "{\"tipo\":\"solicitud.creada\",\"id\":1,\"solicitante\":\"alumno@duocuc.cl\",\"motivo\":\"Vacaciones\",\"dias\":5}";

    private final Channel canal = mock(Channel.class);
    private final NotificacionService servicio = mock(NotificacionService.class);
    // 3 intentos, sin espera, para que la prueba sea instantanea.
    private final Reintentos reintentos = new Reintentos(new MensajeriaProperties(null, null,
        new MensajeriaProperties.Reintentos(3, 0, 1)));
    private final NotificacionListener listener =
        new NotificacionListener(new LectorEventos(JsonMapper.builder().build()), reintentos, servicio);

    private static Message mensaje(String cuerpo) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    void procesadoBien_haceAck() throws Exception {
        listener.recibir(mensaje(EVENTO_OK), canal);
        verify(servicio).avisarAprobador(any());
        verify(canal).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void jsonRoto_vaDirectoALaDlqSinReintentar() throws Exception {
        listener.recibir(mensaje("esto no es json"), canal);
        verify(servicio, never()).avisarAprobador(any());
        verify(canal).basicNack(TAG, false, false);   // requeue=false -> DLX -> DLQ
    }

    @Test
    void eventoSinId_vaDirectoALaDlq() throws Exception {
        listener.recibir(mensaje("{\"tipo\":\"solicitud.creada\"}"), canal);
        verify(servicio, never()).avisarAprobador(any());
        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void errorTransitorio_reintentaTresVecesYLuegoVaALaDlq() throws Exception {
        doThrow(new ErrorTransitorioException("El servidor de correo no respondio"))
            .when(servicio).avisarAprobador(any());
        listener.recibir(mensaje(EVENTO_OK), canal);
        verify(servicio, times(3)).avisarAprobador(any());
        verify(canal).basicNack(TAG, false, false);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void errorTransitorioQueSeRecupera_terminaEnAck() throws Exception {
        doThrow(new ErrorTransitorioException("timeout"))
            .doNothing()
            .when(servicio).avisarAprobador(any());
        listener.recibir(mensaje(EVENTO_OK), canal);
        verify(servicio, times(2)).avisarAprobador(any());
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void errorInesperado_noSeReintentaYVaALaDlq() throws Exception {
        doThrow(new IllegalStateException("bug")).when(servicio).avisarAprobador(any());
        listener.recibir(mensaje(EVENTO_OK), canal);
        verify(servicio, times(1)).avisarAprobador(any());
        verify(canal).basicNack(TAG, false, false);
    }
}
