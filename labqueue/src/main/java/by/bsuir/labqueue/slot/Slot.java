package by.bsuir.labqueue.slot;

import by.bsuir.labqueue.catalog.Discipline;
import by.bsuir.labqueue.catalog.StudyGroup;
import by.bsuir.labqueue.queue.SlotPhase;
import by.bsuir.labqueue.queue.SlotRules;
import by.bsuir.labqueue.security.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Слот защиты (REQ-FUNC-003, REQ-FUNC-005). */
@Entity
@Table(name = "slot")
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discipline_id")
    private Discipline discipline;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @ManyToMany
    @JoinTable(name = "slot_group",
            joinColumns = @JoinColumn(name = "slot_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<StudyGroup> groups = new LinkedHashSet<>();

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false, length = 200)
    private String place;

    @Column(name = "defense_minutes", nullable = false)
    private int defenseMinutes;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private SlotStatus status;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Slot() {
    }

    public Slot(Discipline discipline, User teacher, Set<StudyGroup> groups, Instant startsAt, Instant endsAt,
                String place, int defenseMinutes, Instant now) {
        this.discipline = discipline;
        this.teacher = teacher;
        this.groups.addAll(groups);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.place = place.trim();
        this.defenseMinutes = defenseMinutes;
        this.capacity = SlotRules.capacity(startsAt, endsAt, defenseMinutes);
        this.status = SlotStatus.PLANNED;
        this.createdAt = now;
    }

    public SlotPhase phase(Instant now) {
        return switch (status) {
            case PLANNED -> SlotPhase.planned(startsAt, now);
            case IN_PROGRESS -> SlotPhase.IN_PROGRESS;
            case FINISHED -> SlotPhase.FINISHED;
            case CANCELLED -> SlotPhase.CANCELLED;
        };
    }

    public boolean ownedBy(User user) {
        return teacher.getId().equals(user.getId());
    }

    public boolean isPlanned() {
        return status == SlotStatus.PLANNED;
    }

    void changePlace(String place) {
        this.place = place.trim();
    }

    void changeEnd(Instant endsAt) {
        this.endsAt = endsAt;
        this.capacity = SlotRules.capacity(startsAt, endsAt, defenseMinutes);
    }

    void cancel(String reason) {
        this.status = SlotStatus.CANCELLED;
        this.cancelReason = reason.trim();
    }

    public String groupNumbers() {
        return groups.stream().map(StudyGroup::getNumber).sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(", "));
    }

    public Long getId() {
        return id;
    }

    public Discipline getDiscipline() {
        return discipline;
    }

    public User getTeacher() {
        return teacher;
    }

    public Set<StudyGroup> getGroups() {
        return groups;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getPlace() {
        return place;
    }

    public int getDefenseMinutes() {
        return defenseMinutes;
    }

    public int getCapacity() {
        return capacity;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public int getVersion() {
        return version;
    }
}
