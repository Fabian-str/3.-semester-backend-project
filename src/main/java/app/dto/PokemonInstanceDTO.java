package app.dto;

import app.entities.Gender;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PokemonInstanceDTO {

    private Long id;
    private int pokemonId;
    private String nickname;
    private Gender gender;
    private boolean shiny;
    private boolean currentlyOwned;
    private LocalDateTime obtainedAt;
    private Long playthroughId;
}