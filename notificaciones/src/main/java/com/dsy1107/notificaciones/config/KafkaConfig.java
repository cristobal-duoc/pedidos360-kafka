package com.dsy1107.notificaciones.config;

import com.dsy1107.notificaciones.comun.ErrorTransitorioException;
import com.dsy1107.notificaciones.comun.MensajeInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

// Manejo de errores de los consumidores Kafka (lo usan todos los @KafkaListener).
//
//   error recuperable -> se reintenta con espera creciente (1 s, 2 s), igual que en RabbitMQ.
//   mensaje invalido  -> sin reintentos: reintentarlo daria el mismo resultado.
//   si se agotan      -> queda registrado en el log y el consumidor sigue con el
//                        siguiente evento: un mensaje malo no detiene la particion.
@Configuration
@EnableConfigurationProperties(KafkaEventosProperties.class)
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    DefaultErrorHandler manejoDeErroresKafka(MensajeriaProperties props) {
        MensajeriaProperties.Reintentos r = props.reintentos();
        ExponentialBackOff espera = new ExponentialBackOff(r.esperaInicialMs(), r.multiplicador());
        espera.setMaxAttempts(r.maximo() - 1); // el primer intento + los reintentos = maximo

        DefaultErrorHandler manejador = new DefaultErrorHandler((registro, error) -> {
            Throwable causa = causa(error);
            String como = causa instanceof MensajeInvalidoException
                ? "invalido, sin reintentos"
                : "tras " + r.maximo() + " intentos";
            log.error("[KAFKA] Evento descartado ({}) | topico {} particion {} offset {} | causa: {}",
                como, registro.topic(), registro.partition(), registro.offset(), causa.getMessage());
        }, espera);
        manejador.addNotRetryableExceptions(MensajeInvalidoException.class);
        return manejador;
    }

    // El error propio del negocio si lo hay (mensaje invalido, servicio caido); si no, el mas profundo.
    private static Throwable causa(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && !(c instanceof MensajeInvalidoException)
                && !(c instanceof ErrorTransitorioException)) {
            c = c.getCause();
        }
        return c;
    }
}
