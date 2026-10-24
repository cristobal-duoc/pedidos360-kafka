package com.dsy1107.rabbitadmin.api;

import com.dsy1107.rabbitadmin.servicio.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// La API REST del administrador. Recursos en plural, verbo HTTP segun la accion:
//
//   POST   /api/rabbitmq/queues               crea una cola quorum   201 | 400 | 404 | 409
//   DELETE /api/rabbitmq/queues/{nombre}      la elimina             204 | 400 | 404
//   POST   /api/rabbitmq/exchanges            crea un exchange       201 | 400 | 409
//   DELETE /api/rabbitmq/exchanges/{nombre}   lo elimina             204 | 400 | 404
//   POST   /api/rabbitmq/bindings             une exchange y cola    201 | 400 | 404
//   DELETE /api/rabbitmq/bindings?exchange=&cola=&routingKey=        204 | 400 | 404
//
// Todas exigen un JWT de Cognito con el rol aprobadores (401 / 403). Si no hay
// broker, 503. El controlador solo valida la entrada y delega en el servicio.
@RestController
@RequestMapping("/api/rabbitmq")
public class RabbitAdminController {

    private final RabbitAdminService servicio;

    public RabbitAdminController(RabbitAdminService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/queues")
    public ResponseEntity<Map<String, String>> crearCola(@Valid @RequestBody CrearColaRequest pedido) {
        servicio.crearCola(pedido);
        return ResponseEntity.created(URI.create("/api/rabbitmq/queues/" + pedido.nombre()))
            .body(Map.of("mensaje", "Cola '" + pedido.nombre() + "' creada (quorum)"));
    }

    @DeleteMapping("/queues/{nombre}")
    public ResponseEntity<Void> eliminarCola(
            @PathVariable @Pattern(regexp = Nombres.PATRON, message = Nombres.MENSAJE) String nombre) {
        servicio.eliminarCola(nombre);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, String>> crearExchange(@Valid @RequestBody CrearExchangeRequest pedido) {
        servicio.crearExchange(pedido);
        return ResponseEntity.created(URI.create("/api/rabbitmq/exchanges/" + pedido.nombre()))
            .body(Map.of("mensaje", "Exchange '" + pedido.nombre() + "' creado (" + pedido.tipo() + ")"));
    }

    @DeleteMapping("/exchanges/{nombre}")
    public ResponseEntity<Void> eliminarExchange(
            @PathVariable @Pattern(regexp = Nombres.PATRON, message = Nombres.MENSAJE) String nombre) {
        servicio.eliminarExchange(nombre);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bindings")
    public ResponseEntity<Map<String, String>> crearBinding(@Valid @RequestBody BindingRequest pedido) {
        servicio.crearBinding(pedido);
        return ResponseEntity.status(201).body(Map.of("mensaje",
            "Binding creado: " + pedido.exchange() + " --" + pedido.routingKey() + "--> " + pedido.cola()));
    }

    // Un binding no tiene id propio: se identifica por sus tres partes, en la query.
    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@Valid @ModelAttribute BindingRequest pedido) {
        servicio.eliminarBinding(pedido);
        return ResponseEntity.noContent().build();
    }
}
