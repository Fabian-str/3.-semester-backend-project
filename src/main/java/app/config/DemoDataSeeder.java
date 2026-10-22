package app.config;

import app.dao.UserDAO;
import app.entities.Role;
import app.entities.User;
import app.services.PasswordService;

public class DemoDataSeeder {

    private final UserDAO userDAO;
    private final PasswordService passwordService;

    public DemoDataSeeder(
            UserDAO userDAO,
            PasswordService passwordService
    ) {
        this.userDAO = userDAO;
        this.passwordService = passwordService;
    }

    public void seed() {

        String enabled = System.getenv("SEED_DEMO_DATA");

        if (!"true".equalsIgnoreCase(enabled)) {
            return;
        }

        createUserIfMissing("testuser", "password123", Role.USER);

        createUserIfMissing("testadmin", "admin123", Role.ADMIN);
    }

    private void createUserIfMissing(String username, String password, Role role) {

        if (userDAO.findByUsername(username).isPresent()) {
            return;
        }

        User user = User.builder()
                .username(username)
                .passwordHash(passwordService.hashPassword(password))
                .role(role)
                .build();

        userDAO.create(user);
    }
}