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
public class GameStateDTO {

    private int mapId;
    private int partyCount;
    private List<Integer> speciesIds;
    private String playerName;
    private String rivalName;
    private List<Integer> seenPokemonIds;
    private List<Integer> caughtPokemonIds;
}