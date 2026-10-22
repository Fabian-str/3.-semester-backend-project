package app.dao;

import app.entities.Gender;
import app.entities.PokemonInstance;

import java.util.List;

public interface PokemonInstanceDAO {

    PokemonInstance create(PokemonInstance pokemon);

    PokemonInstance findById(Long id);

    PokemonInstance findByIdAndUserId(Long id, Long userId);

    List<PokemonInstance> findAll();

    List<PokemonInstance> findByPlaythroughId(Long playthroughId);

    List<PokemonInstance> findByPlaythroughIdAndUserId(Long playthroughId, Long userId);

    List<PokemonInstance> findByUserId(Long userId);

    PokemonInstance update(PokemonInstance pokemon);

    void delete(Long id);

    List<PokemonInstance> findShinyByPlaythroughId(Long playthroughId);

    long countBySpecies(Long playthroughId, int pokemonId);

    long countBySpeciesAndGender(Long playthroughId, int pokemonId, Gender gender);

    long countBySpeciesGenderAndShiny(Long playthroughId, int pokemonId, Gender gender, boolean shiny);
}