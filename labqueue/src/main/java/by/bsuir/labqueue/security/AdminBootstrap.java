package by.bsuir.labqueue.security;

import by.bsuir.labqueue.config.LabQueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Создаёт администратора из переменных ADMIN_EMAIL и ADMIN_PASSWORD при первом запуске. */
@Component
@Order(0)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AccountService accounts;
    private final LabQueueProperties properties;

    public AdminBootstrap(AccountService accounts, LabQueueProperties properties) {
        this.accounts = accounts;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.admin().email();
        String password = properties.admin().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD не заданы — администратор не создаётся");
            return;
        }
        if (accounts.ensureAdmin(email, password)) {
            log.info("Создан администратор {}", email);
        }
    }
}
