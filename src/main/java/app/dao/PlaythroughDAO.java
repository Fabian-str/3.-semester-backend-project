package app.dao;

import app.entities.Playthrough;
import app.entities.PokemonGame;
import app.entities.PokemonInstance;

import java.util.List;

public interface PlaythroughDAO {

    Playthrough create(Playthrough playthrough);

    Playthrough findById(Long id);

    Playthrough findByIdAndUserId(Long id, Long userId);

    List<Playthrough> findAll();

    List<Playthrough> findByUserId(Long userId);

    Playthrough update(Playthrough playthrough);

    void delete(Long id);

    void verifyOrBindEmulator(Long playthroughId, Long userId, PokemonGame gameVersion,
        int trainerId, String playerName, String stateJson, String receivedAt, List<PokemonInstance> party);

    void importParty(Long playthroughId, Long userId, List<PokemonInstance> party);
}