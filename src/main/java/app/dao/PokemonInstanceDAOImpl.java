package app.dao;

import app.entities.Gender;
import app.entities.PokemonInstance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.List;

public class PokemonInstanceDAOImpl implements PokemonInstanceDAO {

    private final EntityManagerFactory emf;

    public PokemonInstanceDAOImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public PokemonInstance create(PokemonInstance pokemon) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.persist(pokemon);
            em.getTransaction().commit();

            return pokemon;
        }
    }

    @Override
    public PokemonInstance findById(Long id) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.find(PokemonInstance.class, id);
        }
    }

    @Override
    public List<PokemonInstance> findAll() {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                    "SELECT p FROM PokemonInstance p ORDER BY p.obtainedAt DESC",
                    PokemonInstance.class
            ).getResultList();
        }
    }

    @Override
    public PokemonInstance update(PokemonInstance pokemon) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();

            PokemonInstance updated = em.merge(pokemon);

            em.getTransaction().commit();

            return updated;
        }
    }

    @Override
    public void delete(Long id) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();

            PokemonInstance pokemon =
                    em.find(PokemonInstance.class, id);

            if (pokemon != null) {
                em.remove(pokemon);
            }

            em.getTransaction().commit();
        }
    }

    @Override
    public List<PokemonInstance> findByPlaythroughId(Long playthroughId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            """
                            SELECT p
                            FROM PokemonInstance p
                            WHERE p.playthrough.id = :playthroughId
                            ORDER BY p.obtainedAt DESC
                            """,
                            PokemonInstance.class
                    )
                    .setParameter("playthroughId", playthroughId)
                    .getResultList();
        }
    }

    @Override
    public List<PokemonInstance> findShinyByPlaythroughId(Long playthroughId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            """
                            SELECT p
                            FROM PokemonInstance p
                            WHERE p.playthrough.id = :playthroughId
                            AND p.shiny = true
                            ORDER BY p.obtainedAt DESC
                            """,
                            PokemonInstance.class
                    )
                    .setParameter("playthroughId", playthroughId)
                    .getResultList();
        }
    }

    @Override
    public long countBySpecies(Long playthroughId, int pokemonId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            """
                            SELECT COUNT(p)
                            FROM PokemonInstance p
                            WHERE p.playthrough.id = :playthroughId
                            AND p.pokemonId = :pokemonId
                            """,
                            Long.class
                    )
                    .setParameter("playthroughId", playthroughId)
                    .setParameter("pokemonId", pokemonId)
                    .getSingleResult();
        }
    }

    @Override
    public long countBySpeciesAndGender(
            Long playthroughId,
            int pokemonId,
            Gender gender
    ) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            """
                            SELECT COUNT(p)
                            FROM PokemonInstance p
                            WHERE p.playthrough.id = :playthroughId
                            AND p.pokemonId = :pokemonId
                            AND p.gender = :gender
                            """,
                            Long.class
                    )
                    .setParameter("playthroughId", playthroughId)
                    .setParameter("pokemonId", pokemonId)
                    .setParameter("gender", gender)
                    .getSingleResult();
        }
    }

    @Override
    public long countBySpeciesGenderAndShiny(
            Long playthroughId,
            int pokemonId,
            Gender gender,
            boolean shiny
    ) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            """
                            SELECT COUNT(p)
                            FROM PokemonInstance p
                            WHERE p.playthrough.id = :playthroughId
                            AND p.pokemonId = :pokemonId
                            AND p.gender = :gender
                            AND p.shiny = :shiny
                            """,
                            Long.class
                    )
                    .setParameter("playthroughId", playthroughId)
                    .setParameter("pokemonId", pokemonId)
                    .setParameter("gender", gender)
                    .setParameter("shiny", shiny)
                    .getSingleResult();
        }
    }

    @Override
    public PokemonInstance findByIdAndUserId(Long id, Long userId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            """
                            SELECT p
                            FROM PokemonInstance p
                            WHERE p.id = :id
                            AND p.playthrough.user.id = :userId
                            """,
                            PokemonInstance.class
                    )
                    .setParameter("id", id)
                    .setParameter("userId", userId)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        }
    }

    @Override
    public List<PokemonInstance> findByPlaythroughIdAndUserId(
            Long playthroughId,
            Long userId
    ) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            """
                            SELECT p
                            FROM PokemonInstance p
                            WHERE p.playthrough.id = :playthroughId
                            AND p.playthrough.user.id = :userId
                            ORDER BY p.obtainedAt DESC
                            """,
                            PokemonInstance.class
                    )
                    .setParameter("playthroughId", playthroughId)
                    .setParameter("userId", userId)
                    .getResultList();
        }
    }

    @Override
    public List<PokemonInstance> findByUserId(Long userId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            """
                            SELECT p
                            FROM PokemonInstance p
                            WHERE p.playthrough.user.id = :userId
                            ORDER BY p.pokemonId ASC
                            """,
                            PokemonInstance.class
                    )
                    .setParameter("userId", userId)
                    .getResultList();
        }
    }
}