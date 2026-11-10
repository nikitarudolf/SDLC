package by.bsuir.labqueue.security;

import java.util.Optional;

/** Требования к паролю: не менее 8 символов, буквы и цифры (REQ-FUNC-001). */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static Optional<String> problem(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of("Пароль должен быть не короче 8 символов");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return Optional.of("Пароль должен содержать буквы и цифры");
        }
        return Optional.empty();
    }
}
