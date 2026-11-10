package by.bsuir.labqueue.notification;

/**
 * Отправка писем. В итерации I1 письма записываются в журнал приложения
 * ({@link LoggingMailGateway}); SMTP и transactional outbox (ADR-0005) подключаются в F13.
 */
public interface MailGateway {

    void send(String to, String subject, String body);
}
