package by.bsuir.labqueue.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Загрузка учётной записи для Spring Security. Неподтверждённая запись отключена,
 * заблокированная после пяти неудачных попыток — заперта на 15 минут.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final Clock clock;

    public AppUserDetailsService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        User user = users.findByEmail(User.normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash() == null ? "" : user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(!user.canLogIn())
                .accountLocked(user.isLocked(clock.instant()))
                .build();
    }
}
