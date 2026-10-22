package app.config;

import app.adapters.GenOneAdapter;
import app.api.*;
import app.dao.*;
import app.security.AuthHandler;
import app.security.RoleHandler;
import app.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;

public class AppDependencies {

    public final PlaythroughController playthroughController;
    public final PokemonController pokemonController;
    public final AuthController authController;
    public final GameStateController gameStateController;
    public final PokemonInstanceController pokemonInstanceController;
    public final EmulatorConnectionController emulatorConnectionController;

    public final AuthHandler authHandler;
    public final RoleHandler roleHandler;

    public final GameStateWebSocketService gameStateWebSocketService;

    public AppDependencies() {

        EntityManagerFactory emf = HibernateConfig.getEntityManagerFactory();

        PlaythroughDAO playthroughDAO = new PlaythroughDAOImpl(emf);

        PokemonInstanceDAO pokemonInstanceDAO = new PokemonInstanceDAOImpl(emf);

        UserDAO userDAO = new UserDAOImpl(emf);

        PokemonApiService pokemonApiService = new PokemonApiService();

        PasswordService passwordService = new PasswordService();

        DemoDataSeeder demoDataSeeder = new DemoDataSeeder(userDAO, passwordService);

        demoDataSeeder.seed();

        AuthService authService = new AuthService(userDAO, passwordService);

        JwtService jwtService = new JwtService();

        GenOneAdapter genOneAdapter = new GenOneAdapter();

        gameStateWebSocketService = new GameStateWebSocketService(new ObjectMapper());

        EmulatorConnectionService emulatorConnectionService = new EmulatorConnectionService();

        emulatorConnectionController = new EmulatorConnectionController(playthroughDAO, emulatorConnectionService);

        playthroughController = new PlaythroughController(playthroughDAO, userDAO);

        pokemonController = new PokemonController(pokemonApiService);

        pokemonInstanceController = new PokemonInstanceController(pokemonInstanceDAO, playthroughDAO);

        authController = new AuthController(authService, jwtService);

        gameStateController = new GameStateController(genOneAdapter, emulatorConnectionService, playthroughDAO);

        authHandler = new AuthHandler(jwtService);

        roleHandler = new RoleHandler();
    }
}