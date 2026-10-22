package app.services;

import app.dto.PokemonDTO;
import app.dto.pokeapi.PokeApiPokemonDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class PokemonApiService {

    private static final String BASE_URL = "https://pokeapi.co/api/v2/pokemon/";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PokemonApiService() {

        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public PokemonDTO getPokemon(int pokemonId) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + pokemonId))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("Pokemon not found");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "PokéAPI returned status " + response.statusCode()
            );
        }

        PokeApiPokemonDTO apiPokemon = objectMapper.readValue(response.body(), PokeApiPokemonDTO.class);

        return mapToPokemonDTO(apiPokemon);
    }

    public PokemonDTO mapToPokemonDTO(PokeApiPokemonDTO apiPokemon) {

        List<String> types = apiPokemon.getTypes()
            .stream()
            .map(typeSlot -> typeSlot.getType().getName())
            .toList();

        return PokemonDTO.builder()
            .id(apiPokemon.getId())
            .name(apiPokemon.getName())
            .sprite(apiPokemon.getSprites().getFrontDefault())
            .femaleSprite(apiPokemon.getSprites().getFrontFemale())
            .shinySprite(apiPokemon.getSprites().getFrontShiny())
            .shinyFemaleSprite(apiPokemon.getSprites().getFrontShinyFemale())
            .types(types)
            .build();
    }

    public List<PokemonDTO> getPokemonBatch(List<Integer> pokemonIds) throws InterruptedException {

        return new ArrayList<>();
    }
}