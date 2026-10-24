package com.dsy1107.pedidos360;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.dsy1107.pedidos360.mensajeria.PublicadorEventos;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

// Prueba básica: que el contexto de Spring arranca. Usa H2 en memoria y
// reemplaza el JwtDecoder por un mock para no depender de la red ni de AWS.
@SpringBootTest
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.com",
    "cognito.client-id=test",
    "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class Pedidos360ApplicationTests {

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    PublicadorEventos eventos;

    @Test
    void contextLoads() {
    }
}
