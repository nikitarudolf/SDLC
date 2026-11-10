package by.bsuir.labqueue.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Расчётное время защиты (REQ-FUNC-011, представление SDD 008).
 * <ul>
 *   <li>слот не начат: eta = startsAt + (k − 1) · defense, где k — номер среди занимающих место;</li>
 *   <li>слот идёт: eta = max(now, calledAt(текущего)) + k · defense для k-го после текущего.</li>
 * </ul>
 * Отменённые записи и записи из листа ожидания пропускаются.
 */
public final class EtaCalculator {

    private EtaCalculator() {
    }

    public static Map<Long, Instant> calculate(Instant startsAt, int defenseMinutes, List<QueueItem> items,
                                               Instant now, boolean started) {
        Duration defense = Duration.ofMinutes(defenseMinutes);
        List<QueueItem> seated = items.stream()
                .filter(i -> i.status().holdsSeat())
                .sorted(Comparator.comparingInt(QueueItem::position))
                .toList();
        Map<Long, Instant> result = new LinkedHashMap<>();
        if (!started) {
            for (int k = 0; k < seated.size(); k++) {
                result.put(seated.get(k).bookingId(), startsAt.plus(defense.multipliedBy(k)));
            }
            return result;
        }
        QueueItem current = seated.stream()
                .filter(i -> i.status() == BookingStatus.DEFENDING)
                .findFirst()
                .orElse(null);
        Instant base;
        int offset;
        if (current != null) {
            Instant calledAt = current.calledAt() != null ? current.calledAt() : now;
            result.put(current.bookingId(), calledAt);
            base = max(now, calledAt);
            offset = 1;
        } else {
            base = max(now, startsAt);
            offset = 0;
        }
        int k = 0;
        for (QueueItem item : seated) {
            if (item == current) {
                continue;
            }
            result.put(item.bookingId(), base.plus(defense.multipliedBy(k + offset)));
            k++;
        }
        return result;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
