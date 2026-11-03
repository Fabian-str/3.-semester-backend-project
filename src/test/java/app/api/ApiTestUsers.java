package app.api;

import app.config.HibernateConfig;
import app.entities.Role;
import app.entities.User;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

public final class ApiTestUsers {

    public static final String PASSWORD = "test123";
    public static final String USERNAME = "user_" + UUID.randomUUID();
    public static final String ADMIN_USERNAME = "admin_" + UUID.randomUUID();

    private static boolean initialized;

    private ApiTestUsers() {
    }

    public static synchronized void setup() {
        if (initialized) {
            return;
        }

        var emf = HibernateConfig.createTestEntityManagerFactory();

        try {
            try (var em = emf.createEntityManager()) {
                var transaction = em.getTransaction();

                try {
                    transaction.begin();

                    em.persist(User.builder()
                            .username(USERNAME)
                            .passwordHash(BCrypt.hashpw(
                                    PASSWORD, BCrypt.gensalt()
                            ))
                            .role(Role.USER)
                            .build());

                    em.persist(User.builder()
                            .username(ADMIN_USERNAME)
                            .passwordHash(BCrypt.hashpw(
                                    PASSWORD, BCrypt.gensalt()
                            ))
                            .role(Role.ADMIN)
                            .build());

                    transaction.commit();

                } catch (RuntimeException e) {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }

                    throw e;
                }
            }

            initialized = true;

        } finally {
            emf.close();
        }
    }
}