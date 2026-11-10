package by.bsuir.labqueue.journal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Запись действий в журнал в рамках текущей транзакции. */
@Service
public class Journal {

    private final JournalRepository repository;
    private final Clock clock;

    public Journal(JournalRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String actor, String action, String details) {
        repository.save(new JournalEntry(clock.instant(), actor, action, details));
    }
}
