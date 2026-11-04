package codeit.ootd.global.security;

import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.ErrorResponse;
import codeit.ootd.global.exception.OtbooException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider,
            AuthSessionService authSessionService,
            UserRepository userRepository,
            JsonMapper jsonMapper
    ) throws Exception {

        http.csrf(csrf -> csrf
                .csrfTokenRepository(
                        CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
                .csrfTokenRequestHandler(
                        new SpaCsrfTokenRequestHandler()
                )
        );

        // Spring Security 기본 로그인 방식 비활성화
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());

        // 기존 API 접근 권한 설정
        http.sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/",
                        "/index.html",
                        "/favicon.ico",
                        "/assets/**",
                        "/css/**",
                        "/js/**",
                        "/images/**"
                ).permitAll()

                .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/sign-out").permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/auth/csrf",
                        "/api/auth/csrf-token"
                ).permitAll()
                .anyRequest().authenticated()
        );

        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                        writeSecurityError(
                                response,
                                ErrorCode.AUTHENTICATION_REQUIRED,
                                jsonMapper
                        )
                )
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeSecurityError(
                                response,
                                ErrorCode.ACCESS_DENIED,
                                jsonMapper
                        )
                )
        );

        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(
                        jwtProvider,
                        authSessionService,
                        userRepository,
                        jsonMapper
                );

        http.addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }


    private void writeSecurityError(
            HttpServletResponse response,
            ErrorCode errorCode,
            JsonMapper jsonMapper
    ) throws IOException {

        ErrorResponse errorResponse = ErrorResponse.from(
                new OtbooException(errorCode),
                errorCode
        );

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        jsonMapper.writeValue(
                response.getWriter(),
                errorResponse
        );
    }

}