package by.bsuir.labqueue.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;

/** Получение учётной записи текущего пользователя по принципалу запроса. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User get(Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Требуется вход");
        }
        return users.findByEmail(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("Учётная запись не найдена"));
    }
}
