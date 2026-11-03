package app.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PartyPokemonDTO {

    private Integer speciesId;
    private String nickname;
    private Integer level;
    private Integer originalTrainerId;
    private Integer dvs;
}