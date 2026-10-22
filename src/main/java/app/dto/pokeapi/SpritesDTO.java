package app.dto.pokeapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpritesDTO {

    @JsonProperty("front_default")
    private String frontDefault;

    @JsonProperty("front_female")
    private String frontFemale;

    @JsonProperty("front_shiny")
    private String frontShiny;

    @JsonProperty("front_shiny_female")
    private String frontShinyFemale;
}