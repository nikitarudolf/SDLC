package by.bsuir.labqueue.queue;

import java.time.Duration;
import java.time.Instant;

/**
 * Фаза слота. Подсостояния составного состояния Planned вычисляются по времени
 * (диаграмма sdd/uml/state-slot.puml), остальные хранятся в базе как статус слота.
 */
public enum SlotPhase {
    SCHEDULED("Запись ещё не открыта"),
    OPEN("Идёт запись"),
    REGISTRATION_CLOSED("Запись закрыта"),
    FROZEN("Лист ожидания заморожен"),
    IN_PROGRESS("Идёт защита"),
    FINISHED("Завершён"),
    CANCELLED("Отменён");

    private final String label;

    SlotPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Подсостояние запланированного слота в момент {@code now}. */
    public static SlotPhase planned(Instant startsAt, Instant now) {
        Duration left = Duration.between(now, startsAt);
        if (left.compareTo(BookingRules.REGISTRATION_OPENS) > 0) {
            return SCHEDULED;
        }
        if (left.compareTo(BookingRules.REGISTRATION_CLOSES) >= 0) {
            return OPEN;
        }
        if (left.compareTo(BookingRules.WAITLIST_FREEZE) > 0) {
            return REGISTRATION_CLOSED;
        }
        return FROZEN;
    }
}
