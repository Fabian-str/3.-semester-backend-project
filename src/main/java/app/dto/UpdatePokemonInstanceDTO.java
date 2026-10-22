package app.dto;

import app.entities.Gender;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePokemonInstanceDTO {

    private String nickname;
    private Gender gender;
    private boolean shiny;
    private boolean currentlyOwned;
}