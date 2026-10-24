package com.dsy1107.notificaciones.notificacion;

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

// Consumidor de notificaciones.aprobador (routing key solicitud.creada, exchange direct).
//
// Manejo de la confirmacion, explicito en cada caso:
//   todo bien                    -> basicAck: RabbitMQ borra el mensaje.
//   error recuperable            -> se reintenta (Reintentos); si se agotan, basicNack -> DLQ.
//   mensaje invalido             -> sin reintentos: basicNack -> DLQ.
//   error inesperado (un bug)    -> sin reintentos: basicNack -> DLQ.
// basicNack siempre con requeue=false: devolverlo a la cola lo reentregaria en bucle.
// Con requeue=false RabbitMQ lo manda al DLX configurado en la cola, que lo deja en la DLQ.
@Component
public class NotificacionListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionListener.class);

    private final LectorEventos lector;
    private final Reintentos reintentos;
    private final NotificacionService servicio;

    public NotificacionListener(LectorEventos lector, Reintentos reintentos, NotificacionService servicio) {
        this.lector = lector;
        this.reintentos = reintentos;
        this.servicio = servicio;
    }

    @RabbitListener(id = "notificaciones-aprobador",
            queues = "${mensajeria.colas.notificaciones.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        try {
            SolicitudEvento evento = lector.leer(mensaje);
            reintentos.ejecutar("avisar al aprobador", () -> servicio.avisarAprobador(evento));
            canal.basicAck(tag, false);
        } catch (MensajeInvalidoException e) {
            log.error("[DLQ] Notificacion rechazada, mensaje invalido: {}. Cuerpo: {}",
                    e.getMessage(), new String(mensaje.getBody()));
            canal.basicNack(tag, false, false);
        } catch (ErrorTransitorioException e) {
            log.error("[DLQ] Notificacion rechazada tras agotar los reintentos: {}", e.getMessage());
            canal.basicNack(tag, false, false);
        } catch (RuntimeException e) {
            log.error("[DLQ] Notificacion rechazada por un error inesperado", e);
            canal.basicNack(tag, false, false);
        }
        // Si basicAck/basicNack fallan (el canal se cerro), la IOException sube: el
        // mensaje quedo sin confirmar y RabbitMQ lo reentrega a otro consumidor.
    }
}
