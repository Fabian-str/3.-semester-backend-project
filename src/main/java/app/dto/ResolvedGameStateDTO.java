package app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedGameStateDTO {

    private int mapId;
    private String locationKey;
    private int partyCount;
    private List<Integer> pokemonIds;
    private String playerName;
    private String rivalName;
    private LocationDTO location;
    private List<Integer> seenPokemonIds;
    private List<Integer> caughtPokemonIds;
    private Integer badgeMask;
}