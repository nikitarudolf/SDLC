package by.bsuir.labqueue.queue;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-FUNC-003 и REQ-FUNC-005: вместимость, длина слота, пересечения, сокращение. */
class SlotRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant TEN = Instant.parse("2026-11-17T07:00:00Z"); // 10:00 по Минску

    @Test
    void capacityFromAcceptanceCriterion() {
        // слот 10:00–11:30 с длительностью 10 мин получает вместимость 9
        assertThat(SlotRules.capacity(TEN, TEN.plus(Duration.ofMinutes(90)), 10)).isEqualTo(9);
        assertThat(SlotRules.capacity(TEN, TEN.plus(Duration.ofMinutes(95)), 10)).isEqualTo(9);
        assertThat(SlotRules.capacity(TEN, TEN.plus(Duration.ofMinutes(40)), 20)).isEqualTo(2);
    }

    @Test
    void validSlotHasNoViolations() {
        assertThat(SlotRules.validateNew(TEN, TEN.plus(Duration.ofMinutes(90)), 10, NOW)).isEmpty();
    }

    @Test
    void slotInPastRejected() {
        List<RuleViolation> v = SlotRules.validateNew(NOW.minusSeconds(60), NOW.plusSeconds(3600), 10, NOW);
        assertThat(v).extracting(RuleViolation::field).contains("date");
    }

    @Test
    void endBeforeStartRejected() {
        List<RuleViolation> v = SlotRules.validateNew(TEN, TEN.minusSeconds(60), 10, NOW);
        assertThat(v).extracting(RuleViolation::message).contains("Окончание должно быть позже начала");
    }

    @Test
    void lengthBounds() {
        assertThat(SlotRules.validateInterval(TEN, TEN.plus(Duration.ofMinutes(14)), 5))
                .extracting(RuleViolation::field).contains("endTime");
        assertThat(SlotRules.validateInterval(TEN, TEN.plus(Duration.ofMinutes(15)), 5)).isEmpty();
        assertThat(SlotRules.validateInterval(TEN, TEN.plus(Duration.ofHours(4)), 10)).isEmpty();
        assertThat(SlotRules.validateInterval(TEN, TEN.plus(Duration.ofMinutes(241)), 10))
                .extracting(RuleViolation::field).contains("endTime");
    }

    @Test
    void defenseBounds() {
        assertThat(SlotRules.validateInterval(TEN, TEN.plus(Duration.ofHours(1)), 4))
                .extracting(RuleViolation::field).containsExactly("defenseMinutes");
        assertThat(SlotRules.validateInterval(TEN, TEN.plus(Duration.ofHours(1)), 31))
                .extracting(RuleViolation::field).containsExactly("defenseMinutes");
        assertThat(SlotRules.validateInterval(TEN, TEN.plus(Duration.ofMinutes(20)), 30))
                .extracting(RuleViolation::message).containsExactly("В слот не помещается ни одна защита");
    }

    @Test
    void overlapIsHalfOpen() {
        Instant a = TEN;
        Instant b = TEN.plus(Duration.ofMinutes(90));
        assertThat(SlotRules.overlaps(a, b, b, b.plusSeconds(3600))).isFalse();
        assertThat(SlotRules.overlaps(a, b, b.minusSeconds(60), b.plusSeconds(3600))).isTrue();
    }

    @Test
    void cannotShrinkBelowBooked() {
        // попытка сократить слот с 8 записанными до вместимости 6 отклоняется
        assertThat(SlotRules.validateResize(6, 8)).hasSize(1)
                .first().extracting(RuleViolation::message).asString().contains("записано 8");
        assertThat(SlotRules.validateResize(8, 8)).isEmpty();
    }
}
