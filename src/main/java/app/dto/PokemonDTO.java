package app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PokemonDTO {

    private int id;
    private String name;
    private String sprite;
    private String femaleSprite;
    private String shinySprite;
    private String shinyFemaleSprite;
    private List<String> types;
}