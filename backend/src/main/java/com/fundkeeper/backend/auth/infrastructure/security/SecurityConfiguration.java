package com.fundkeeper.backend.auth.infrastructure.security;

import java.io.IOException;
import java.time.Clock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.web.SecurityFilterChain;

import com.fundkeeper.backend.auth.application.AuthProperties;
import com.fundkeeper.backend.auth.application.CryptoSupport;
import com.fundkeeper.backend.auth.domain.AuthSessionRepository;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.shared.api.ApiResponse;
import com.fundkeeper.backend.shared.exception.ErrorCode;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(
                                        response,
                                        objectMapper,
                                        HttpStatus.UNAUTHORIZED,
                                        ErrorCode.AUTHENTICATION_REQUIRED,
                                        "请先登录或重新登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(
                                        response,
                                        objectMapper,
                                        HttpStatus.FORBIDDEN,
                                        ErrorCode.ACCESS_DENIED,
                                        "无权执行此操作")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/email-codes",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password-reset",
                                "/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(
                                        response,
                                        objectMapper,
                                        HttpStatus.UNAUTHORIZED,
                                        ErrorCode.AUTHENTICATION_REQUIRED,
                                        "请先登录或重新登录"))
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }

    private void writeSecurityError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            ErrorCode errorCode,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.error(errorCode.name(), message));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecretKey(
            AuthProperties properties,
            CryptoSupport cryptoSupport) {
        return new SecretKeySpec(
                cryptoSupport.decodeSecret(
                        properties.jwtSecretBase64(),
                        "JWT"),
                "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey).build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            AuthProperties properties,
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new AudienceValidator(properties.audience()),
                new SessionJwtValidator(userRepository, sessionRepository, clock)));
        return decoder;
    }
}
