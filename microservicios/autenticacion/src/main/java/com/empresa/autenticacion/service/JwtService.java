package com.empresa.autenticacion.service;

import com.empresa.autenticacion.exception.TokenInvalidoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID empleadoId, String username, String rol) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(empleadoId.toString())
                .claim("username", username)
                .claim("rol", rol)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token expirado: {}", e.getMessage());
            throw new TokenInvalidoException("El token ha expirado");
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Token con firma invalida: {}", e.getMessage());
            throw new TokenInvalidoException("Token invalido: firma incorrecta");
        } catch (UnsupportedJwtException e) {
            log.warn("Token no soportado: {}", e.getMessage());
            throw new TokenInvalidoException("Token no soportado");
        } catch (IllegalArgumentException e) {
            log.warn("Token vacio o nulo: {}", e.getMessage());
            throw new TokenInvalidoException("Token vacio o invalido");
        }
    }

    public UUID getEmpleadoIdFromToken(String token) {
        Claims claims = validateToken(token);
        return UUID.fromString(claims.getSubject());
    }

    public String getRolFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("rol", String.class);
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
