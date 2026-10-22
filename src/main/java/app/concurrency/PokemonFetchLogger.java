package app.concurrency;

public class PokemonFetchLogger implements Runnable {

    private final int pokemonId;

    public PokemonFetchLogger(int pokemonId) {

        this.pokemonId = pokemonId;
    }

    @Override
    public void run() {

        System.out.println("Preparing fetch for Pokémon ID: " + pokemonId);
    }
}