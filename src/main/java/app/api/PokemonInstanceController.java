package app.api;

import app.dao.PlaythroughDAO;
import app.dao.PokemonInstanceDAO;
import app.dto.CreatePokemonInstanceDTO;
import app.dto.PokemonInstanceDTO;
import app.dto.UpdatePokemonInstanceDTO;
import app.entities.Playthrough;
import app.entities.PokemonInstance;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class PokemonInstanceController {

    private final PokemonInstanceDAO pokemonInstanceDAO;
    private final PlaythroughDAO playthroughDAO;

    public PokemonInstanceController(PokemonInstanceDAO pokemonInstanceDAO, PlaythroughDAO playthroughDAO) {
        this.pokemonInstanceDAO = pokemonInstanceDAO;
        this.playthroughDAO = playthroughDAO;
    }

    public void getAllForUser(Context ctx) {

        Long userId = getUserId(ctx);

        List<PokemonInstanceDTO> pokemon = pokemonInstanceDAO
            .findByUserId(userId)
            .stream()
            .map(this::toDTO)
            .toList();

        ctx.json(pokemon);
    }

    public void getById(Context ctx) {

        Long id = parseId(ctx, "id");

        if (id == null) {
            return;
        }

        Long userId = getUserId(ctx);

        PokemonInstance pokemon = pokemonInstanceDAO.findByIdAndUserId(id, userId);

        if (pokemon == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Pokemon not found"));
            return;
        }

        ctx.json(toDTO(pokemon));
    }

    public void getByPlaythrough(Context ctx) {

        Long playthroughId = parseId(ctx, "id");

        if (playthroughId == null) {
            return;
        }

        Long userId = getUserId(ctx);

        Playthrough playthrough = playthroughDAO.findByIdAndUserId(playthroughId, userId);

        if (playthrough == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Playthrough not found"));
            return;
        }

        List<PokemonInstanceDTO> pokemon = pokemonInstanceDAO
            .findByPlaythroughIdAndUserId(playthroughId, userId)
            .stream()
            .map(this::toDTO)
            .toList();

        ctx.json(pokemon);
    }

    public void create(Context ctx) {

        Long playthroughId = parseId(ctx, "id");

        if (playthroughId == null) {
            return;
        }

        Long userId = getUserId(ctx);

        Playthrough playthrough = playthroughDAO.findByIdAndUserId(playthroughId, userId);

        if (playthrough == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Playthrough not found"));
            return;
        }

        CreatePokemonInstanceDTO request = ctx.bodyAsClass(CreatePokemonInstanceDTO.class);

        if (request.getPokemonId() <= 0) {
            ctx.status(400);
            ctx.json(Map.of("error", "Pokemon ID must be greater than 0"));
            return;
        }

        PokemonInstance pokemon = PokemonInstance.builder()
            .pokemonId(request.getPokemonId())
            .nickname(request.getNickname())
            .gender(request.getGender())
            .shiny(request.isShiny())
            .currentlyOwned(request.isCurrentlyOwned())
            .playthrough(playthrough)
            .build();

        PokemonInstance created = pokemonInstanceDAO.create(pokemon);

        ctx.status(201);
        ctx.json(toDTO(created));
    }

    public void update(Context ctx) {

        Long id = parseId(ctx, "id");

        if (id == null) {
            return;
        }

        Long userId = getUserId(ctx);

        PokemonInstance existing = pokemonInstanceDAO.findByIdAndUserId(id, userId);

        if (existing == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Pokemon not found"));
            return;
        }

        UpdatePokemonInstanceDTO request = ctx.bodyAsClass(UpdatePokemonInstanceDTO.class);

        existing.setNickname(request.getNickname());
        existing.setGender(request.getGender());
        existing.setShiny(request.isShiny());
        existing.setCurrentlyOwned(request.isCurrentlyOwned());

        PokemonInstance updated = pokemonInstanceDAO.update(existing);

        ctx.json(toDTO(updated));
    }

    public void delete(Context ctx) {

        Long id = parseId(ctx, "id");

        if (id == null) {
            return;
        }

        Long userId = getUserId(ctx);

        PokemonInstance existing = pokemonInstanceDAO.findByIdAndUserId(id, userId);

        if (existing == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Pokemon not found"));
            return;
        }

        pokemonInstanceDAO.delete(id);

        ctx.status(204);
    }

    private PokemonInstanceDTO toDTO(PokemonInstance pokemon) {

        return PokemonInstanceDTO.builder()
            .id(pokemon.getId())
            .pokemonId(pokemon.getPokemonId())
            .nickname(pokemon.getNickname())
            .gender(pokemon.getGender())
            .shiny(pokemon.isShiny())
            .currentlyOwned(pokemon.isCurrentlyOwned())
            .obtainedAt(pokemon.getObtainedAt())
            .playthroughId(pokemon.getPlaythrough().getId())
            .build();
    }

    private Long getUserId(Context ctx) {

        Object userId = ctx.attribute("userId");

        if (userId instanceof Number number) {
            return number.longValue();
        }

        throw new IllegalStateException("Authenticated user ID is missing");
    }

    private Long parseId(Context ctx, String parameter) {

        try {
            return Long.parseLong(ctx.pathParam(parameter));

        } catch (NumberFormatException e) {
            ctx.status(400);
            ctx.json(Map.of("error", "ID must be a number"));
            return null;
        }
    }
}