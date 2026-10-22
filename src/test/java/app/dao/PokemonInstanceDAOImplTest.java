package app.dao;

import app.config.HibernateConfig;
import app.entities.Gender;
import app.entities.Playthrough;
import app.entities.PokemonGame;
import app.entities.PokemonInstance;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PokemonInstanceDAOImplTest {

    private static EntityManagerFactory emf;

    private PlaythroughDAO playthroughDAO;
    private PokemonInstanceDAO pokemonDAO;
    private Playthrough playthrough;

    @BeforeAll
    static void setupAll() {

        emf = HibernateConfig.getEntityManagerFactory();
    }

    @BeforeEach
    void setup() {

        playthroughDAO = new PlaythroughDAOImpl(emf);
        pokemonDAO = new PokemonInstanceDAOImpl(emf);

        playthrough = Playthrough.builder()
            .name("Test Red Run")
            .game(PokemonGame.RED)
            .build();

        playthroughDAO.create(playthrough);
    }

    @AfterAll
    static void tearDownAll() {
        emf.close();
    }

    @Test
    void createAndFindById() {
        PokemonInstance pikachu = PokemonInstance.builder()
            .pokemonId(25)
            .gender(Gender.MALE)
            .shiny(false)
            .currentlyOwned(true)
            .playthrough(playthrough)
            .build();

        pokemonDAO.create(pikachu);

        PokemonInstance found = pokemonDAO.findById(pikachu.getId());

        assertNotNull(found);
        assertEquals(25, found.getPokemonId());
        assertEquals(Gender.MALE, found.getGender());
    }

    @Test
    void updatePokemon() {
        PokemonInstance pikachu = PokemonInstance.builder()
            .pokemonId(25)
            .gender(Gender.MALE)
            .shiny(false)
            .currentlyOwned(true)
            .playthrough(playthrough)
            .build();

        pokemonDAO.create(pikachu);

        pikachu.setNickname("Sparky");
        pokemonDAO.update(pikachu);

        PokemonInstance updated = pokemonDAO.findById(pikachu.getId());

        assertEquals("Sparky", updated.getNickname());
    }

    @Test
    void deletePokemon() {
        PokemonInstance pikachu = PokemonInstance.builder()
            .pokemonId(25)
            .gender(Gender.MALE)
            .shiny(false)
            .currentlyOwned(true)
            .playthrough(playthrough)
            .build();

        pokemonDAO.create(pikachu);

        pokemonDAO.delete(pikachu.getId());

        assertNull(pokemonDAO.findById(pikachu.getId()));
    }

    @Test
    void countBySpecies() {
        PokemonInstance male = PokemonInstance.builder()
            .pokemonId(25)
            .gender(Gender.MALE)
            .shiny(false)
            .currentlyOwned(true)
            .playthrough(playthrough)
            .build();

        PokemonInstance female = PokemonInstance.builder()
            .pokemonId(25)
            .gender(Gender.FEMALE)
            .shiny(false)
            .currentlyOwned(true)
            .playthrough(playthrough)
            .build();

        pokemonDAO.create(male);
        pokemonDAO.create(female);

        long count = pokemonDAO.countBySpecies(playthrough.getId(), 25);

        assertEquals(2, count);
    }

    @Test
    void countFemaleShinyPokemon() {
        PokemonInstance shinyFemale = PokemonInstance.builder()
            .pokemonId(25)
            .gender(Gender.FEMALE)
            .shiny(true)
            .currentlyOwned(true)
            .playthrough(playthrough)
            .build();

        PokemonInstance normalFemale = PokemonInstance.builder()
            .pokemonId(25)
            .gender(Gender.FEMALE)
            .shiny(false)
            .currentlyOwned(true)
            .playthrough(playthrough)
            .build();

        pokemonDAO.create(shinyFemale);
        pokemonDAO.create(normalFemale);

        long count = pokemonDAO.countBySpeciesGenderAndShiny(playthrough.getId(), 25, Gender.FEMALE, true);

        assertEquals(1, count);
    }
}