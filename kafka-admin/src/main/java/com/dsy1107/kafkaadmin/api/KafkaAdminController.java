package com.dsy1107.kafkaadmin.api;

import com.dsy1107.kafkaadmin.servicio.KafkaAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// La API REST del administrador de Kafka:
//
//   GET    /api/kafka/topics                              lista los topicos            200
//   GET    /api/kafka/topics/{nombre}                     particiones, replicas, config 200 | 404
//   POST   /api/kafka/topics                              crea un topico               201 | 400 | 409
//   PUT    /api/kafka/topics/{nombre}/configuraciones     cambia su configuracion      200 | 400 | 404
//   PUT    /api/kafka/topics/{nombre}/particiones         aumenta sus particiones      200 | 400 | 404
//   DELETE /api/kafka/topics/{nombre}                     lo elimina                   204 | 404
//   GET    /api/kafka/consumer-groups                     lista los consumer groups    200
//   GET    /api/kafka/consumer-groups/{grupo}             offsets y lag por particion  200 | 404
//
// Todas exigen un JWT de Cognito con el rol aprobadores (401 / 403). Sin brokers, 503.
@RestController
@RequestMapping("/api/kafka")
public class KafkaAdminController {

    private final KafkaAdminService servicio;

    public KafkaAdminController(KafkaAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/topics")
    public List<String> listarTopicos() {
        return servicio.listarTopicos();
    }

    @GetMapping("/topics/{nombre}")
    public Map<String, Object> describirTopico(@PathVariable @Pattern(
            regexp = Peticiones.PATRON_NOMBRE, message = Peticiones.MENSAJE_NOMBRE) String nombre) {
        return servicio.describirTopico(nombre);
    }

    @PostMapping("/topics")
    public ResponseEntity<Map<String, Object>> crearTopico(@Valid @RequestBody Peticiones.CrearTopico pedido) {
        servicio.crearTopico(pedido);
        return ResponseEntity.created(URI.create("/api/kafka/topics/" + pedido.nombre()))
            .body(servicio.describirTopico(pedido.nombre()));
    }

    @PutMapping("/topics/{nombre}/configuraciones")
    public Map<String, String> cambiarConfiguraciones(
            @PathVariable @Pattern(regexp = Peticiones.PATRON_NOMBRE, message = Peticiones.MENSAJE_NOMBRE) String nombre,
            @Valid @RequestBody Peticiones.CambiarConfiguraciones pedido) {
        return servicio.cambiarConfiguraciones(nombre, pedido.configuraciones());
    }

    @PutMapping("/topics/{nombre}/particiones")
    public Map<String, Object> aumentarParticiones(
            @PathVariable @Pattern(regexp = Peticiones.PATRON_NOMBRE, message = Peticiones.MENSAJE_NOMBRE) String nombre,
            @Valid @RequestBody Peticiones.CambiarParticiones pedido) {
        servicio.aumentarParticiones(nombre, pedido.particiones());
        return servicio.describirTopico(nombre);
    }

    @DeleteMapping("/topics/{nombre}")
    public ResponseEntity<Void> eliminarTopico(@PathVariable @Pattern(
            regexp = Peticiones.PATRON_NOMBRE, message = Peticiones.MENSAJE_NOMBRE) String nombre) {
        servicio.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/consumer-groups")
    public List<Map<String, Object>> listarGrupos() {
        return servicio.listarGrupos();
    }

    @GetMapping("/consumer-groups/{grupo}")
    public Map<String, Object> describirGrupo(@PathVariable String grupo) {
        return servicio.describirGrupo(grupo);
    }
}
