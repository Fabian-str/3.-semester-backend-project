package app.dao;

import app.entities.Playthrough;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.List;

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
}