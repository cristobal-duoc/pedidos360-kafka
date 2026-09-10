package com.dsy1107.pedidos360;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

// El backend es un RESOURCE SERVER: AUTENTICA cada peticion validando el JWT
// de Cognito (firma via JWKS, issuer, audience, vigencia). NO autoriza por rol:
// de eso vive el API Gateway. Esto cubre el 40% de EP1 ("el BFF valida el token").
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
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll() // health check de ECS
                .anyRequest().authenticated())                   // el resto exige token valido
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
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
