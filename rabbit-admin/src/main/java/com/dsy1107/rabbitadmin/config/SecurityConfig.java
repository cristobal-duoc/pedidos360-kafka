package com.dsy1107.rabbitadmin.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

// El mismo filtro JWT que el backend de solicitudes: valida firma, issuer, vigencia
// y client_id contra Cognito. Sin token valido, 401.
//
// Ademas, administrar RabbitMQ exige el rol "aprobadores" (el grupo de Cognito):
// un solicitante no puede crear ni borrar colas. Sin el rol, 403.
@Configuration
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    @Value("${cognito.client-id}")
    private String clientId;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // API con tokens: no usa CSRF
            .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("aprobadores"))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(convertidorDeRoles())));
        return http.build();
    }

    // Traduce cada grupo de "cognito:groups" a un rol ROLE_<grupo>.
    private JwtAuthenticationConverter convertidorDeRoles() {
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> grupos = jwt.getClaimAsStringList("cognito:groups");
            if (grupos != null) {
                grupos.forEach(g -> authorities.add(new SimpleGrantedAuthority("ROLE_" + g)));
            }
            return authorities;
        });
        return conv;
    }

    // Firma, issuer y vigencia con el issuer-uri; la audiencia aparte, porque el
    // access token de Cognito trae "client_id" en vez de "aud".
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
