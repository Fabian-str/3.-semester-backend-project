package app.api;

import app.adapters.GenOneAdapter;
import app.dao.PlaythroughDAO;
import app.dto.GameStateDTO;
import app.dto.ResolvedGameStateDTO;
import app.entities.PokemonGame;
import app.services.EmulatorConnectionService;
import io.javalin.http.*;
import java.time.Instant;
import java.util.List;
import app.entities.Playthrough;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.services.EncounterService;
import java.io.IOException;
import app.entities.PokemonInstance;
import app.services.PartyImportService;

public class GameStateController {

    private final GenOneAdapter genOneAdapter;
    private final EmulatorConnectionService connectionService;
    private final PlaythroughDAO playthroughDAO;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EncounterService encounterService = new EncounterService();
    private final PartyImportService partyImportService;

    public GameStateController(GenOneAdapter genOneAdapter, EmulatorConnectionService connectionService,
        PlaythroughDAO playthroughDAO, PartyImportService partyImportService) {

        this.genOneAdapter = genOneAdapter;
        this.connectionService = connectionService;
        this.playthroughDAO = playthroughDAO;
        this.partyImportService = partyImportService;
    }

    public void updateGameState(Context ctx) {
        String connectionCode = ctx.header("X-Emulator-Code");
        var connection = connectionService.findByCode(connectionCode);

        if (connection == null) {
            throw new UnauthorizedResponse(
                    "Invalid or expired emulator connection"
            );
        }

        if (playthroughDAO.findByIdAndUserId(
                connection.playthroughId(), connection.userId()) == null) {
            connectionService.disconnect(connectionCode);

            throw new UnauthorizedResponse(
                    "Emulator connection is no longer valid"
            );
        }

        GameStateDTO gameState = ctx.bodyAsClass(GameStateDTO.class);

        PokemonGame gameVersion;

        try {
            gameVersion = PokemonGame.valueOf(
                    gameState.getGameVersion() == null
                            ? ""
                            : gameState.getGameVersion()
            );

        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(
                    "Missing or unsupported game version. Reload the updated tracker."
            );
        }

        if (gameVersion != PokemonGame.RED &&
                gameVersion != PokemonGame.BLUE) {
            throw new BadRequestResponse(
                    "The tracker currently supports English Pokémon Red and Blue."
            );
        }

        Integer trainerId = gameState.getTrainerId();
        String playerName = gameState.getPlayerName();

        if (trainerId == null || trainerId < 0 || trainerId > 65535) {
            throw new BadRequestResponse("Missing or invalid trainer ID.");
        }

        if (playerName == null ||
                playerName.isBlank() ||
                playerName.length() > 7 ||
                playerName.contains("?")) {
            throw new BadRequestResponse(
                    "Invalid player name. Load your save before connecting."
            );
        }

        if (gameState.getSpeciesIds() == null ||
                gameState.getPartyCount() < 1 ||
                gameState.getPartyCount() > 6 ||
                gameState.getSpeciesIds().size() != gameState.getPartyCount()) {
            throw new BadRequestResponse(
                    "Load your save with a valid party before connecting."
            );
        }

        List<PokemonInstance> party;

        try {
            party = partyImportService.prepare(gameState);

        } catch (IllegalArgumentException e) {
            System.err.println(
                    "Emulator update rejected: " + e.getMessage()
            );

            throw new BadRequestResponse(e.getMessage());
        }

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
                gameState.getCaughtPokemonIds(),
                gameState.getBadgeMask()
        );

        String receivedAt = Instant.now().toString();
        String stateJson;

        try {
            stateJson = objectMapper.writeValueAsString(resolvedState);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException("Could not serialize emulator state", e);
        }

        // Save binding, snapshot and Pokémon collection in one transaction.
        try {
            playthroughDAO.verifyOrBindEmulator(connection.playthroughId(),
                    connection.userId(), gameVersion, trainerId, playerName,
                    stateJson, receivedAt, party);

        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());

        } catch (IllegalStateException e) {
            connectionService.disconnect(connectionCode);
            throw new ConflictResponse(e.getMessage());
        }

        ctx.status(200).json(resolvedState);
    }

    public record LiveGameState(String receivedAt, ResolvedGameStateDTO state) {

    }

    public void getGameState(Context ctx) {

        Object userIdAttribute = ctx.attribute("userId");

        if (!(userIdAttribute instanceof Number number)) {
            throw new UnauthorizedResponse("Authentication required");
        }

        Long userId = number.longValue();

        Long playthroughId = ctx.pathParamAsClass("id", Long.class)
            .check(id -> id > 0, "Playthrough ID must be positive").get();

        Playthrough playthrough = playthroughDAO.findByIdAndUserId(playthroughId, userId);

        if (playthrough == null) {
            throw new NotFoundResponse("Playthrough not found");
        }

        String stateJson = playthrough.getLastEmulatorState();

        if (stateJson == null || playthrough.getLastEmulatorReceivedAt() == null) {

            ctx.status(204);
            return;
        }

        try {
            ResolvedGameStateDTO state = objectMapper.readValue(stateJson, ResolvedGameStateDTO.class);

            ctx.json(new LiveGameState(playthrough.getLastEmulatorReceivedAt(), state));

        } catch (JsonProcessingException e) {

            throw new IllegalStateException("Could not read saved emulator state", e);
        }
    }

    public void getEncounters(Context ctx) {

        Object userIdAttribute = ctx.attribute("userId");

        if (!(userIdAttribute instanceof Number number)) {
            throw new UnauthorizedResponse("Authentication required");
        }

        Long playthroughId = ctx.pathParamAsClass("id", Long.class)
            .check(id -> id > 0, "Invalid playthrough ID").get();

        Playthrough playthrough = playthroughDAO.findByIdAndUserId(playthroughId, number.longValue());

        if (playthrough == null) {
            throw new NotFoundResponse("Playthrough not found");
        }

        if (playthrough.getGame() != PokemonGame.RED && playthrough.getGame() != PokemonGame.BLUE) {

            throw new BadRequestResponse("Encounter lookup currently supports Red and Blue.");
        }

        int mapId = ctx.queryParamAsClass("mapId", Integer.class)
            .check(id -> id >= 0 && id <= 255, "Invalid map ID").get();

        String locationKey = genOneAdapter.toLocationKey(mapId);

        try {

            ctx.json(encounterService.getEncounters(locationKey, playthrough.getGame()));

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new HttpResponseException(503, "Encounter lookup interrupted");

        } catch (IOException e) {

            throw new HttpResponseException(502, "Could not load encounter data from PokéAPI");
        }
    }
}