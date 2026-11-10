package by.bsuir.labqueue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Настройки приложения (раздел labqueue в application.yml).
 *
 * @param zone    часовой пояс, в котором показывается и вводится время (в базе время хранится в UTC)
 * @param baseUrl адрес приложения для ссылок в письмах
 * @param admin   учётная запись администратора, создаваемая при первом запуске
 */
@ConfigurationProperties(prefix = "labqueue")
public record LabQueueProperties(ZoneId zone, String baseUrl, Admin admin) {

    public LabQueueProperties {
        if (zone == null) {
            zone = ZoneId.of("Europe/Minsk");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        if (admin == null) {
            admin = new Admin(null, null);
        }
    }

    public record Admin(String email, String password) {
    }
}
