package by.bsuir.labqueue.booking;

import by.bsuir.labqueue.queue.BookingRules;
import by.bsuir.labqueue.queue.BookingStatus;
import by.bsuir.labqueue.security.User;
import by.bsuir.labqueue.slot.Slot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Запись студента на защиту ЛР. Статус меняется только через методы, которые
 * проверяют таблицу переходов {@link BookingStatus} и сторожевые условия {@link BookingRules}.
 * Сервис записи (F07) подключается в итерации I2; в I1 сущность используется при отмене слота.
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id")
    private Slot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "lab_number", nullable = false)
    private int labNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "position")
    private Integer position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "noshow_ack_at", nullable = false)
    private Instant noShowAckAt;

    protected Booking() {
    }

    /** Новая запись: BOOKED с позицией или WAITLISTED без позиции (выбор на диаграмме состояний). */
    public Booking(Slot slot, User student, int labNumber, Integer position, Instant now, Instant noShowAckAt) {
        this.slot = slot;
        this.student = student;
        this.labNumber = labNumber;
        this.position = position;
        this.status = position == null ? BookingStatus.WAITLISTED : BookingStatus.BOOKED;
        this.createdAt = now;
        this.noShowAckAt = noShowAckAt;
    }

    /** Слот отменён преподавателем: запись отменяется без проверки срока. */
    public void cancelBySlot() {
        BookingRules.requireTransition(status, BookingStatus.CANCELLED);
        status = BookingStatus.CANCELLED;
    }

    public void cancelByStudent(Instant now) {
        BookingRules.requireStudentCancel(status, slot.getStartsAt(), now);
        status = BookingStatus.CANCELLED;
    }

    public void markNoShow(Instant now) {
        BookingRules.requireNoShow(status, calledAt, now);
        status = BookingStatus.NO_SHOW;
    }

    public Long getId() {
        return id;
    }

    public Slot getSlot() {
        return slot;
    }

    public User getStudent() {
        return student;
    }

    public int getLabNumber() {
        return labNumber;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Integer getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCalledAt() {
        return calledAt;
    }

    public Instant getNoShowAckAt() {
        return noShowAckAt;
    }
}
