package app.services;

import app.dao.UserDAO;
import app.entities.Role;
import app.entities.User;

public class AuthService {

    private final UserDAO userDAO;
    private final PasswordService passwordService;

    public AuthService(UserDAO userDAO, PasswordService passwordService) {
        this.userDAO = userDAO;
        this.passwordService = passwordService;
    }

    public User register(String username, String password) {

        if (userDAO.findByUsername(username).isPresent()) {

            throw new IllegalArgumentException("Username already exists");
        }

        String passwordHash = passwordService.hashPassword(password);

        User user = User.builder()
            .username(username)
            .passwordHash(passwordHash)
            .role(Role.USER)
            .build();

        return userDAO.create(user);
    }

    public User login(String username, String password) {

        User user = userDAO.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        boolean validPassword = passwordService.verifyPassword(password, user.getPasswordHash());

        if (!validPassword) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return user;
    }
}