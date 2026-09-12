package com.dsy1107.pedidos360;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Verifica las reglas que definen la seguridad del backend:
//   AUTENTICAR -> sin token valido, 401.
//   AUTORIZAR  -> cada papel solo puede lo suyo: el solicitante crea pero no
//                 resuelve; el aprobador resuelve pero no crea. Y nadie toca
//                 la solicitud de otro.
//
// No llama a Cognito: el JwtDecoder esta mockeado y el post-procesador jwt()
// construye la autenticacion con las autoridades que en produccion produce el
// convertidor de SecurityConfig a partir del claim cognito:groups.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.com",
    "cognito.client-id=test",
    "spring.datasource.url=jdbc:h2:mem:seguridad;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SeguridadTest {

    private static final String UNA_SOLICITUD = "{\"motivo\":\"Vacaciones de invierno\",\"dias\":5}";
    private static final String UNA_DECISION = "{\"aprobar\":true,\"comentario\":\"Aprobado\"}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    @MockBean
    JwtDecoder jwtDecoder;

    private static RequestPostProcessor como(String grupo, String email) {
        return jwt()
            .jwt(j -> j.claim("email", email))
            .authorities(new SimpleGrantedAuthority("ROLE_" + grupo));
    }

    @Test
    void sinToken_leer_da401() throws Exception {
        mockMvc.perform(get("/solicitudes"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sinToken_crear_da401() throws Exception {
        mockMvc.perform(post("/solicitudes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(UNA_SOLICITUD))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void cualquierAutenticado_puedeLeer() throws Exception {
        mockMvc.perform(get("/solicitudes").with(como("solicitantes", "alumno@duocuc.cl")))
            .andExpect(status().isOk());
    }

    @Test
    void solicitante_creaYQuedaPendienteASuNombre() throws Exception {
        mockMvc.perform(post("/solicitudes")
                .with(como("solicitantes", "alumno@duocuc.cl"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UNA_SOLICITUD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.solicitante").value("alumno@duocuc.cl"));
    }

    @Test
    void solicitante_noPuedeResolver_da403() throws Exception {
        mockMvc.perform(put("/solicitudes/1/decision")
                .with(como("solicitantes", "alumno@duocuc.cl"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UNA_DECISION))
            .andExpect(status().isForbidden());
    }

    @Test
    void aprobador_noPuedeCrear_da403() throws Exception {
        mockMvc.perform(post("/solicitudes")
                .with(como("aprobadores", "jefatura@duocuc.cl"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UNA_SOLICITUD))
            .andExpect(status().isForbidden());
    }

    @Test
    void aprobador_resuelveYRegistraComentario() throws Exception {
        String creada = mockMvc.perform(post("/solicitudes")
                .with(como("solicitantes", "alumno@duocuc.cl"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UNA_SOLICITUD))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long id = json.readTree(creada).get("id").asLong();

        mockMvc.perform(put("/solicitudes/" + id + "/decision")
                .with(como("aprobadores", "jefatura@duocuc.cl"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aprobar\":false,\"comentario\":\"No hay cupo en esa fecha\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RECHAZADA"))
            .andExpect(jsonPath("$.comentario").value("No hay cupo en esa fecha"))
            .andExpect(jsonPath("$.resueltaPor").value("jefatura@duocuc.cl"));
    }

    private long crearComo(String email) throws Exception {
        String creada = mockMvc.perform(post("/solicitudes")
                .with(como("solicitantes", email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UNA_SOLICITUD))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(creada).get("id").asLong();
    }

    @Test
    void solicitante_noPuedeEditarNiBorrarLaDeOtro_da403() throws Exception {
        long ajena = crearComo("otra.persona@duocuc.cl");

        mockMvc.perform(put("/solicitudes/" + ajena)
                .with(como("solicitantes", "alumno@duocuc.cl"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UNA_SOLICITUD))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/solicitudes/" + ajena)
                .with(como("solicitantes", "alumno@duocuc.cl")))
            .andExpect(status().isForbidden());
    }

    @Test
    void solicitante_noPuedeBorrarUnaResuelta_da409() throws Exception {
        long id = crearComo("alumno@duocuc.cl");
        mockMvc.perform(put("/solicitudes/" + id + "/decision")
                .with(como("aprobadores", "jefatura@duocuc.cl"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UNA_DECISION))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/solicitudes/" + id)
                .with(como("solicitantes", "alumno@duocuc.cl")))
            .andExpect(status().isConflict());
    }

    @Test
    void solicitante_soloVeLasSuyas() throws Exception {
        crearComo("otra.persona@duocuc.cl");
        crearComo("alumno@duocuc.cl");

        mockMvc.perform(get("/solicitudes").with(como("solicitantes", "alumno@duocuc.cl")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].solicitante",
                org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("alumno@duocuc.cl"))));
    }

    // El health check de ECS tiene que responder sin token: si exigiera
    // autenticacion, Fargate marcaria la task como enferma y la reiniciaria
    // en bucle.
    @Test
    void health_esPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }
}
