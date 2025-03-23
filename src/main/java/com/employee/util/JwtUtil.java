package com.employee.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {


    private static final String SECRET = "YourSuperSecretKeyForJWTYourSuperSecretKeyForJWT"; // Should be at least 32 bytes
    private final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(Base64.getEncoder().encode(SECRET.getBytes(StandardCharsets.UTF_8)));

    public String generateToken(Map<String, Object> claims, String username) {
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusMillis(1000 * 60 * 60))) // 1-hour expiration
                .signWith(SECRET_KEY)
                .compact();
    }

    public String isTokenValid(String jwtToken) {
        try {
            return parseToken(jwtToken).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public Claims parseToken(String jwtToken) {
        try {
            return Jwts.parser()
                    .verifyWith(SECRET_KEY) // Corrected parsing method
                    .build()
                    .parseSignedClaims(jwtToken)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims(); // Return claims even if expired
        } catch (JwtException e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }


    public Object getClaims(String jwtToken , String field) {
        Map<String, Object> claimsMap = new HashMap<>();
        Claims claims = parseToken(jwtToken);
        claimsMap.putAll(claims);
        return claimsMap.getOrDefault(field,null);
    }

    public SecretKey getSigningKey() {
        return SECRET_KEY; // Provide the signing key
    }
}
