package app.api;

import app.dao.PlaythroughDAO;
import app.dao.UserDAO;
import app.dto.CreatePlaythroughDTO;
import app.dto.UpdatePlaythroughDTO;
import app.entities.Playthrough;
import app.entities.User;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class PlaythroughController {

    private final PlaythroughDAO playthroughDAO;
    private final UserDAO userDAO;

    public PlaythroughController(
            PlaythroughDAO playthroughDAO,
            UserDAO userDAO
    ) {
        this.playthroughDAO = playthroughDAO;
        this.userDAO = userDAO;
    }

    public void getAll(Context ctx) {

        Long userId = getUserId(ctx);

        List<Playthrough> playthroughs =
                playthroughDAO.findByUserId(userId);

        ctx.json(playthroughs);
    }

    public void getById(Context ctx) {

        long id;

        try {
            id = Long.parseLong(ctx.pathParam("id"));

        } catch (NumberFormatException e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Playthrough ID must be a number"));
            return;
        }

        Long userId = getUserId(ctx);

        Playthrough playthrough = playthroughDAO.findByIdAndUserId(id, userId);

        if (playthrough == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Playthrough not found"));
            return;
        }

        ctx.json(playthrough);
    }

    public void create(Context ctx) {

        CreatePlaythroughDTO request =
                ctx.bodyAsClass(CreatePlaythroughDTO.class);

        if (request.getName() == null ||
                request.getName().isBlank()) {

            ctx.status(400);
            ctx.json(Map.of("error", "Playthrough name is required"));
            return;
        }

        if (request.getGame() == null) {
            ctx.status(400);
            ctx.json(Map.of("error", "Game is required"));
            return;
        }

        Long userId = getUserId(ctx);

        User user = userDAO.findById(userId)
                .orElse(null);

        if (user == null) {
            ctx.status(401);
            ctx.json(Map.of("error", "User not found"));
            return;
        }

        Playthrough playthrough = Playthrough.builder()
            .name(request.getName())
            .game(request.getGame())
            .user(user)
            .build();

        Playthrough created = playthroughDAO.create(playthrough);

        ctx.status(201);
        ctx.json(created);
    }

    public void update(Context ctx) {

        long id;

        try {
            id = Long.parseLong(ctx.pathParam("id"));

        } catch (NumberFormatException e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Playthrough ID must be a number"));
            return;
        }

        Long userId = getUserId(ctx);

        Playthrough existing = playthroughDAO.findByIdAndUserId(id, userId);

        if (existing == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Playthrough not found"));
            return;
        }

        UpdatePlaythroughDTO request = ctx.bodyAsClass(UpdatePlaythroughDTO.class);

        if (request.getName() == null || request.getName().isBlank()) {

            ctx.status(400);
            ctx.json(Map.of("error", "Playthrough name is required"));
            return;
        }

        if (request.getGame() == null) {
            ctx.status(400);
            ctx.json(Map.of("error", "Game is required"));
            return;
        }

        existing.setName(request.getName());
        existing.setGame(request.getGame());

        Playthrough updated = playthroughDAO.update(existing);

        ctx.json(updated);
    }

    public void delete(Context ctx) {

        long id;

        try {
            id = Long.parseLong(ctx.pathParam("id"));

        } catch (NumberFormatException e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Playthrough ID must be a number"));
            return;
        }

        Long userId = getUserId(ctx);

        Playthrough existing = playthroughDAO.findByIdAndUserId(id, userId);

        if (existing == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Playthrough not found"));
            return;
        }

        playthroughDAO.delete(id);

        ctx.status(204);
    }

    private Long getUserId(Context ctx) {

        Object userId = ctx.attribute("userId");

        if (userId instanceof Number number) {
            return number.longValue();
        }

        throw new IllegalStateException("Authenticated user ID is missing");
    }
}