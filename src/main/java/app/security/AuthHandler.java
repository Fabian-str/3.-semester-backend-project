package app.security;

import app.services.JwtService;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.jsonwebtoken.Claims;

public class AuthHandler {

    private final JwtService jwtService;

    public AuthHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public void handle(Context ctx) {

        String authHeader = ctx.header("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedResponse("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.validateToken(token);

            ctx.attribute("userId", claims.get("userId"));
            ctx.attribute("username", claims.getSubject());
            ctx.attribute("role", claims.get("role"));

        } catch (Exception e) {
            throw new UnauthorizedResponse("Invalid or expired token");
        }
    }
}