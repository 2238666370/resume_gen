package com.resumegen.security;

import com.resumegen.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtil(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String role, Long ver) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + props.getExpireSeconds() * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("ver", ver)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public LoginUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        Long ver = claims.get("ver", Long.class);
        return new LoginUser(userId, role, ver == null ? 0L : ver);
    }

    public long getExpireSeconds() {
        return props.getExpireSeconds();
    }
}