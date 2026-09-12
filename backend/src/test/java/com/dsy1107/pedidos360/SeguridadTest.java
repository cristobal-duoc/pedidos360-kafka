package com.dsy1107.pedidos360;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

// Verifica las dos reglas que definen la seguridad del backend:
//   AUTENTICAR -> sin token valido, 401.
//   AUTORIZAR  -> con token pero sin el rol editores, 403 al escribir.
//
// No llama a Cognito: el JwtDecoder esta mockeado y el post-procesador jwt()
// construye la autenticacion con las autoridades que se le indiquen, que es lo
// que en produccion produce el convertidor de SecurityConfig a partir del claim
// cognito:groups.
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

    private static final String UN_PRODUCTO = "{\"nombre\":\"Teclado\",\"precio\":25000}";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority rol(String nombre) {
        return new SimpleGrantedAuthority("ROLE_" + nombre);
    }

    @Test
    void sinToken_leer_da401() throws Exception {
        mockMvc.perform(get("/productos"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sinToken_escribir_da401() throws Exception {
        mockMvc.perform(post("/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(UN_PRODUCTO))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void lector_puedeLeer() throws Exception {
        mockMvc.perform(get("/productos").with(jwt().authorities(rol("lectores"))))
            .andExpect(status().isOk());
    }

    @Test
    void lector_noPuedeEscribir_da403() throws Exception {
        mockMvc.perform(post("/productos")
                .with(jwt().authorities(rol("lectores")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UN_PRODUCTO))
            .andExpect(status().isForbidden());
    }

    @Test
    void editor_puedeEscribir() throws Exception {
        mockMvc.perform(post("/productos")
                .with(jwt().authorities(rol("editores")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UN_PRODUCTO))
            .andExpect(status().isOk());
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
