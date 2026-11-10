package by.bsuir.labqueue.queue;

import java.time.Duration;
import java.time.Instant;

/**
 * Сторожевые условия переходов записи (REQ-FUNC-007, 008, 010, 020).
 * Класс не зависит от Spring и базы данных, поэтому проверяется модульными тестами.
 */
public final class BookingRules {

    /** Запись открывается за 7 суток до начала слота. */
    public static final Duration REGISTRATION_OPENS = Duration.ofDays(7);
    /** Запись и отмена закрываются за 60 минут до начала слота. */
    public static final Duration REGISTRATION_CLOSES = Duration.ofMinutes(60);
    /** Лист ожидания замораживается за 30 минут до начала слота. */
    public static final Duration WAITLIST_FREEZE = Duration.ofMinutes(30);
    /** Неявку можно отметить не раньше чем через 5 минут после вызова. */
    public static final Duration NO_SHOW_DELAY = Duration.ofMinutes(5);

    private BookingRules() {
    }

    /** Проверяет общий переход по таблице состояний. */
    public static void requireTransition(BookingStatus from, BookingStatus to) {
        if (!from.canMoveTo(to)) {
            throw new IllegalTransitionException(from, to,
                    "Переход «" + from.label() + "» → «" + to.label() + "» не предусмотрен");
        }
    }

    /** Отмена записи студентом: не позже чем за 60 минут до начала слота. */
    public static void requireStudentCancel(BookingStatus from, Instant slotStartsAt, Instant now) {
        requireTransition(from, BookingStatus.CANCELLED);
        if (Duration.between(now, slotStartsAt).compareTo(REGISTRATION_CLOSES) < 0) {
            throw new IllegalTransitionException(from, BookingStatus.CANCELLED,
                    "До начала слота меньше 60 минут — сообщите преподавателю");
        }
    }

    /** Отметка «Не явился»: не раньше чем через 5 минут после вызова. */
    public static void requireNoShow(BookingStatus from, Instant calledAt, Instant now) {
        requireTransition(from, BookingStatus.NO_SHOW);
        if (calledAt == null || Duration.between(calledAt, now).compareTo(NO_SHOW_DELAY) < 0) {
            throw new IllegalTransitionException(from, BookingStatus.NO_SHOW,
                    "После вызова прошло меньше 5 минут");
        }
    }

    /** Открыто ли окно записи на слот в момент {@code now}. */
    public static boolean registrationOpen(Instant slotStartsAt, Instant now) {
        Duration left = Duration.between(now, slotStartsAt);
        return left.compareTo(REGISTRATION_OPENS) <= 0 && left.compareTo(REGISTRATION_CLOSES) >= 0;
    }

    /** Заморожен ли лист ожидания (до начала 30 минут или меньше). */
    public static boolean waitlistFrozen(Instant slotStartsAt, Instant now) {
        return Duration.between(now, slotStartsAt).compareTo(WAITLIST_FREEZE) <= 0;
    }
}
