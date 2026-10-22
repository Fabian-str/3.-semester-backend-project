package app.api;

import app.dao.PlaythroughDAO;
import app.services.EmulatorConnectionService;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;

public class EmulatorConnectionController {

    private final PlaythroughDAO playthroughDAO;
    private final EmulatorConnectionService connectionService;

    public EmulatorConnectionController(PlaythroughDAO playthroughDAO, EmulatorConnectionService connectionService) {

        this.playthroughDAO = playthroughDAO;
        this.connectionService = connectionService;
    }

    public void createConnection(Context ctx) {

        Object userIdAttribute = ctx.attribute("userId");

        if (!(userIdAttribute instanceof Number number)) {
            throw new UnauthorizedResponse("Authentication required");
        }

        Long userId = number.longValue();

        Long playthroughId = ctx.pathParamAsClass("id", Long.class)
            .check(id -> id > 0, "Playthrough ID must be positive")
            .get();

        if (playthroughDAO.findByIdAndUserId(playthroughId, userId) == null) {
            throw new NotFoundResponse("Playthrough not found");
        }

        var connection = connectionService.createConnection(userId, playthroughId);

        ctx.status(201).json(connection);
    }
}