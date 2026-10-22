package app.api;

import app.dto.LoginDTO;
import app.dto.LoginResponseDTO;
import app.dto.RegisterUserDTO;
import app.dto.UserResponseDTO;
import app.entities.User;
import app.services.AuthService;
import app.services.JwtService;
import io.javalin.http.Context;

import java.util.Map;

public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    public void register(Context ctx) {

        RegisterUserDTO request = ctx.bodyAsClass(RegisterUserDTO.class);

        if (request.getUsername() == null || request.getUsername().isBlank()) {

            ctx.status(400);
            ctx.json(Map.of("error", "Username is required"));
            return;
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {

            ctx.status(400);
            ctx.json(Map.of("error", "Password is required"));

            return;
        }

        try {
            User user = authService.register(request.getUsername(), request.getPassword());

            UserResponseDTO response = new UserResponseDTO(user.getId(), user.getUsername(), user.getRole());

            ctx.status(201);
            ctx.json(response);

        } catch (IllegalArgumentException e) {
            ctx.status(409);
            ctx.json(Map.of("error", e.getMessage()));
        }
    }

    public void login(Context ctx) {

        LoginDTO request = ctx.bodyAsClass(LoginDTO.class);

        if (request.getUsername() == null ||
            request.getUsername().isBlank() ||
            request.getPassword() == null ||
            request.getPassword().isBlank()) {

            ctx.status(400);
            ctx.json(Map.of("error", "Username and password are required"));
            return;
        }

        try {
            User user = authService.login(
                request.getUsername(),
                request.getPassword());

            String token = jwtService.generateToken(user);

            LoginResponseDTO response = new LoginResponseDTO(
                token,
                user.getUsername(),
                user.getRole());

            ctx.status(200);
            ctx.json(response);

        } catch (IllegalArgumentException e) {
            ctx.status(401);
            ctx.json(Map.of("error", "Invalid username or password"));
        }
    }
}