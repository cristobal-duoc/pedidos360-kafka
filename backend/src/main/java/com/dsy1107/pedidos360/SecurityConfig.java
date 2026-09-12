package com.dsy1107.pedidos360;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

// El backend es un RESOURCE SERVER: AUTENTICA cada peticion validando el JWT de
// Cognito (firma via JWKS, issuer, audience, vigencia) y ademas AUTORIZA por rol
// en los metodos de escritura (ver ProductoController).
//
// La autorizacion primaria sigue siendo la del API Gateway, que corta la peticion
// antes de que llegue aqui. Esta es una SEGUNDA compuerta, independiente: si
// alguien llamara al contenedor directo por su IP, saltandose el gateway, el
// backend rechaza igual. Defensa en profundidad.
@Configuration
@EnableMethodSecurity // habilita @PreAuthorize
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    @Value("${cognito.client-id}")
    private String clientId;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // API con tokens: no usa CSRF
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll() // health check de ECS
                .anyRequest().authenticated())                   // el resto exige token valido
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(convertidorDeRoles())));
        return http.build();
    }

    // Spring no sabe leer los grupos de Cognito: por defecto solo mira el claim
    // "scope". Este convertidor ademas traduce cada grupo de "cognito:groups" a un
    // rol ROLE_<grupo>, que es lo que evalua hasRole() en @PreAuthorize.
    private JwtAuthenticationConverter convertidorDeRoles() {
        JwtGrantedAuthoritiesConverter porScope = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(porScope.convert(jwt));
            List<String> grupos = jwt.getClaimAsStringList("cognito:groups");
            if (grupos != null) {
                grupos.forEach(g -> authorities.add(new SimpleGrantedAuthority("ROLE_" + g)));
            }
            return authorities;
        });
        return conv;
    }

    // El issuer-uri valida firma, issuer y expiracion. La audiencia la validamos
    // aparte: el access token de Cognito trae "client_id" en vez de "aud".
    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);

        OAuth2TokenValidator<Jwt> conIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> conAudiencia = jwt ->
            clientId.equals(jwt.getClaimAsString("client_id"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "client_id no coincide", null));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(conIssuer, conAudiencia));
        return decoder;
    }
}
