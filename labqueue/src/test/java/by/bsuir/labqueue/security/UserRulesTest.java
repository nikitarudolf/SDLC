package by.bsuir.labqueue.security;

import by.bsuir.labqueue.catalog.StudyGroup;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

    @Test
    void passwordPolicy() {
        assertThat(PasswordPolicy.problem("abc123")).contains("Пароль должен быть не короче 8 символов");
        assertThat(PasswordPolicy.problem("abcdefgh")).contains("Пароль должен содержать буквы и цифры");
        assertThat(PasswordPolicy.problem("12345678")).contains("Пароль должен содержать буквы и цифры");
        assertThat(PasswordPolicy.problem("labqueue2026")).isEmpty();
    }

    @Test
    void lockAfterFiveFailuresForFifteenMinutes() {
        User user = User.student("A@Mail.by ", "Иванов Иван", new StudyGroup("450501"), NOW);
        assertThat(user.getEmail()).isEqualTo("a@mail.by");
        for (int i = 0; i < 4; i++) {
            assertThat(user.registerFailedLogin(NOW)).isFalse();
        }
        assertThat(user.registerFailedLogin(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void successfulLoginResetsCounter() {
        User user = User.teacher("t@bsuir.by", "Петрова Анна Сергеевна", NOW);
        user.registerFailedLogin(NOW);
        user.registerFailedLogin(NOW);
        user.registerSuccessfulLogin();
        assertThat(user.getFailedAttempts()).isZero();
    }

    @Test
    void shortNameHidesPersonalData() {
        assertThat(User.teacher("t@bsuir.by", "Петрова Анна Сергеевна", NOW).shortName()).isEqualTo("Петрова А.");
    }

    @Test
    void cannotLogInBeforeEmailConfirmed() {
        User user = User.student("s@bsuir.by", "Иванов Иван", new StudyGroup("450501"), NOW);
        user.setPasswordHash("hash");
        user.issueToken("t", NOW.plus(Duration.ofHours(24)));
        assertThat(user.canLogIn()).isFalse();
        user.confirmEmail();
        assertThat(user.canLogIn()).isTrue();
        assertThat(user.getToken()).isNull();
    }
}
