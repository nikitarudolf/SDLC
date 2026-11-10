package by.bsuir.labqueue.queue;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static by.bsuir.labqueue.queue.BookingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/** Переходы записи совпадают с диаграммой состояний (рисунок 3.4 отчёта ЛР5, таблица 3.6). */
class BookingStatusTest {

    private static final Instant START = Instant.parse("2026-11-17T07:00:00Z");

    @Test
    void allowedTransitionsMatchStateDiagram() {
        assertThat(WAITLISTED.canMoveTo(BOOKED)).isTrue();
        assertThat(WAITLISTED.canMoveTo(EXPIRED)).isTrue();
        assertThat(WAITLISTED.canMoveTo(CANCELLED)).isTrue();
        assertThat(BOOKED.canMoveTo(IN_QUEUE)).isTrue();
        assertThat(BOOKED.canMoveTo(CANCELLED)).isTrue();
        assertThat(IN_QUEUE.canMoveTo(DEFENDING)).isTrue();
        assertThat(DEFENDING.canMoveTo(PASSED)).isTrue();
        assertThat(DEFENDING.canMoveTo(REWORK)).isTrue();
        assertThat(DEFENDING.canMoveTo(NO_SHOW)).isTrue();
    }

    @Test
    void forbiddenTransitions() {
        assertThat(BOOKED.canMoveTo(PASSED)).isFalse();
        assertThat(IN_QUEUE.canMoveTo(CANCELLED)).isFalse();
        assertThat(NO_SHOW.canMoveTo(BOOKED)).isFalse();
        assertThat(CANCELLED.canMoveTo(BOOKED)).isFalse();
        assertThatThrownBy(() -> BookingRules.requireTransition(PASSED, BOOKED))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void finalStatesHaveNoExits() {
        assertThat(PASSED.isFinal()).isTrue();
        assertThat(REWORK.isFinal()).isTrue();
        assertThat(NO_SHOW.isFinal()).isTrue();
        assertThat(CANCELLED.isFinal()).isTrue();
        assertThat(EXPIRED.isFinal()).isTrue();
        assertThat(BOOKED.isFinal()).isFalse();
    }

    @Test
    void seatHoldingStatuses() {
        assertThat(BOOKED.holdsSeat()).isTrue();
        assertThat(DEFENDING.holdsSeat()).isTrue();
        assertThat(WAITLISTED.holdsSeat()).isFalse();
        assertThat(WAITLISTED.isActive()).isTrue();
        assertThat(CANCELLED.isActive()).isFalse();
    }

    @Test
    void studentCancelAllowedOnlyAnHourBeforeStart() {
        assertThatNoException().isThrownBy(
                () -> BookingRules.requireStudentCancel(BOOKED, START, START.minus(Duration.ofMinutes(60))));
        assertThatThrownBy(() -> BookingRules.requireStudentCancel(BOOKED, START, START.minus(Duration.ofMinutes(59))))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("60 минут");
    }

    @Test
    void noShowOnlyFiveMinutesAfterCall() {
        Instant called = START.plus(Duration.ofMinutes(20));
        assertThatThrownBy(() -> BookingRules.requireNoShow(DEFENDING, called, called.plus(Duration.ofMinutes(4))))
                .isInstanceOf(IllegalTransitionException.class);
        assertThatNoException().isThrownBy(
                () -> BookingRules.requireNoShow(DEFENDING, called, called.plus(Duration.ofMinutes(5))));
        assertThatThrownBy(() -> BookingRules.requireNoShow(BOOKED, called, called.plus(Duration.ofMinutes(10))))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void registrationWindow() {
        assertThat(BookingRules.registrationOpen(START, START.minus(Duration.ofDays(8)))).isFalse();
        assertThat(BookingRules.registrationOpen(START, START.minus(Duration.ofDays(7)))).isTrue();
        assertThat(BookingRules.registrationOpen(START, START.minus(Duration.ofMinutes(61)))).isTrue();
        assertThat(BookingRules.registrationOpen(START, START.minus(Duration.ofMinutes(59)))).isFalse();
    }
}
