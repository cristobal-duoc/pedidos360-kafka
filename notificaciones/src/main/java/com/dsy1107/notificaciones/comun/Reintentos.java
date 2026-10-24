package com.dsy1107.notificaciones.comun;

import com.dsy1107.notificaciones.config.MensajeriaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// La politica de reintentos para errores RECUPERABLES, con espera creciente
// (1 s, 2 s, ...). Solo reintenta ErrorTransitorioException: cualquier otro error
// sale de inmediato, porque reintentarlo no cambiaria nada.
//
// Se reintenta dentro del consumidor (y no devolviendo el mensaje a la cola con
// requeue=true) para no crear un bucle: el mensaje se reentregaria al instante y
// fallaria una y otra vez (en quorum, hasta el limite de 20 entregas de RabbitMQ 4).
@Component
public class Reintentos {

    private static final Logger log = LoggerFactory.getLogger(Reintentos.class);

    private final MensajeriaProperties.Reintentos config;

    public Reintentos(MensajeriaProperties props) {
        this.config = props.reintentos();
    }

    // Ejecuta la tarea hasta "maximo" veces. Si todas fallan, relanza el ultimo
    // ErrorTransitorioException para que el listener mande el mensaje a la DLQ.
    public void ejecutar(String que, Runnable tarea) {
        long espera = config.esperaInicialMs();
        for (int intento = 1; ; intento++) {
            try {
                tarea.run();
                return;
            } catch (ErrorTransitorioException e) {
                if (intento >= config.maximo()) {
                    throw e;
                }
                log.warn("Intento {}/{} de {} fallo ({}). Reintento en {} ms",
                        intento, config.maximo(), que, e.getMessage(), espera);
                dormir(espera);
                espera = (long) (espera * config.multiplicador());
            }
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorTransitorioException("Interrumpido mientras esperaba para reintentar");
        }
    }
}
