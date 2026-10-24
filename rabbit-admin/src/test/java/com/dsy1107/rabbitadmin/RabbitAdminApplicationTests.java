package com.dsy1107.rabbitadmin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dsy1107.rabbitadmin.servicio.RabbitAdminService;
import com.dsy1107.rabbitadmin.servicio.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// La API del administrador: seguridad, validacion de la entrada y codigos de error.
// RabbitMQ no existe en las pruebas: el servicio es un mock.
@SpringBootTest
@AutoConfigureMockMvc
class RabbitAdminApplicationTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RabbitAdminService servicio;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static RequestPostProcessor como(String grupo) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + grupo));
    }

    private org.springframework.test.web.servlet.ResultActions crearCola(String json, RequestPostProcessor quien)
            throws Exception {
        return mvc.perform(post("/api/rabbitmq/queues").with(quien)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---------- Seguridad ----------

    @Test
    void sinToken_401() throws Exception {
        mvc.perform(post("/api/rabbitmq/queues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"demo.queue\"}"))
            .andExpect(status().isUnauthorized());
        verify(servicio, never()).crearCola(any());
    }

    @Test
    void solicitante_403() throws Exception {
        crearCola("{\"nombre\":\"demo.queue\"}", como("solicitantes")).andExpect(status().isForbidden());
        verify(servicio, never()).crearCola(any());
    }

    // ---------- Validacion ----------

    @Test
    void colaValida_201ConLocation() throws Exception {
        crearCola("{\"nombre\":\"demo.queue\"}", como("aprobadores"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/rabbitmq/queues/demo.queue"));
        verify(servicio).crearCola(any());
    }

    @Test
    void nombreVacio_400() throws Exception {
        crearCola("{\"nombre\":\"  \"}", como("aprobadores"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Datos invalidos"));
        verify(servicio, never()).crearCola(any());
    }

    @Test
    void sinNombre_400() throws Exception {
        crearCola("{}", como("aprobadores")).andExpect(status().isBadRequest());
    }

    @Test
    void nombreReservadoAmq_400() throws Exception {
        crearCola("{\"nombre\":\"amq.mia\"}", como("aprobadores")).andExpect(status().isBadRequest());
    }

    @Test
    void nombreConEspacios_400() throws Exception {
        crearCola("{\"nombre\":\"mi cola\"}", como("aprobadores")).andExpect(status().isBadRequest());
    }

    @Test
    void routingKeyDeDlqSinExchange_400() throws Exception {
        crearCola("{\"nombre\":\"demo.queue\",\"deadLetterRoutingKey\":\"x\"}", como("aprobadores"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void tipoDeExchangeInexistente_400() throws Exception {
        mvc.perform(post("/api/rabbitmq/exchanges").with(como("aprobadores"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"demo.exchange\",\"tipo\":\"HEADERZ\"}"))
            .andExpect(status().isBadRequest());
        verify(servicio, never()).crearExchange(any());
    }

    @Test
    void exchangeSinTipo_400() throws Exception {
        mvc.perform(post("/api/rabbitmq/exchanges").with(como("aprobadores"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"demo.exchange\"}"))
            .andExpect(status().isBadRequest());
        verify(servicio, never()).crearExchange(any());
    }

    @Test
    void exchangeTopicValido_201() throws Exception {
        mvc.perform(post("/api/rabbitmq/exchanges").with(como("aprobadores"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"demo.exchange\",\"tipo\":\"TOPIC\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void bindingSinRoutingKey_400() throws Exception {
        mvc.perform(post("/api/rabbitmq/bindings").with(como("aprobadores"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"exchange\":\"e\",\"cola\":\"c\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void eliminarColaConNombreInvalido_400() throws Exception {
        mvc.perform(delete("/api/rabbitmq/queues/amq.algo").with(como("aprobadores")))
            .andExpect(status().isBadRequest());
        verify(servicio, never()).eliminarCola(any());
    }

    @Test
    void eliminarBindingPorQuery_204() throws Exception {
        mvc.perform(delete("/api/rabbitmq/bindings").with(como("aprobadores"))
                .param("exchange", "demo.exchange").param("cola", "demo.queue").param("routingKey", "demo.#"))
            .andExpect(status().isNoContent());
        verify(servicio).eliminarBinding(any());
    }

    // ---------- Errores de RabbitMQ ----------

    @Test
    void colaQueNoExiste_404() throws Exception {
        doThrow(new RecursoNoEncontradoException("La cola 'x' no existe")).when(servicio).eliminarCola("x");
        mvc.perform(delete("/api/rabbitmq/queues/x").with(como("aprobadores")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("La cola 'x' no existe"));
    }

    @Test
    void rabbitRechaza_409() throws Exception {
        doThrow(new AmqpIOException(new java.io.IOException("PRECONDITION_FAILED - inequivalent arg 'x-queue-type'")))
            .when(servicio).crearCola(any());
        crearCola("{\"nombre\":\"demo.queue\"}", como("aprobadores")).andExpect(status().isConflict());
    }

    @Test
    void nodoQueNoContesta_503() throws Exception {
        doThrow(new AmqpIOException(new java.net.SocketTimeoutException("connect timed out")))
            .when(servicio).crearCola(any());
        crearCola("{\"nombre\":\"demo.queue\"}", como("aprobadores")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void sinBroker_503() throws Exception {
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
            .when(servicio).crearCola(any());
        crearCola("{\"nombre\":\"demo.queue\"}", como("aprobadores")).andExpect(status().isServiceUnavailable());
    }
}
