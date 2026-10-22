package app.dao;

import app.entities.User;

import java.util.Optional;

public interface UserDAO {

    User create(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);
}