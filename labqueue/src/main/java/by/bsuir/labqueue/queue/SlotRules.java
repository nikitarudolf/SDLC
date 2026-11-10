package by.bsuir.labqueue.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Правила параметров слота (REQ-FUNC-003, REQ-FUNC-005). */
public final class SlotRules {

    public static final Duration MIN_LENGTH = Duration.ofMinutes(15);
    public static final Duration MAX_LENGTH = Duration.ofHours(4);
    public static final int MIN_DEFENSE_MINUTES = 5;
    public static final int MAX_DEFENSE_MINUTES = 30;
    public static final int DEFAULT_DEFENSE_MINUTES = 10;

    private SlotRules() {
    }

    /** Проверка интервала и длительности защиты нового слота. */
    public static List<RuleViolation> validateNew(Instant startsAt, Instant endsAt, int defenseMinutes, Instant now) {
        List<RuleViolation> result = new ArrayList<>();
        if (!startsAt.isAfter(now)) {
            result.add(new RuleViolation("date", "Нельзя создать слот в прошлом"));
        }
        result.addAll(validateInterval(startsAt, endsAt, defenseMinutes));
        return result;
    }

    /** Проверка интервала, общая для создания и изменения слота. */
    public static List<RuleViolation> validateInterval(Instant startsAt, Instant endsAt, int defenseMinutes) {
        List<RuleViolation> result = new ArrayList<>();
        if (!endsAt.isAfter(startsAt)) {
            result.add(new RuleViolation("endTime", "Окончание должно быть позже начала"));
        } else {
            Duration length = Duration.between(startsAt, endsAt);
            if (length.compareTo(MIN_LENGTH) < 0) {
                result.add(new RuleViolation("endTime", "Слот должен длиться не меньше 15 минут"));
            } else if (length.compareTo(MAX_LENGTH) > 0) {
                result.add(new RuleViolation("endTime", "Слот должен длиться не больше 4 часов"));
            }
        }
        if (defenseMinutes < MIN_DEFENSE_MINUTES || defenseMinutes > MAX_DEFENSE_MINUTES) {
            result.add(new RuleViolation("defenseMinutes", "Длительность защиты — от 5 до 30 минут"));
        } else if (endsAt.isAfter(startsAt) && capacity(startsAt, endsAt, defenseMinutes) < 1) {
            result.add(new RuleViolation("defenseMinutes", "В слот не помещается ни одна защита"));
        }
        return result;
    }

    /** Вместимость: сколько защит целиком помещается в интервал. 10:00–11:30 по 10 мин → 9. */
    public static int capacity(Instant startsAt, Instant endsAt, int defenseMinutes) {
        long minutes = Duration.between(startsAt, endsAt).toMinutes();
        return (int) (minutes / defenseMinutes);
    }

    /** Пересекаются ли полуинтервалы [aStart, aEnd) и [bStart, bEnd). */
    public static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /** Нельзя сократить слот так, что уже записанные студенты не поместятся (REQ-FUNC-005). */
    public static List<RuleViolation> validateResize(int newCapacity, int seatsTaken) {
        if (newCapacity < seatsTaken) {
            return List.of(new RuleViolation("endTime",
                    "Нельзя сократить слот: записано " + seatsTaken + ", а вместимость станет " + newCapacity));
        }
        return List.of();
    }
}
