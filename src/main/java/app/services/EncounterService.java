package app.services;

import app.entities.PokemonGame;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EncounterService {

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final Map<String, List<Integer>> areaMappings;
    private final Map<Integer, CachedArea> cache = new ConcurrentHashMap<>();

    public EncounterService() {

        try (InputStream input = getClass().getResourceAsStream("/gen1/encounter-areas.json"))

        {
            if (input == null) {throw new IllegalStateException("encounter-areas.json is missing");

            }

            areaMappings = mapper.readValue(input, new TypeReference<Map<String, List<Integer>>>() {

            });

        } catch (IOException e) {

            throw new IllegalStateException("Could not load encounter mappings", e);
        }
    }

    public EncounterResult getEncounters(String locationKey, PokemonGame game) throws IOException, InterruptedException {

        List<Integer> areaIds = areaMappings.get(locationKey);

        if (areaIds == null) {
            return new EncounterResult(false, List.of());
        }

        String version = game.name().toLowerCase(Locale.ROOT);

        Map<Integer, EncounterPokemon> pokemon = new TreeMap<>();

        for (int areaId : areaIds) {

            JsonNode area = loadArea(areaId);

            for (JsonNode encounter : area.path("pokemon_encounters")) {
                JsonNode resource = encounter.path("pokemon");

                String url = resource.path("url").asText();
                String[] segments = url.split("/");

                int pokemonId = Integer.parseInt(segments[segments.length - 1]);

                if (pokemonId < 1 || pokemonId > 151) {
                    continue;
                }

                for (JsonNode details : encounter.path("version_details")) {

                    if (!version.equals(details.path("version").path("name").asText())) {

                        continue;
                    }

                    for (JsonNode option : details.path("encounter_details")) {

                        List<String> conditions = new ArrayList<>();

                        for (JsonNode condition : option.path("condition_values")) {

                            conditions.add(condition.path("name").asText());
                        }

                        EncounterOption result = new EncounterOption(
                            option.path("method").path("name").asText(), option.path("min_level").asInt(),
                            option.path("max_level").asInt(), List.copyOf(conditions));

                        EncounterPokemon entry = pokemon.computeIfAbsent(
                            pokemonId, id -> new EncounterPokemon(
                            id, resource.path("name").asText(), new ArrayList<>()));

                        if (!entry.options().contains(result)) {
                            entry.options().add(result);
                        }
                    }
                }
            }
        }

        List<EncounterPokemon> results = pokemon.values().stream()
            .map(entry -> new EncounterPokemon(
            entry.pokemonId(), entry.name(), List.copyOf(entry.options()))).toList();

        return new EncounterResult(true, results);
    }

    private JsonNode loadArea(int areaId)

            throws IOException, InterruptedException {

        CachedArea cached = cache.get(areaId);

        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {

            return cached.data();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://pokeapi.co/api/v2/location-area/" + areaId + "/"
                )).timeout(Duration.ofSeconds(10)).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {

            throw new IOException("PokéAPI returned status " + response.statusCode());
        }

        JsonNode data = mapper.readTree(response.body());

        cache.put(areaId, new CachedArea(data, Instant.now().plusSeconds(3600)));

        return data;
    }

    public record EncounterResult(boolean mapped, List<EncounterPokemon> pokemon) {

    }

    public record EncounterPokemon(int pokemonId, String name, List<EncounterOption> options) {

    }

    public record EncounterOption(String method, int minLevel, int maxLevel, List<String> conditions) {

    }

    private record CachedArea(JsonNode data, Instant expiresAt) {

    }
}