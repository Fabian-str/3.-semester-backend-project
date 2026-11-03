package app.services;

import app.adapters.GenOneAdapter;
import app.dao.PokemonInstanceDAO;
import app.dto.GameStateDTO;
import app.dto.PartyPokemonDTO;
import app.entities.PokemonInstance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PartyImportService {

    private final PokemonInstanceDAO pokemonInstanceDAO;
    private final GenOneAdapter genOneAdapter;

    public PartyImportService(
            PokemonInstanceDAO pokemonInstanceDAO,
            GenOneAdapter genOneAdapter
    ) {
        this.pokemonInstanceDAO = pokemonInstanceDAO;
        this.genOneAdapter = genOneAdapter;
    }

    public List<PokemonInstance> prepare(GameStateDTO state) {
        List<PartyPokemonDTO> party = state.getParty();
        List<PartyPokemonDTO> pc = state.getPcPokemon();
        List<PartyPokemonDTO> dayCare = state.getDayCarePokemon();

        if (dayCare == null || dayCare.size() > 1) {

            throw new IllegalArgumentException(
                "Day Care details are missing or invalid. " + "Reload the updated tracker.");
        }

        if (party == null ||
                party.size() != state.getPartyCount()) {

            throw new IllegalArgumentException("Party details are missing. Reload the updated tracker.");
        }

        if (pc == null || pc.size() > 240) {
            throw new IllegalArgumentException("PC details are missing or invalid. " + "Reload the updated tracker.");
        }

        List<PokemonInstance> result = new ArrayList<>();
        Set<String> identities = new HashSet<>();

        for (int index = 0; index < party.size(); index++) {
            PartyPokemonDTO pokemon = party.get(index);

            if (pokemon == null || pokemon.getSpeciesId() == null ||
                !pokemon.getSpeciesId().equals(state.getSpeciesIds().get(index))) {

                throw new IllegalArgumentException("Party details do not match the species list.");
            }

            addPokemon(pokemon, true, result, identities);
        }

        for (PartyPokemonDTO pokemon : pc) {
            addPokemon(pokemon, false, result, identities);
        }

        for (PartyPokemonDTO pokemon : dayCare) {
            addPokemon(pokemon, false, result, identities);
        }

        return result;
    }

    private void addPokemon(PartyPokemonDTO pokemon, boolean inParty,
        List<PokemonInstance> result, Set<String> identities) {

        if (pokemon == null || pokemon.getSpeciesId() == null) {

            throw new IllegalArgumentException("Missing Pokémon species.");
        }

        if (inParty && (pokemon.getLevel() == null ||
            pokemon.getLevel() < 1 || pokemon.getLevel() > 100)) {

            throw new IllegalArgumentException("Invalid party level.");
        }

        if (!isUnsignedShort(pokemon.getOriginalTrainerId()) || !isUnsignedShort(pokemon.getDvs())) {

            throw new IllegalArgumentException("Invalid Pokémon trainer ID or DVs.");
        }

        String nickname = pokemon.getNickname();

        if (nickname == null || nickname.isBlank() || nickname.length() > 10) {

            throw new IllegalArgumentException("Invalid Pokémon nickname.");
        }

        String identity = pokemon.getOriginalTrainerId() + ":" + pokemon.getDvs();

        if (!identities.add(identity)) {
            throw new IllegalArgumentException("Two Pokémon have identical trainer IDs and DVs. " +
                "Automatic import cannot distinguish them.");
        }

        int pokemonId = genOneAdapter.toPokedexId(pokemon.getSpeciesId());

        result.add(PokemonInstance.builder()
            .pokemonId(pokemonId)
            .nickname(nickname)
            .level(inParty ? pokemon.getLevel() : null)
            .originalTrainerId(pokemon.getOriginalTrainerId())
            .dvs(pokemon.getDvs())
            .emulatorIdentity(identity)
            .currentlyOwned(true)
            .build());
    }

    public void importParty(Long playthroughId, Long userId, List<PokemonInstance> pokemon) {
        pokemonInstanceDAO.importParty(playthroughId, userId, pokemon);
    }

    private boolean isUnsignedShort(Integer value) {
        return value != null && value >= 0 && value <= 65535;
    }
}