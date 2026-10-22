package app.api;

import app.adapters.GenOneAdapter;
import app.dao.PlaythroughDAO;
import app.dto.GameStateDTO;
import app.dto.ResolvedGameStateDTO;
import app.services.EmulatorConnectionService;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameStateController {

    private final GenOneAdapter genOneAdapter;
    private final EmulatorConnectionService connectionService;
    private final PlaythroughDAO playthroughDAO;

    public GameStateController(
        GenOneAdapter genOneAdapter,
        EmulatorConnectionService connectionService,
        PlaythroughDAO playthroughDAO
    ) {
        this.genOneAdapter = genOneAdapter;
        this.connectionService = connectionService;
        this.playthroughDAO = playthroughDAO;
    }

    public void updateGameState(Context ctx) {

        String connectionCode = ctx.header("X-Emulator-Code");

        var connection = connectionService.findByCode(connectionCode);

        if (connection == null) {

            throw new UnauthorizedResponse("Invalid or expired emulator connection");
        }

        if (playthroughDAO.findByIdAndUserId(connection.playthroughId(), connection.userId()) == null) {

            connectionService.disconnect(connectionCode);

            throw new UnauthorizedResponse("Emulator connection is no longer valid");
        }

        GameStateDTO gameState = ctx.bodyAsClass(GameStateDTO.class);

        List<Integer> pokemonIds = gameState.getSpeciesIds()
            .stream()
            .map(genOneAdapter::toPokedexId)
            .toList();

        ResolvedGameStateDTO resolvedState = new ResolvedGameStateDTO(
            gameState.getMapId(),
            genOneAdapter.toLocationKey(gameState.getMapId()),
            gameState.getPartyCount(),
            pokemonIds,
            gameState.getPlayerName(),
            gameState.getRivalName(),
            genOneAdapter.toLocation(gameState.getMapId()),
            gameState.getSeenPokemonIds(),
            gameState.getCaughtPokemonIds()
        );

        latestStates.put(connection.playthroughId(), new LiveGameState(Instant.now().toString(), resolvedState));

        // Return the resolved state while testing the connection.
        ctx.status(200).json(resolvedState);
    }

    private final Map<Long, LiveGameState> latestStates = new ConcurrentHashMap<>();

    public record LiveGameState(String receivedAt, ResolvedGameStateDTO state) {
    }

    public void getGameState(Context ctx) {

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

        LiveGameState latestState = latestStates.get(playthroughId);

        if (latestState == null) {
            ctx.status(204);
            return;
        }

        ctx.json(latestState);
    }
}