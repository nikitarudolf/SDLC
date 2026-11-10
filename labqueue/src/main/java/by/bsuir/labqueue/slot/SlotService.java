package by.bsuir.labqueue.slot;

import by.bsuir.labqueue.booking.Booking;
import by.bsuir.labqueue.booking.BookingRepository;
import by.bsuir.labqueue.catalog.Discipline;
import by.bsuir.labqueue.catalog.DisciplineRepository;
import by.bsuir.labqueue.catalog.StudyGroup;
import by.bsuir.labqueue.common.ValidationFailedException;
import by.bsuir.labqueue.journal.Journal;
import by.bsuir.labqueue.queue.BookingStatus;
import by.bsuir.labqueue.queue.RuleViolation;
import by.bsuir.labqueue.queue.SeatAvailability;
import by.bsuir.labqueue.queue.SlotPhase;
import by.bsuir.labqueue.queue.SlotRules;
import by.bsuir.labqueue.security.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Управление слотами преподавателя (F04) и список слотов студента (F06, базовая часть). */
@Service
public class SlotService {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final SlotRepository slots;
    private final DisciplineRepository disciplines;
    private final BookingRepository bookings;
    private final Journal journal;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId zone;

    public SlotService(SlotRepository slots, DisciplineRepository disciplines, BookingRepository bookings,
                       Journal journal, ApplicationEventPublisher events, Clock clock, ZoneId appZone) {
        this.slots = slots;
        this.disciplines = disciplines;
        this.bookings = bookings;
        this.journal = journal;
        this.events = events;
        this.clock = clock;
        this.zone = appZone;
    }

    // ------------------------------------------------------------ создание

    @Transactional
    public Slot create(User teacher, SlotForm form) {
        Discipline discipline = disciplines.findById(form.getDisciplineId())
                .orElseThrow(() -> new ValidationFailedException("disciplineId", "Дисциплина не найдена"));
        if (!discipline.ownedBy(teacher)) {
            throw new AccessDeniedException("Чужая дисциплина");
        }
        Set<StudyGroup> groups = selectGroups(discipline, form.getGroupIds());
        Instant now = clock.instant();
        Instant startsAt = toInstant(form.getDate(), form.getStartTime());
        Instant endsAt = toInstant(form.getDate(), form.getEndTime());
        int defense = form.getDefenseMinutes();

        List<RuleViolation> problems = new ArrayList<>(SlotRules.validateNew(startsAt, endsAt, defense, now));
        if (problems.isEmpty()) {
            problems.addAll(overlapProblems(teacher, startsAt, endsAt, -1L));
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }
        Slot slot = slots.save(new Slot(discipline, teacher, groups, startsAt, endsAt, form.getPlace(), defense, now));
        journal.record(teacher.getEmail(), "SLOT_CREATED",
                describe(slot) + ", вместимость " + slot.getCapacity());
        return slot;
    }

    private Set<StudyGroup> selectGroups(Discipline discipline, List<Long> groupIds) {
        Set<Long> wanted = new HashSet<>(groupIds);
        Set<StudyGroup> selected = discipline.getGroups().stream()
                .filter(g -> wanted.contains(g.getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selected.isEmpty() || selected.size() != wanted.size()) {
            throw new ValidationFailedException("groupIds", "Выберите группы, привязанные к дисциплине");
        }
        return selected;
    }

    private List<RuleViolation> overlapProblems(User teacher, Instant startsAt, Instant endsAt, Long excludeId) {
        return slots.findOverlapping(teacher.getId(), startsAt, endsAt, excludeId).stream()
                .findFirst()
                .map(other -> List.of(new RuleViolation("startTime",
                        "Пересекается с вашим слотом " + day(other.getStartsAt()) + " " + timeRange(other))))
                .orElse(List.of());
    }

    // ------------------------------------------------------------ изменение и отмена

    @Transactional
    public Slot update(User teacher, Long slotId, SlotEditForm form) {
        Slot slot = lockOwnPlanned(teacher, slotId);
        LocalDate date = slot.getStartsAt().atZone(zone).toLocalDate();
        Instant newEnd = toInstant(date, form.getEndTime());
        List<RuleViolation> problems = new ArrayList<>(
                SlotRules.validateInterval(slot.getStartsAt(), newEnd, slot.getDefenseMinutes()));
        if (problems.isEmpty()) {
            int newCapacity = SlotRules.capacity(slot.getStartsAt(), newEnd, slot.getDefenseMinutes());
            int taken = (int) bookings.countBySlotIdAndStatusIn(slot.getId(), BookingStatus.seatHolding());
            problems.addAll(SlotRules.validateResize(newCapacity, taken));
            problems.addAll(overlapProblems(teacher, slot.getStartsAt(), newEnd, slot.getId()));
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(problems);
        }
        String before = describe(slot);
        slot.changePlace(form.getPlace());
        slot.changeEnd(newEnd);
        journal.record(teacher.getEmail(), "SLOT_UPDATED", before + " → " + describe(slot));
        return slot;
    }

    @Transactional
    public Slot cancel(User teacher, Long slotId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationFailedException("reason", "Укажите причину отмены");
        }
        Slot slot = lockOwnPlanned(teacher, slotId);
        List<Booking> affected = bookings.findBySlotIdAndStatusIn(slot.getId(), BookingStatus.active());
        List<String> emails = new ArrayList<>();
        for (Booking booking : affected) {
            booking.cancelBySlot();
            emails.add(booking.getStudent().getEmail());
        }
        slot.cancel(reason);
        journal.record(teacher.getEmail(), "SLOT_CANCELLED",
                describe(slot) + "; причина: " + reason.trim() + "; отменено записей: " + affected.size());
        events.publishEvent(new SlotCancelledEvent(slot.getId(), slot.getDiscipline().getName(),
                day(slot.getStartsAt()) + " " + timeRange(slot), reason.trim(), emails));
        return slot;
    }

