package by.bsuir.labqueue.queue;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Продвижение из листа ожидания (REQ-FUNC-010, представление SDD 008).
 * Вызывается внутри транзакции, в которой строка слота заблокирована.
 */
public final class QueuePolicy {

    private QueuePolicy() {
    }

    /**
     * Возвращает идентификаторы записей, которые нужно перевести в BOOKED, в порядке продвижения.
     * Если до начала слота 30 минут или меньше, лист заморожен и никто не продвигается.
     */
    public static List<Long> promote(Instant slotStartsAt, Instant now, int capacity, int seatsTaken,
                                     List<WaitlistItem> waitlist) {
        if (BookingRules.waitlistFrozen(slotStartsAt, now)) {
            return List.of();
        }
        int free = capacity - seatsTaken;
        if (free <= 0) {
            return List.of();
        }
        return waitlist.stream()
                .sorted(Comparator.comparing(WaitlistItem::createdAt).thenComparingLong(WaitlistItem::bookingId))
                .limit(free)
                .map(WaitlistItem::bookingId)
                .toList();
    }
}
