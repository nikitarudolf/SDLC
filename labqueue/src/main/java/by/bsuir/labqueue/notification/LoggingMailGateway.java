package by.bsuir.labqueue.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Письма пишутся в лог и хранятся в памяти (последние 100) — для разработки и тестов. */
@Component
public class LoggingMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailGateway.class);
    private static final int KEEP = 100;

    private final Deque<SentMail> sent = new ConcurrentLinkedDeque<>();

    @Override
    public void send(String to, String subject, String body) {
        log.info("Письмо для {}: «{}»\n{}", to, subject, body);
        sent.addLast(new SentMail(to, subject, body));
        while (sent.size() > KEEP) {
            sent.pollFirst();
        }
    }

    public List<SentMail> sent() {
        return new ArrayList<>(sent);
    }

    public List<SentMail> sentTo(String to) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(to)).toList();
    }

    public record SentMail(String to, String subject, String body) {
    }
}
