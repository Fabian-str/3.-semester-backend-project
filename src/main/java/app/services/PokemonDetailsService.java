package app.services;

import app.dto.PokemonDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PokemonDetailsService {

    private static final String API_URL = "https://pokeapi.co/api/v2/";

    private final PokemonApiService pokemonApiService;

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final Map<String, CachedJson> cache =
            new ConcurrentHashMap<>();

    public PokemonDetailsService(PokemonApiService pokemonApiService) {

        this.pokemonApiService = pokemonApiService;
    }

    public PokemonDetails getDetails(int pokemonId)

            throws IOException, InterruptedException {

        PokemonDTO pokemon = pokemonApiService.getPokemon(pokemonId);

        JsonNode species = getJson("pokemon-species/" + pokemonId + "/");

        JsonNode encounters = getJson("pokemon/" + pokemonId + "/encounters");

        String evolutionUrl = species.path("evolution_chain").path("url").asText();

        String prefix = API_URL + "evolution-chain/";

        if (!evolutionUrl.startsWith(prefix)) {

            throw new IOException("Unexpected evolution-chain URL");
        }

        String chainId = evolutionUrl.substring(prefix.length()).replaceAll("/$", "");

        if (!chainId.matches("\\d+")) {

            throw new IOException("Invalid evolution-chain ID");
        }

        JsonNode evolutionChain = getJson("evolution-chain/" + chainId + "/");

        return new PokemonDetails(pokemon, encounters, evolutionChain.path("chain"));
    }

    private JsonNode getJson(String path) throws IOException, InterruptedException {

        CachedJson cached = cache.get(path);

        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {

            return cached.value();
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL + path))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {

            throw new IllegalArgumentException("Pokémon details not found");
        }

        if (response.statusCode() != 200) {

            throw new IOException("PokéAPI returned status " + response.statusCode());
        }

        JsonNode result = mapper.readTree(response.body());

        cache.put(path, new CachedJson(result, Instant.now().plusSeconds(3600)));

        return result;
    }

    public record PokemonDetails(PokemonDTO pokemon, JsonNode encounters, JsonNode evolutionChain) {

    }

    private record CachedJson(JsonNode value, Instant expiresAt) {

    }
}