package by.bsuir.labqueue.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Запись журнала действий (REQ-OBS-001): кто, когда и что сделал. */
@Entity
@Table(name = "journal_entry")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant at;

    @Column(name = "actor", nullable = false, length = 254)
    private String actor;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "details", length = 500)
    private String details;

    protected JournalEntry() {
    }

    public JournalEntry(Instant at, String actor, String action, String details) {
        this.at = at;
        this.actor = actor;
        this.action = action;
        this.details = details == null || details.length() <= 500 ? details : details.substring(0, 500);
    }

    public Long getId() {
        return id;
    }

    public Instant getAt() {
        return at;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }
}
