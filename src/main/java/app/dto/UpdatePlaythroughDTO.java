package app.dto;

import app.entities.PokemonGame;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdatePlaythroughDTO {

    private String name;
    private PokemonGame game;
}