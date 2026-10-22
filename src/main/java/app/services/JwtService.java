package app.services;

import app.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

public class JwtService {

    private static final long EXPIRATION_TIME = 1000 * 60 * 60;

    private final SecretKey key;

    public JwtService() {

        try {
            Properties properties = new Properties();

            InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream("config.properties");

            if (input != null) {
                properties.load(input);
            }

            String secret = System.getenv("JWT_SECRET");

            if (secret == null || secret.isBlank()) {
                secret = properties.getProperty("JWT_SECRET");
            }

            if (secret == null || secret.isBlank()) {
                throw new RuntimeException("JWT_SECRET is missing");
            }

            key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

        } catch (Exception e) {
            throw new RuntimeException("Could not load JWT secret", e);
        }
    }

    public String generateToken(User user) {

        Date now = new Date();

        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);

        return Jwts.builder()
            .subject(user.getUsername())
            .claim("userId", user.getId())
            .claim("role", user.getRole().name())
            .issuedAt(now)
            .expiration(expiration)
            .signWith(key)
            .compact();
    }

    public Claims validateToken(String token) {

        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}