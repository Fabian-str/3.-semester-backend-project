package app.dao;

import app.entities.Gender;
import app.entities.PokemonInstance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import app.entities.Playthrough;

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
    public long countBySpeciesGenderAndShiny(Long playthroughId, int pokemonId, Gender gender, boolean shiny) {
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
    public List<PokemonInstance> findByPlaythroughIdAndUserId(Long playthroughId, Long userId) {
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

    @Override
    public void importParty(
            Long playthroughId,
            Long userId,
            List<PokemonInstance> party
    ) {
        try (EntityManager em = emf.createEntityManager()) {
            var transaction = em.getTransaction();

            try {
                transaction.begin();

                Playthrough playthrough = em.find(
                        Playthrough.class,
                        playthroughId,
                        jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
                );

                if (playthrough == null ||
                        !java.util.Objects.equals(
                                playthrough.getUser().getId(), userId)) {
                    throw new IllegalArgumentException("Playthrough not found");
                }

                importParty(em, playthrough, party);

                transaction.commit();

            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }

                throw e;
            }
        }
    }

    static void importParty(
            EntityManager em,
            Playthrough playthrough,
            List<PokemonInstance> party
    ) {
        if (party == null) {
            throw new IllegalArgumentException("Pokémon collection is missing");
        }

        var identities = new java.util.HashSet<String>();

        for (PokemonInstance incoming : party) {
            if (incoming == null ||
                    incoming.getEmulatorIdentity() == null ||
                    !identities.add(incoming.getEmulatorIdentity())) {
                throw new IllegalArgumentException(
                        "Missing or duplicate emulator identity in collection."
                );
            }
        }

        List<PokemonInstance> existing = em.createQuery(
                        """
                        SELECT p
                        FROM PokemonInstance p
                        WHERE p.playthrough.id = :playthroughId
                        AND p.emulatorIdentity IS NOT NULL
                        """,
                        PokemonInstance.class
                )
                .setParameter("playthroughId", playthrough.getId())
                .getResultList();

        var byIdentity = new java.util.HashMap<String, PokemonInstance>();

        for (PokemonInstance pokemon : existing) {
            if (byIdentity.putIfAbsent(
                    pokemon.getEmulatorIdentity(), pokemon) != null) {
                throw new IllegalArgumentException(
                        "Duplicate emulator identities exist in the saved collection."
                );
            }
        }

        for (PokemonInstance saved : existing) {
            saved.setCurrentlyOwned(false);
        }

        for (PokemonInstance incoming : party) {
            PokemonInstance saved = byIdentity.get(
                    incoming.getEmulatorIdentity()
            );

            if (saved == null) {
                incoming.setPlaythrough(playthrough);
                incoming.setCurrentlyOwned(true);
                em.persist(incoming);

                byIdentity.put(incoming.getEmulatorIdentity(), incoming);

            } else {
                saved.setPokemonId(incoming.getPokemonId());
                saved.setNickname(incoming.getNickname());

                if (incoming.getLevel() != null) {
                    saved.setLevel(incoming.getLevel());
                }

                saved.setOriginalTrainerId(incoming.getOriginalTrainerId());
                saved.setDvs(incoming.getDvs());
                saved.setCurrentlyOwned(true);
            }
        }
    }
}