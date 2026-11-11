package com.example.jira.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // tạo token
    public String generateToken(String username) {
        return generateToken(username,0);
    }

    /** Login/refresh truyền auth_version vừa đọc dưới khóa user để token cũ bị từ chối sau đổi mật khẩu/disable. */
    public String generateToken(String username,long authVersion) {
        return Jwts.builder()
                .setSubject(username)
                .claim("ver",authVersion)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    /** Token legacy thiếu ver là 0; claim sai kiểu/âm không được dùng để xác thực. */
    public long extractVersion(String token) {
        Object version = extractClaims(token).get("ver");
        if (version == null) return 0;
        if (!(version instanceof Number number) || number.longValue() < 0) throw new IllegalArgumentException("Invalid token version");
        return number.longValue();
    }

    // lấy username
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    // validate
    public boolean validateToken(String token, String username) {
        return username.equals(extractUsername(token))
                && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token)
                .getExpiration()
                .before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
