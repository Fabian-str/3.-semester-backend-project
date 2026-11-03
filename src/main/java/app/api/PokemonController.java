package app.api;

import app.dto.PokemonDTO;
import app.services.PokemonApiService;
import io.javalin.http.Context;
import java.util.Map;
import app.services.PokemonDetailsService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.HttpResponseException;
import io.javalin.http.NotFoundResponse;

import java.io.IOException;

public class PokemonController {

    private final PokemonApiService pokemonApiService;
    private final PokemonDetailsService pokemonDetailsService;

    public PokemonController(PokemonApiService pokemonApiService) {
        this.pokemonApiService = pokemonApiService;
        this.pokemonDetailsService = new PokemonDetailsService(pokemonApiService);
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

    public void getPokemonDetails(Context ctx) {
        int id;

        try {
            id = Integer.parseInt(ctx.pathParam("id"));

        } catch (NumberFormatException e) {

            throw new BadRequestResponse("Pokémon ID must be a number");
        }

        if (id < 1 || id > 151) {

            throw new BadRequestResponse("Detailed Red/Blue information currently supports IDs 1–151");
        }

        try {
            ctx.json(pokemonDetailsService.getDetails(id));

        } catch (IllegalArgumentException e) {
            throw new NotFoundResponse(e.getMessage());

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new HttpResponseException(503, "Pokémon lookup interrupted");

        } catch (IOException e) {

            throw new HttpResponseException(502, "Could not load Pokémon details from PokéAPI");
        }
    }
}