package app.api;

import app.dto.PokemonDTO;
import app.services.PokemonApiService;
import io.javalin.http.Context;
import java.util.Map;

public class PokemonController {

    private final PokemonApiService pokemonApiService;

    public PokemonController(PokemonApiService pokemonApiService) {
        this.pokemonApiService = pokemonApiService;
    }

    public void getPokemon(Context ctx) throws Exception {

        String idParam = ctx.pathParam("id");

        int id;

        try {id = Integer.parseInt(idParam);}

        catch (NumberFormatException e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Pokemon ID must be a number"));
            return;
        }

        if (id <= 0) {
            ctx.status(400);
            ctx.json(Map.of("error", "Pokemon ID must be greater than 0"));
            return;
        }

        try {
            PokemonDTO pokemon = pokemonApiService.getPokemon(id);
            ctx.json(pokemon);

        } catch (IllegalArgumentException e) {
            ctx.status(404);
            ctx.json(Map.of("error", e.getMessage()));
        }
    }
}