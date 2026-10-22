package app.services;

import app.dto.PokemonDTO;
import app.dto.pokeapi.NamedResourceDTO;
import app.dto.pokeapi.PokeApiPokemonDTO;
import app.dto.pokeapi.SpritesDTO;
import app.dto.pokeapi.TypeSlotDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PokemonApiServiceTest {

    private final PokemonApiService service =
            new PokemonApiService();

    @Test
    void shouldMapPokeApiPokemonToPokemonDTO() {

        NamedResourceDTO electricType = new NamedResourceDTO();

        electricType.setName("electric");

        TypeSlotDTO typeSlot = new TypeSlotDTO();

        typeSlot.setType(electricType);

        SpritesDTO sprites = new SpritesDTO();

        sprites.setFrontDefault("pikachu.png");

        PokeApiPokemonDTO apiPokemon = new PokeApiPokemonDTO();

        apiPokemon.setId(25);
        apiPokemon.setName("pikachu");
        apiPokemon.setSprites(sprites);
        apiPokemon.setTypes(List.of(typeSlot));

        PokemonDTO result = service.mapToPokemonDTO(apiPokemon);

        assertEquals(25, result.getId());
        assertEquals("pikachu", result.getName());
        assertEquals("pikachu.png", result.getSprite());
        assertEquals(List.of("electric"), result.getTypes());
    }

    @Test
    void shouldMapMultiplePokemonTypes() {

        NamedResourceDTO fire = new NamedResourceDTO();

        fire.setName("fire");

        NamedResourceDTO flying = new NamedResourceDTO();

        flying.setName("flying");

        TypeSlotDTO fireSlot = new TypeSlotDTO();

        fireSlot.setType(fire);

        TypeSlotDTO flyingSlot = new TypeSlotDTO();

        flyingSlot.setType(flying);

        SpritesDTO sprites = new SpritesDTO();

        sprites.setFrontDefault("charizard.png");

        PokeApiPokemonDTO apiPokemon = new PokeApiPokemonDTO();

        apiPokemon.setId(6);
        apiPokemon.setName("charizard");
        apiPokemon.setSprites(sprites);
        apiPokemon.setTypes(List.of(fireSlot, flyingSlot));

        PokemonDTO result = service.mapToPokemonDTO(apiPokemon);

        assertEquals(6, result.getId());
        assertEquals("charizard", result.getName());
        assertEquals("charizard.png", result.getSprite());

        assertEquals(List.of("fire", "flying"), result.getTypes());
    }
}