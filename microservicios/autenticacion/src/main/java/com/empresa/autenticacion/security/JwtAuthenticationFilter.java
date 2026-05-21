package com.empresa.autenticacion.security;

import com.empresa.autenticacion.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                Claims claims = jwtService.validateToken(token);
                UUID empleadoId = UUID.fromString(claims.getSubject());
                String username = claims.get("username", String.class);
                String rol = claims.get("rol", String.class);

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + rol)
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(empleadoId);

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Agregar headers con informacion del usuario para los controladores
                request.setAttribute("empleadoId", empleadoId);
                request.setAttribute("rol", rol);

                log.debug("Usuario autenticado por token: {} (empleadoId: {}, rol: {})", username, empleadoId, rol);

                // Envolver la request para agregar el header X-Empleado-Id
                // Los controladores usan @RequestHeader("X-Empleado-Id") para leerlo
                Map<String, String> extraHeaders = new HashMap<>();
                extraHeaders.put("X-Empleado-Id", empleadoId.toString());
                extraHeaders.put("X-Rol", rol);
                request = new HeaderAddingRequestWrapper(request, extraHeaders);

            } catch (Exception e) {
                log.warn("Error validando token JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else {
            // Si no hay token, intentar autenticar por header interno X-Empleado-Id (desde API Gateway)
            String empleadoIdHeader = request.getHeader("X-Empleado-Id");
            String rolHeader = request.getHeader("X-Rol");

            if (StringUtils.hasText(empleadoIdHeader) && StringUtils.hasText(rolHeader)) {
                try {
                    UUID empleadoId = UUID.fromString(empleadoIdHeader);

                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + rolHeader)
                    );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken("internal", null, authorities);
                    authentication.setDetails(empleadoId);

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    request.setAttribute("empleadoId", empleadoId);
                    request.setAttribute("rol", rolHeader);

                    log.debug("Usuario autenticado por header interno: empleadoId: {}, rol: {}", empleadoId, rolHeader);

                } catch (Exception e) {
                    log.warn("Error autenticando por header X-Empleado-Id: {}", e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Wrapper que permite agregar headers adicionales a la request.
     */
    private static class HeaderAddingRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> extraHeaders;

        public HeaderAddingRequestWrapper(HttpServletRequest request, Map<String, String> extraHeaders) {
            super(request);
            this.extraHeaders = extraHeaders;
        }

        @Override
        public String getHeader(String name) {
            String headerValue = extraHeaders.get(name);
            if (headerValue != null) {
                return headerValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new HashSet<>(extraHeaders.keySet());
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                names.add(original.nextElement());
            }
            return Collections.enumeration(names);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String headerValue = extraHeaders.get(name);
            if (headerValue != null) {
                return Collections.enumeration(List.of(headerValue));
            }
            return super.getHeaders(name);
        }
    }
}