    private Slot lockOwnPlanned(User teacher, Long slotId) {
        Slot slot = slots.lockById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Слот не найден"));
        if (!slot.ownedBy(teacher)) {
            throw new AccessDeniedException("Чужой слот");
        }
        if (!slot.isPlanned()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Слот уже начался, завершён или отменён");
        }
        return slot;
    }

    @Transactional(readOnly = true)
    public Slot ownSlot(User teacher, Long slotId) {
        Slot slot = slots.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Слот не найден"));
        if (!slot.ownedBy(teacher)) {
            throw new AccessDeniedException("Чужой слот");
        }
        return slot;
    }

    // ------------------------------------------------------------ списки

    @Transactional(readOnly = true)
    public List<TeacherSlotRow> teacherSchedule(User teacher) {
        Instant now = clock.instant();
        List<Slot> list = slots.findTeacherSlotsEndingAfter(teacher.getId(), now.minusSeconds(7 * 24 * 3600));
        List<Long> ids = list.stream().map(Slot::getId).toList();
        Map<Long, Integer> taken = bookings.seatsTaken(ids);
        Map<Long, Integer> waiting = bookings.waitlistLength(ids);
        return list.stream().map(s -> new TeacherSlotRow(
                s.getId(), dayWithWeekday(s.getStartsAt()), timeRange(s), s.getDiscipline().getName(),
                s.groupNumbers(), s.getPlace(), s.getDefenseMinutes(), s.getCapacity(),
                taken.getOrDefault(s.getId(), 0), waiting.getOrDefault(s.getId(), 0),
                s.phase(now).label(), s.isPlanned() && s.getStartsAt().isAfter(now), s.getCancelReason()
        )).toList();
    }

    /** Слоты групп студента на неделю, которая содержит {@code anyDay} (REQ-FUNC-006). */
    @Transactional(readOnly = true)
    public List<StudentSlotRow> studentWeek(User student, LocalDate anyDay, Long disciplineId) {
        if (student.getGroup() == null) {
            return List.of();
        }
        LocalDate monday = weekStart(anyDay);
        Instant from = monday.atStartOfDay(zone).toInstant();
        Instant to = monday.plusDays(7).atStartOfDay(zone).toInstant();
        List<Slot> list = slots.findForGroup(student.getGroup().getId(), from, to).stream()
                .filter(s -> disciplineId == null || s.getDiscipline().getId().equals(disciplineId))
                .toList();
        List<Long> ids = list.stream().map(Slot::getId).toList();
        Map<Long, Integer> taken = bookings.seatsTaken(ids);
        Map<Long, Integer> waiting = bookings.waitlistLength(ids);
        Map<Long, Booking> mine = ids.isEmpty() ? Map.of() : bookings.findByStudentIdAndSlotIdIn(student.getId(), ids)
                .stream()
                .filter(b -> b.getStatus().isActive() || b.getStatus().isFinal())
                .collect(Collectors.toMap(b -> b.getSlot().getId(), Function.identity(), (a, b) -> a));
        Instant now = clock.instant();
        return list.stream().map(s -> {
            int t = taken.getOrDefault(s.getId(), 0);
            SeatAvailability availability = SeatAvailability.of(s.getCapacity(), t);
            boolean cancelled = s.getStatus() == SlotStatus.CANCELLED;
            Booking my = mine.get(s.getId());
            return new StudentSlotRow(s.getId(), dayWithWeekday(s.getStartsAt()), timeRange(s),
                    s.getDiscipline().getName(), s.getTeacher().getFullName(), s.getPlace(), s.getCapacity(),
                    Math.max(0, s.getCapacity() - t), waiting.getOrDefault(s.getId(), 0),
                    cancelled ? "Отменён" : availability.label(),
                    cancelled ? "cancelled" : availability.css(),
                    s.phase(now).label(),
                    my == null ? "—" : my.getStatus().label(),
                    s.getCancelReason());
        }).toList();
    }

    public LocalDate weekStart(LocalDate anyDay) {
        return anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    // ------------------------------------------------------------ форматирование

    private Instant toInstant(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    private String day(Instant instant) {
        return instant.atZone(zone).format(DAY);
    }

    private String dayWithWeekday(Instant instant) {
        ZonedDateTime z = instant.atZone(zone);
        return z.format(DAY) + ", " + z.getDayOfWeek().getDisplayName(TextStyle.SHORT, RU);
    }

    public String timeRange(Slot slot) {
        return slot.getStartsAt().atZone(zone).format(TIME) + "–" + slot.getEndsAt().atZone(zone).format(TIME);
    }

    private String describe(Slot slot) {
        return "слот #" + slot.getId() + " " + slot.getDiscipline().getName() + " " + day(slot.getStartsAt())
                + " " + timeRange(slot) + ", " + slot.getPlace();
    }

    public SlotEditForm editFormFor(Slot slot) {
        SlotEditForm form = new SlotEditForm();
        form.setPlace(slot.getPlace());
        form.setEndTime(slot.getEndsAt().atZone(zone).toLocalTime());
        return form;
    }

    public SlotPhase phaseOf(Slot slot) {
        return slot.phase(clock.instant());
    }
}
