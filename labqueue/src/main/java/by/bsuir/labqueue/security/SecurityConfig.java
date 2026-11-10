package by.bsuir.labqueue.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Доступ по ролям (REQ-SEC-003), CSRF включён для всех изменяющих запросов (REQ-SEC-004),
 * пароли — BCrypt с cost 12 (REQ-SEC-001).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/verify", "/invite",
                                "/css/**", "/favicon.ico", "/actuator/health", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/slots/**").hasRole("STUDENT")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .successHandler(roleRedirect())
                        .failureHandler(loginFailure())
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    /** После входа студент попадает на экран слотов, преподаватель — на расписание (REQ-FUNC-001). */
    static AuthenticationSuccessHandler roleRedirect() {
        return (request, response, authentication) -> response.sendRedirect(
                request.getContextPath() + homeFor(authentication));
    }

    public static String homeFor(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (roles.contains("ROLE_ADMIN")) {
            return "/admin/groups";
        }
        if (roles.contains("ROLE_TEACHER")) {
            return "/teacher/slots";
        }
        return "/slots";
    }

    /** Неверный пароль и неизвестный e-mail дают одинаковое сообщение (REQ-FUNC-001, критерий 3). */
    static AuthenticationFailureHandler loginFailure() {
        return new AuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                                AuthenticationException exception) throws IOException {
                String reason = "error";
                if (exception instanceof LockedException) {
                    reason = "locked";
                } else if (exception instanceof DisabledException) {
                    reason = "unverified";
                }
                response.sendRedirect(request.getContextPath() + "/login?" + reason);
            }
        };
    }
}
