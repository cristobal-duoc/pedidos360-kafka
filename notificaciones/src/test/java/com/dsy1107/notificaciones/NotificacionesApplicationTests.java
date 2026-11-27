package com.dsy1107.notificaciones;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// El contexto arranca con toda la configuracion. Los listeners no se encienden:
// en las pruebas no hay RabbitMQ ni Kafka.
@SpringBootTest(properties = {
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.kafka.listener.auto-startup=false"
})
class NotificacionesApplicationTests {

    @Test
    void contextLoads() {
    }
}
