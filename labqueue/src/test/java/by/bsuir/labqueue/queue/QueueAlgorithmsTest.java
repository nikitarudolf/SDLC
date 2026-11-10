package by.bsuir.labqueue.queue;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Алгоритмы представления SDD 008: продвижение из листа ожидания и расчётное время. */
class QueueAlgorithmsTest {

    private static final Instant START = Instant.parse("2026-11-17T07:00:00Z");

    @Test
    void promotesInOrderOfCreation() {
        List<WaitlistItem> waitlist = List.of(
                new WaitlistItem(3, START.minus(Duration.ofHours(1))),
                new WaitlistItem(1, START.minus(Duration.ofHours(3))),
                new WaitlistItem(2, START.minus(Duration.ofHours(2))));
        List<Long> promoted = QueuePolicy.promote(START, START.minus(Duration.ofHours(2)), 9, 7, waitlist);
        assertThat(promoted).containsExactly(1L, 2L);
    }

    @Test
    void noPromotionWhenFullOrFrozen() {
        List<WaitlistItem> waitlist = List.of(new WaitlistItem(1, START.minus(Duration.ofDays(1))));
        assertThat(QueuePolicy.promote(START, START.minus(Duration.ofHours(2)), 9, 9, waitlist)).isEmpty();
        // за 30 минут до начала лист заморожен
        assertThat(QueuePolicy.promote(START, START.minus(Duration.ofMinutes(30)), 9, 5, waitlist)).isEmpty();
        assertThat(QueuePolicy.promote(START, START.minus(Duration.ofMinutes(31)), 9, 5, waitlist)).containsExactly(1L);
    }

    @Test
    void etaBeforeStartSkipsCancelled() {
        List<QueueItem> items = List.of(
                new QueueItem(10, 1, BookingStatus.BOOKED, null),
                new QueueItem(11, 2, BookingStatus.CANCELLED, null),
                new QueueItem(12, 3, BookingStatus.BOOKED, null),
                new QueueItem(13, 0, BookingStatus.WAITLISTED, null));
        Map<Long, Instant> eta = EtaCalculator.calculate(START, 10, items, START.minus(Duration.ofDays(1)), false);
        assertThat(eta).containsOnlyKeys(10L, 12L);
        assertThat(eta.get(10L)).isEqualTo(START);
        assertThat(eta.get(12L)).isEqualTo(START.plus(Duration.ofMinutes(10)));
    }

    @Test
    void etaDuringDefenseCountsFromCall() {
        Instant called = START.plus(Duration.ofMinutes(25));
        Instant now = called.plus(Duration.ofMinutes(3));
        List<QueueItem> items = List.of(
                new QueueItem(1, 1, BookingStatus.PASSED, null),
                new QueueItem(2, 2, BookingStatus.DEFENDING, called),
                new QueueItem(3, 3, BookingStatus.IN_QUEUE, null),
                new QueueItem(4, 4, BookingStatus.IN_QUEUE, null));
        Map<Long, Instant> eta = EtaCalculator.calculate(START, 10, items, now, true);
        assertThat(eta.get(2L)).isEqualTo(called);
        assertThat(eta.get(3L)).isEqualTo(now.plus(Duration.ofMinutes(10)));
        assertThat(eta.get(4L)).isEqualTo(now.plus(Duration.ofMinutes(20)));
    }

    @Test
    void seatAvailabilityLabels() {
        assertThat(SeatAvailability.of(9, 0)).isEqualTo(SeatAvailability.FREE);
        assertThat(SeatAvailability.of(9, 7)).isEqualTo(SeatAvailability.FEW);
        assertThat(SeatAvailability.of(9, 9)).isEqualTo(SeatAvailability.FULL);
    }

    @Test
    void slotPhasesFollowStateDiagram() {
        assertThat(SlotPhase.planned(START, START.minus(Duration.ofDays(8)))).isEqualTo(SlotPhase.SCHEDULED);
        assertThat(SlotPhase.planned(START, START.minus(Duration.ofDays(2)))).isEqualTo(SlotPhase.OPEN);
        assertThat(SlotPhase.planned(START, START.minus(Duration.ofMinutes(45))))
                .isEqualTo(SlotPhase.REGISTRATION_CLOSED);
        assertThat(SlotPhase.planned(START, START.minus(Duration.ofMinutes(20)))).isEqualTo(SlotPhase.FROZEN);
    }
}
