package app.dao;

import app.entities.Playthrough;
import app.entities.PokemonGame;
import app.entities.PokemonInstance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Objects;

public class PlaythroughDAOImpl implements PlaythroughDAO {

    private final EntityManagerFactory emf;

    public PlaythroughDAOImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public Playthrough create(Playthrough playthrough) {

        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.persist(playthrough);
            em.getTransaction().commit();
            return playthrough;
        }
    }

    @Override
    public Playthrough findById(Long id) {

        try (EntityManager em = emf.createEntityManager()) {
            return em.find(Playthrough.class, id);
        }
    }

    @Override
    public List<Playthrough> findAll() {

        try (EntityManager em = emf.createEntityManager()) {
            return em
                .createQuery("SELECT p FROM Playthrough p ORDER BY p.createdAt DESC", Playthrough.class)
                .getResultList();
        }
    }

    @Override
    public Playthrough update(Playthrough playthrough) {

        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            Playthrough updated = em.merge(playthrough);
            em.getTransaction().commit();
            return updated;
        }
    }

    @Override
    public void delete(Long id) {

        try (EntityManager em = emf.createEntityManager()) {
            var transaction = em.getTransaction();

            try {
                transaction.begin();

                Playthrough playthrough = em.find(Playthrough.class, id);

                if (playthrough != null) {
                    em.createQuery("""
                        DELETE FROM PokemonInstance p
                        WHERE p.playthrough.id = :playthroughId
                        """)
                            .setParameter("playthroughId", id)
                            .executeUpdate();

                    em.remove(playthrough);
                }

                transaction.commit();

            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }

                throw exception;
            }
        }
    }

    @Override
    public Playthrough findByIdAndUserId(Long id, Long userId) {

        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                    """
                    SELECT p
                    FROM Playthrough p
                    WHERE p.id = :id
                    AND p.user.id = :userId
                    """,
                    Playthrough.class
                )
                .setParameter("id", id)
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        }
    }

    @Override
    public List<Playthrough> findByUserId(Long userId) {

        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                    """
                    SELECT p
                    FROM Playthrough p
                    WHERE p.user.id = :userId
                    ORDER BY p.createdAt DESC
                    """,
                    Playthrough.class
                )
                .setParameter("userId", userId)
                .getResultList();
        }
    }

    @Override
    public void verifyOrBindEmulator(Long playthroughId, Long userId, PokemonGame gameVersion,
        int trainerId, String playerName, String stateJson, String receivedAt, List<PokemonInstance> party) {

        try (EntityManager em = emf.createEntityManager()) {
            var transaction = em.getTransaction();

            try {
                transaction.begin();

                Playthrough playthrough = em.find(
                        Playthrough.class,
                        playthroughId,
                        LockModeType.PESSIMISTIC_WRITE
                );

                if (playthrough == null ||
                        !Objects.equals(playthrough.getUser().getId(), userId)) {
                    throw new IllegalStateException(
                            "Playthrough is no longer available."
                    );
                }

                if (playthrough.getGame() != gameVersion) {
                    throw new IllegalStateException(
                            "Wrong game version. This playthrough uses " +
                                    playthrough.getGame() +
                                    ", but the emulator uses " + gameVersion + "."
                    );
                }

                boolean unbound =
                        playthrough.getEmulatorGame() == null &&
                                playthrough.getEmulatorTrainerId() == null &&
                                playthrough.getEmulatorPlayerName() == null;

                if (unbound) {
                    playthrough.setEmulatorGame(gameVersion);
                    playthrough.setEmulatorTrainerId(trainerId);
                    playthrough.setEmulatorPlayerName(playerName);

                } else {
                    boolean sameSave =
                            playthrough.getEmulatorGame() == gameVersion &&
                                    Objects.equals(
                                            playthrough.getEmulatorTrainerId(), trainerId
                                    ) &&
                                    Objects.equals(
                                            playthrough.getEmulatorPlayerName(), playerName
                                    );

                    if (!sameSave) {
                        throw new IllegalStateException(
                                "This playthrough is linked to another save. " +
                                        "Load the original save or create another playthrough."
                        );
                    }
                }

                playthrough.setLastEmulatorState(stateJson);
                playthrough.setLastEmulatorReceivedAt(receivedAt);

                PokemonInstanceDAOImpl.importParty(em, playthrough, party);


                transaction.commit();

            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }

                throw e;
            }
        }
    }

    @Override
    public void importParty(Long playthroughId, Long userId, List<PokemonInstance> party) {

        try (EntityManager em = emf.createEntityManager()) {

            var transaction = em.getTransaction();
            transaction.begin();

            try {
                app.entities.Playthrough playthrough = em.find(app.entities.Playthrough.class,
                        playthroughId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

                if (playthrough == null || !playthrough.getUser().getId().equals(userId)) {

                    throw new IllegalArgumentException("Playthrough not found");
                }

                var incomingIdentities = new java.util.HashSet<String>();

                for (PokemonInstance incoming : party) {

                    if (incoming.getEmulatorIdentity() == null || !incomingIdentities.add(incoming.getEmulatorIdentity())) {

                        throw new IllegalArgumentException("Two party Pokémon have the same trainer ID " + "and DVs. Automatic import cannot distinguish them.");
                    }
                }

                List<PokemonInstance> existing = em.createQuery(
                    """
                    SELECT p
                    FROM PokemonInstance p
                    WHERE p.playthrough.id = :playthroughId
                    AND p.emulatorIdentity IS NOT NULL
                    """,
                    PokemonInstance.class)
                    .setParameter("playthroughId", playthroughId)
                    .getResultList();

                var byIdentity = new java.util.HashMap<String, PokemonInstance>();

                for (PokemonInstance pokemon : existing) {

                    if (byIdentity.putIfAbsent(pokemon.getEmulatorIdentity(), pokemon) != null) {

                        throw new IllegalArgumentException("Duplicate emulator identities exist " + "in the saved collection.");
                    }
                }

                for (PokemonInstance incoming : party) {

                    PokemonInstance saved = byIdentity.get(incoming.getEmulatorIdentity());

                    if (saved == null) {

                        incoming.setPlaythrough(playthrough);
                        incoming.setCurrentlyOwned(true);

                        em.persist(incoming);

                        byIdentity.put(incoming.getEmulatorIdentity(), incoming);

                    } else {
                        saved.setPokemonId(incoming.getPokemonId());

                        saved.setNickname(incoming.getNickname());

                        saved.setLevel(incoming.getLevel());

                        saved.setOriginalTrainerId(incoming.getOriginalTrainerId());

                        saved.setDvs(incoming.getDvs());

                        saved.setCurrentlyOwned(true);
                    }
                }

                transaction.commit();

            } catch (RuntimeException e) {

                if (transaction.isActive()) {
                    transaction.rollback();
                }

                throw e;
            }
        }
    }
}