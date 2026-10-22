package app.services;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordService {

    public String hashPassword(String password) {
        return BCrypt.hashpw(
            password,
            BCrypt.gensalt());
    }

    public boolean verifyPassword(String password, String passwordHash) {
        return BCrypt.checkpw(
            password,
            passwordHash);
    }
}