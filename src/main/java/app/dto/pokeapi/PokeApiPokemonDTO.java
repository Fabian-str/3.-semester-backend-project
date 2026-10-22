package app.dto.pokeapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)

public class PokeApiPokemonDTO {

    private int id;
    private String name;
    private SpritesDTO sprites;
    private List<TypeSlotDTO> types;
}