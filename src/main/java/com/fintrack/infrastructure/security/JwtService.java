package com.fintrack.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JwtService — Gerencia criação e validação de tokens JWT.
 *
 * ANATOMIA DE UM JWT:
 * Um JWT tem 3 partes separadas por ponto: header.payload.signature
 *
 * Header: algoritmo usado (HS256)
 * Payload (Claims):
 *   - sub: subject (username)
 *   - iat: issued at (quando foi criado)
 *   - exp: expiration (quando expira)
 *   - claims customizados (roles, userId, etc.)
 * Signature: HMAC-SHA256(base64(header) + "." + base64(payload), secret)
 *
 * WHY HMAC-SHA256 (symmetric) and not RSA (asymmetric)?
 * HMAC: mesma chave assina e verifica. Simples, rápido, adequado para monolito/API única.
 * RSA: chave privada assina, chave pública verifica. Necessário quando múltiplos serviços
 *      precisam verificar tokens sem ter acesso à chave privada (OAuth2/OIDC).
 * Para este projeto, HMAC é a escolha correta e mais didática.
 */
@Service
public class JwtService {

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
            .claims(extraClaims)
            .subject(userDetails.getUsername())
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSigningKey())
            .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
