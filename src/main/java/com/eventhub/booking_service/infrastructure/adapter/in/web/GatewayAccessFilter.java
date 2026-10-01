package com.eventhub.booking_service.infrastructure.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

@Component
public class GatewayAccessFilter extends OncePerRequestFilter {

    @Value("${gateway.internal-secret}")
    private String expectedSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Permitir Swagger UI local directo en el microservicio para desarrollo
        if (path.contains("/swagger-ui") || path.contains("/v3/api-docs") || path.contains("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String secretHeader = request.getHeader("X-Gateway-Secret");

        // Validacion segura contra NullPointerException
        if (expectedSecret == null || !Objects.equals(secretHeader, expectedSecret)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("Acceso denegado: Las peticiones deben pasar obligatoriamente por el API Gateway");
            return;
        }

        filterChain.doFilter(request, response);
    }
}