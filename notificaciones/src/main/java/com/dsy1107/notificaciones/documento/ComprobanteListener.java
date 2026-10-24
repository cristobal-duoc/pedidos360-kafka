package com.dsy1107.notificaciones.documento;

import com.dsy1107.notificaciones.comun.ErrorTransitorioException;
import com.dsy1107.notificaciones.comun.LectorEventos;
import com.dsy1107.notificaciones.comun.MensajeInvalidoException;
import com.dsy1107.notificaciones.comun.Reintentos;
import com.dsy1107.notificaciones.comun.SolicitudEvento;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// Consumidor de documentos.comprobante (routing key solicitud.aprobada, exchange direct).
//
// Manejo de la confirmacion, explicito en cada caso:
//   todo bien                    -> basicAck.
//   error recuperable            -> se reintenta; si se agotan, basicNack -> DLQ.
//   mensaje invalido             -> sin reintentos: basicNack -> DLQ.
//   error inesperado (un bug)    -> sin reintentos: basicNack -> DLQ.
// basicNack siempre con requeue=false, para que RabbitMQ lo mande al DLX y no a la misma cola.
@Component
public class ComprobanteListener {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteListener.class);

    private final LectorEventos lector;
    private final Reintentos reintentos;
    private final ComprobanteService servicio;

    public ComprobanteListener(LectorEventos lector, Reintentos reintentos, ComprobanteService servicio) {
        this.lector = lector;
        this.reintentos = reintentos;
        this.servicio = servicio;
    }

    @RabbitListener(
            queues = "${mensajeria.colas.comprobantes.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        try {
            SolicitudEvento evento = lector.leer(mensaje);
            reintentos.ejecutar("generar el comprobante", () -> servicio.generar(evento));
            canal.basicAck(tag, false);
        } catch (MensajeInvalidoException e) {
            log.error("[DLQ] Comprobante rechazado, mensaje invalido: {}. Cuerpo: {}",
                    e.getMessage(), new String(mensaje.getBody()));
            canal.basicNack(tag, false, false);
        } catch (ErrorTransitorioException e) {
            log.error("[DLQ] Comprobante rechazado tras agotar los reintentos: {}", e.getMessage());
            canal.basicNack(tag, false, false);
        } catch (RuntimeException e) {
            log.error("[DLQ] Comprobante rechazado por un error inesperado", e);
            canal.basicNack(tag, false, false);
        }
    }
}
