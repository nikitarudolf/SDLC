package by.bsuir.labqueue.queue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Статусы записи на защиту и разрешённые переходы между ними.
 * Таблица переходов повторяет диаграмму состояний записи
 * (sdd/uml/state-booking.puml, представление SDD 018).
 */
public enum BookingStatus {
    WAITLISTED("В листе ожидания"),
    BOOKED("Записан"),
    IN_QUEUE("В очереди"),
    DEFENDING("Защищается"),
    PASSED("Защищено"),
    REWORK("На доработку"),
    NO_SHOW("Не явился"),
    CANCELLED("Отменена"),
    EXPIRED("Истекла");

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED.put(WAITLISTED, EnumSet.of(BOOKED, EXPIRED, CANCELLED));
        ALLOWED.put(BOOKED, EnumSet.of(IN_QUEUE, CANCELLED));
        ALLOWED.put(IN_QUEUE, EnumSet.of(DEFENDING));
        ALLOWED.put(DEFENDING, EnumSet.of(PASSED, REWORK, NO_SHOW));
        for (BookingStatus s : values()) {
            ALLOWED.putIfAbsent(s, EnumSet.noneOf(BookingStatus.class));
        }
    }

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canMoveTo(BookingStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Запись ещё участвует в слоте (занимает место или стоит в листе ожидания). */
    public boolean isActive() {
        return this == WAITLISTED || holdsSeat();
    }

    /** Запись занимает место в слоте и учитывается во вместимости. */
    public boolean holdsSeat() {
        return this == BOOKED || this == IN_QUEUE || this == DEFENDING;
    }

    public boolean isFinal() {
        return ALLOWED.get(this).isEmpty();
    }

    public static Set<BookingStatus> seatHolding() {
        return EnumSet.of(BOOKED, IN_QUEUE, DEFENDING);
    }

    public static Set<BookingStatus> active() {
        return EnumSet.of(WAITLISTED, BOOKED, IN_QUEUE, DEFENDING);
    }
}
