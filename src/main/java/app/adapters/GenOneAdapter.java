package app.adapters;

import app.dto.LocationDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;

public class GenOneAdapter {

    private final Map<Integer, Integer> speciesMap;
    private final Map<Integer, LocationDTO> mapMap;

    public GenOneAdapter() {

        ObjectMapper objectMapper = new ObjectMapper();

        try (
                InputStream speciesStream = getClass().getResourceAsStream("/gen1/species-map.json");

                InputStream mapStream = getClass().getResourceAsStream("/gen1/map-map.json")
        ) {
            if (speciesStream == null || mapStream == null) {
                throw new IllegalStateException("Gen 1 mapping files are missing");
            }

            speciesMap = objectMapper.readValue(speciesStream, new TypeReference<Map<Integer, Integer>>() {}
            );

            mapMap = objectMapper.readValue(mapStream, new TypeReference<Map<Integer, LocationDTO>>() {});

        } catch (Exception exception) {
            throw new RuntimeException("Could not load Gen 1 mappings", exception);
        }
    }

    public int toPokedexId(int internalSpeciesId) {

        Integer pokedexId = speciesMap.get(internalSpeciesId);

        if (pokedexId == null) {
            throw new IllegalArgumentException("Unknown Gen 1 species ID: " + internalSpeciesId);
        }

        return pokedexId;
    }

    public LocationDTO toLocation(int mapId) {
        return mapMap.get(mapId);
    }

    public String toLocationKey(int mapId) {

        LocationDTO location = toLocation(mapId);

        return location != null ? location.getKey() : "unknown";
    }
}