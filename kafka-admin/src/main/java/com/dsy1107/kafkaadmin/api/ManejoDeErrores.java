package com.dsy1107.kafkaadmin.api;

import com.dsy1107.kafkaadmin.servicio.ErrorKafka;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

// Traduce cada error a un codigo HTTP y un mensaje claro en JSON:
//   400  la entrada no es valida, o Kafka la rechazo (p. ej. bajar particiones)
//   404  el topico o el consumer group no existe
//   409  el topico ya existe
//   503  ningun broker de Kafka responde
@RestControllerAdvice
public class ManejoDeErrores {

    @ExceptionHandler(BindException.class)
    ResponseEntity<Map<String, Object>> datosInvalidos(BindException e) {
        List<String> detalles = e.getBindingResult().getAllErrors().stream()
            .map(err -> (err instanceof org.springframework.validation.FieldError f ? f.getField() : err.getObjectName())
                + ": " + err.getDefaultMessage())
            .toList();
        return error(HttpStatus.BAD_REQUEST, "Datos invalidos", detalles);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> parametroInvalido(HandlerMethodValidationException e) {
        List<String> detalles = e.getParameterValidationResults().stream()
            .flatMap(r -> r.getResolvableErrors().stream()
                .map(err -> r.getMethodParameter().getParameterName() + ": " + err.getDefaultMessage()))
            .toList();
        return error(HttpStatus.BAD_REQUEST, "Datos invalidos", detalles);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "Cuerpo invalido", List.of("JSON mal formado"));
    }

    @ExceptionHandler(ErrorKafka.class)
    ResponseEntity<Map<String, Object>> kafka(ErrorKafka e) {
        return error(e.estado(), e.getMessage(), List.of());
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String error, List<String> detalles) {
        return ResponseEntity.status(estado).body(Map.of("error", error, "detalles", detalles));
    }
}
