package com.woozoo.quiz.global.security;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityConfig(JwtProvider jwtProvider,
                          @Qualifier("handlerExceptionResolver")
                          HandlerExceptionResolver handlerExceptionResolver) {
        this.jwtProvider = jwtProvider;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 토큰을 쿠키가 아니라 Authorization 헤더로 보내므로 CSRF 공격 대상이 아니다
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // /error 를 막아 두면 실제 오류가 401 로 가려진다
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
                        .anyRequest().authenticated())
                // 인증 실패는 필터 체인에서 나서 @RestControllerAdvice 가 잡지 못한다.
                // HandlerExceptionResolver 로 넘겨 다른 오류와 같은 ProblemDetail 모양으로 응답한다
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint((request,
                                                     response,
                                                     authException) ->
                                handlerExceptionResolver.resolveException(request, response, null,
                                        new BusinessException(ErrorCode.UNAUTHORIZED))))
                // 빈으로 등록하면 Spring Boot 가 일반 서블릿 필터로도 등록해 두 번 실행된다. 그래서 체인에만 직접 넣는다
                .addFilterBefore(new JwtFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

