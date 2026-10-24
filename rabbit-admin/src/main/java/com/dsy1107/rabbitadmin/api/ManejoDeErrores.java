package com.dsy1107.rabbitadmin.api;

import com.dsy1107.rabbitadmin.servicio.RecursoNoEncontradoException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

// Traduce cada error a un codigo HTTP y un mensaje claro en JSON:
//   400  la entrada no es valida (dice que campo y por que)
//   404  la cola o el exchange no existe
//   409  RabbitMQ rechazo la operacion (p. ej. la cola ya existe con otra configuracion)
//   503  ningun nodo de RabbitMQ responde
@RestControllerAdvice
public class ManejoDeErrores {

    // @Valid sobre un @RequestBody o un @ModelAttribute
    @ExceptionHandler(BindException.class)
    ResponseEntity<Map<String, Object>> datosInvalidos(BindException e) {
        List<String> detalles = e.getBindingResult().getAllErrors().stream()
            .map(err -> (err instanceof org.springframework.validation.FieldError f ? f.getField() : err.getObjectName())
                + ": " + err.getDefaultMessage())
            .toList();
        return error(HttpStatus.BAD_REQUEST, "Datos invalidos", detalles);
    }

    // @Pattern sobre un @PathVariable
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> parametroInvalido(HandlerMethodValidationException e) {
        List<String> detalles = e.getParameterValidationResults().stream()
            .flatMap(r -> r.getResolvableErrors().stream()
                .map(err -> r.getMethodParameter().getParameterName() + ": " + err.getDefaultMessage()))
            .toList();
        return error(HttpStatus.BAD_REQUEST, "Datos invalidos", detalles);
    }

    // JSON roto, o un tipo de exchange que no existe (p. ej. "tipo": "HEADERZ")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "Cuerpo invalido",
            List.of("JSON mal formado o valor no permitido (tipo de exchange: DIRECT, TOPIC o FANOUT)"));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<Map<String, Object>> noExiste(RecursoNoEncontradoException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    // Sin broker: conexion rechazada (AmqpConnectException) o un nodo que no
    // contesta y vence el connection-timeout (SocketTimeoutException).
    @ExceptionHandler(AmqpException.class)
    ResponseEntity<Map<String, Object>> rabbit(AmqpException e) {
        if (sinBroker(e)) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Ningun nodo de RabbitMQ responde", List.of());
        }
        return error(HttpStatus.CONFLICT, "RabbitMQ rechazo la operacion", List.of(motivoDeRabbit(e)));
    }

    private static boolean sinBroker(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof AmqpConnectException || c instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    // El motivo real viene en la causa mas profunda, p. ej.
    // "PRECONDITION_FAILED - inequivalent arg 'x-queue-type' for queue 'demo.queue'".
    private static String motivoDeRabbit(Throwable e) {
        String motivo = e.getMessage();
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c.getMessage() != null) {
                motivo = c.getMessage();
            }
        }
        return String.valueOf(motivo);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String error, List<String> detalles) {
        return ResponseEntity.status(estado).body(Map.of("error", error, "detalles", detalles));
    }
}
