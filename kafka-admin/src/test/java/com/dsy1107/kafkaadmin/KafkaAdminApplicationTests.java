package com.dsy1107.kafkaadmin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dsy1107.kafkaadmin.servicio.ErrorKafka;
import com.dsy1107.kafkaadmin.servicio.KafkaAdminService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// La API del administrador: seguridad, validacion de la entrada y codigos de error.
// Kafka no existe en las pruebas: el servicio es un mock.
@SpringBootTest
@AutoConfigureMockMvc
class KafkaAdminApplicationTests {

    private static final String TOPICO = "{\"nombre\":\"demo.topico\",\"particiones\":3,\"replicas\":3,"
        + "\"configuraciones\":{\"retention.ms\":\"86400000\"}}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KafkaAdminService servicio;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static RequestPostProcessor como(String grupo) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + grupo));
    }

    // ---------- Seguridad ----------

    @Test
    void sinToken_401() throws Exception {
        mvc.perform(get("/api/kafka/topics")).andExpect(status().isUnauthorized());
    }

    @Test
    void solicitanteNoAdministra_403() throws Exception {
        mvc.perform(post("/api/kafka/topics").with(como("solicitantes"))
                .contentType(MediaType.APPLICATION_JSON).content(TOPICO))
            .andExpect(status().isForbidden());
        verify(servicio, never()).crearTopico(any());
    }

    // ---------- Topicos ----------

    @Test
    void aprobadorCreaTopico_201() throws Exception {
        when(servicio.describirTopico("demo.topico")).thenReturn(Map.of("nombre", "demo.topico", "particiones", 3));
        mvc.perform(post("/api/kafka/topics").with(como("aprobadores"))
                .contentType(MediaType.APPLICATION_JSON).content(TOPICO))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.particiones").value(3));
    }

    @Test
    void topicoSinParticiones_400() throws Exception {
        mvc.perform(post("/api/kafka/topics").with(como("aprobadores"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"demo\",\"replicas\":3}"))
            .andExpect(status().isBadRequest());
        verify(servicio, never()).crearTopico(any());
    }

    @Test
    void masReplicasQueBrokers_400() throws Exception {
        mvc.perform(post("/api/kafka/topics").with(como("aprobadores"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"demo\",\"particiones\":3,\"replicas\":4}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void topicoRepetido_409() throws Exception {
        doThrow(new ErrorKafka(HttpStatus.CONFLICT, "El topico ya existe")).when(servicio).crearTopico(any());
        mvc.perform(post("/api/kafka/topics").with(como("aprobadores"))
                .contentType(MediaType.APPLICATION_JSON).content(TOPICO))
            .andExpect(status().isConflict());
    }

    @Test
    void cambiaConfiguracion_200() throws Exception {
        when(servicio.cambiarConfiguraciones(eq("demo.topico"), any()))
            .thenReturn(Map.of("retention.ms", "3600000"));
        mvc.perform(put("/api/kafka/topics/demo.topico/configuraciones").with(como("aprobadores"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuraciones\":{\"retention.ms\":\"3600000\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['retention.ms']").value("3600000"));
    }

    @Test
    void eliminaTopicoInexistente_404() throws Exception {
        doThrow(new ErrorKafka(HttpStatus.NOT_FOUND, "El topico no existe")).when(servicio).eliminarTopico("nada");
        mvc.perform(delete("/api/kafka/topics/nada").with(como("aprobadores")))
            .andExpect(status().isNotFound());
    }

    @Test
    void sinBrokers_503() throws Exception {
        when(servicio.listarTopicos()).thenThrow(new ErrorKafka(HttpStatus.SERVICE_UNAVAILABLE, "Ningun broker"));
        mvc.perform(get("/api/kafka/topics").with(como("aprobadores")))
            .andExpect(status().isServiceUnavailable());
    }

    // ---------- Consumer groups ----------

    @Test
    void inspeccionaGrupo_200() throws Exception {
        when(servicio.describirGrupo("auditoria-solicitudes"))
            .thenReturn(Map.of("grupo", "auditoria-solicitudes", "lagTotal", 0, "particiones", List.of()));
        mvc.perform(get("/api/kafka/consumer-groups/auditoria-solicitudes").with(como("aprobadores")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lagTotal").value(0));
    }
}
