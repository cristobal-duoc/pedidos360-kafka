package com.dsy1107.rabbitadmin.servicio;

import com.dsy1107.rabbitadmin.api.BindingRequest;
import com.dsy1107.rabbitadmin.api.CrearColaRequest;
import com.dsy1107.rabbitadmin.api.CrearExchangeRequest;
import com.rabbitmq.client.ShutdownSignalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

// TODA la logica de administracion de RabbitMQ vive aqui. El controlador llama a
// metodos de alto nivel (crearCola, eliminarExchange...) y no conoce RabbitAdmin,
// QueueBuilder ni nada de la libreria.
//
// Los errores no se esconden: si RabbitMQ rechaza algo, la excepcion sube y el
// controlador responde 409 (o 503 si no hay broker), en vez de un "creada" falso.
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;

    public RabbitAdminService(RabbitAdmin rabbitAdmin, RabbitTemplate rabbitTemplate) {
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ---------- Colas ----------

    // Siempre QUORUM, como en la clase 2.3.1: una replica en cada nodo del cluster.
    // Una cola clasica viviria en un solo nodo y se perderia si ese nodo cae.
    public void crearCola(CrearColaRequest pedido) {
        QueueBuilder cola = QueueBuilder.durable(pedido.nombre()).quorum();
        if (pedido.deadLetterExchange() != null) {
            // Una DLQ que apunta a un exchange inexistente perderia los mensajes rechazados.
            exigirExchange(pedido.deadLetterExchange());
            cola.deadLetterExchange(pedido.deadLetterExchange());
        }
        if (pedido.deadLetterRoutingKey() != null) {
            cola.deadLetterRoutingKey(pedido.deadLetterRoutingKey());
        }
        rabbitAdmin.declareQueue(cola.build());
        log.info("Cola creada: {} (quorum)", pedido.nombre());
    }

    public void eliminarCola(String nombre) {
        exigirCola(nombre);
        // Con (nombre, false, false) el error de RabbitMQ sube: deleteQueue(nombre)
        // a secas lo atraparia y devolveria false, y la API diria 204 sin haber borrado.
        rabbitAdmin.deleteQueue(nombre, false, false);
        log.info("Cola eliminada: {}", nombre);
    }

    // ---------- Exchanges ----------

    public void crearExchange(CrearExchangeRequest pedido) {
        ExchangeBuilder builder = switch (pedido.tipo()) {
            case DIRECT -> ExchangeBuilder.directExchange(pedido.nombre());
            case TOPIC -> ExchangeBuilder.topicExchange(pedido.nombre());
            case FANOUT -> ExchangeBuilder.fanoutExchange(pedido.nombre());
        };
        Exchange exchange = builder.durable(true).build();
        rabbitAdmin.declareExchange(exchange);
        log.info("Exchange creado: {} ({})", pedido.nombre(), pedido.tipo());
    }

    public void eliminarExchange(String nombre) {
        exigirExchange(nombre);
        // Directo sobre el canal: rabbitAdmin.deleteExchange tambien se traga el error.
        rabbitTemplate.execute(canal -> canal.exchangeDelete(nombre));
        log.info("Exchange eliminado: {}", nombre);
    }

    // ---------- Bindings ----------

    public void crearBinding(BindingRequest pedido) {
        rabbitAdmin.declareBinding(binding(pedido));
        log.info("Binding creado: {} --{}--> {}", pedido.exchange(), pedido.routingKey(), pedido.cola());
    }

    public void eliminarBinding(BindingRequest pedido) {
        rabbitAdmin.removeBinding(binding(pedido));
        log.info("Binding eliminado: {} --{}--> {}", pedido.exchange(), pedido.routingKey(), pedido.cola());
    }

    // Un binding une dos cosas que tienen que existir: si falta una, 404 claro
    // (RabbitMQ solo cerraria el canal con un NOT_FOUND).
    private Binding binding(BindingRequest pedido) {
        exigirExchange(pedido.exchange());
        exigirCola(pedido.cola());
        return new Binding(pedido.cola(), Binding.DestinationType.QUEUE,
                pedido.exchange(), pedido.routingKey(), null);
    }

    // ---------- Existencia ----------

    private void exigirCola(String nombre) {
        if (rabbitAdmin.getQueueInfo(nombre) == null) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
    }

    // RabbitAdmin no tiene un "existe el exchange": se pregunta con una declaracion
    // pasiva, que no crea nada. Solo su respuesta NOT_FOUND significa "no existe";
    // cualquier otro fallo (sin broker, timeout, credenciales) sube tal cual.
    private void exigirExchange(String nombre) {
        try {
            rabbitTemplate.execute(canal -> canal.exchangeDeclarePassive(nombre));
        } catch (AmqpException e) {
            for (Throwable c = e; c != null; c = c.getCause()) {
                if (c instanceof ShutdownSignalException cierre && RabbitUtils.isPassiveDeclarationChannelClose(cierre)) {
                    throw new RecursoNoEncontradoException("El exchange '" + nombre + "' no existe");
                }
            }
            throw e;
        }
    }
}
