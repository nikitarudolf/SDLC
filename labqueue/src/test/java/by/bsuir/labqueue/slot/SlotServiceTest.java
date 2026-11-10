package by.bsuir.labqueue.slot;

import by.bsuir.labqueue.Fixtures;
import by.bsuir.labqueue.booking.Booking;
import by.bsuir.labqueue.booking.BookingRepository;
import by.bsuir.labqueue.catalog.Discipline;
import by.bsuir.labqueue.catalog.StudyGroup;
import by.bsuir.labqueue.common.ValidationFailedException;
import by.bsuir.labqueue.journal.JournalRepository;
import by.bsuir.labqueue.notification.LoggingMailGateway;
import by.bsuir.labqueue.queue.BookingStatus;
import by.bsuir.labqueue.queue.RuleViolation;
import by.bsuir.labqueue.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F04: создание, изменение и отмена слота на настоящей схеме (Flyway + H2). */
@SpringBootTest
@ActiveProfiles("test")
@Import(Fixtures.class)
class SlotServiceTest {

    @Autowired
    SlotService slots;
    @Autowired
    SlotRepository slotRepository;
    @Autowired
    BookingRepository bookings;
    @Autowired
    JournalRepository journal;
    @Autowired
    LoggingMailGateway mail;
    @Autowired
    Fixtures fx;
    @Autowired
    ZoneId zone;

    StudyGroup group;
    User teacher;
    Discipline discipline;
    LocalDate day;

    @BeforeEach
    void setUp() {
        group = fx.group();
        teacher = fx.teacher();
        discipline = fx.discipline(teacher, group);
        day = LocalDate.now(zone).plusDays(3);
    }

    @Test
    void capacityIsCalculated() {
        Slot slot = slots.create(teacher, Fixtures.slotForm(discipline, group, day, "10:00", "11:30", 10));
        assertThat(slot.getCapacity()).isEqualTo(9);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.PLANNED);
        assertThat(journal.findByActionOrderByIdDesc("SLOT_CREATED")).isNotEmpty();
    }

    @Test
    void overlappingSlotRejected() {
        slots.create(teacher, Fixtures.slotForm(discipline, group, day, "10:00", "11:30", 10));
        assertThatThrownBy(() -> slots.create(teacher,
                Fixtures.slotForm(discipline, group, day, "11:00", "12:00", 10)))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("Пересекается");
        // стык слотов пересечением не считается
        assertThat(slots.create(teacher, Fixtures.slotForm(discipline, group, day, "11:30", "12:30", 10)))
                .isNotNull();
    }

    @Test
    void pastAndInvertedSlotsRejected() {
        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        assertThatThrownBy(() -> slots.create(teacher,
                Fixtures.slotForm(discipline, group, yesterday, "10:00", "11:00", 10)))
                .isInstanceOf(ValidationFailedException.class);
        ValidationFailedException e = org.junit.jupiter.api.Assertions.assertThrows(ValidationFailedException.class,
                () -> slots.create(teacher, Fixtures.slotForm(discipline, group, day, "12:00", "11:00", 10)));
        assertThat(e.violations()).extracting(RuleViolation::field).contains("endTime");
    }

    @Test
    void foreignDisciplineAndGroupRejected() {
        User other = fx.teacher();
        assertThatThrownBy(() -> slots.create(other, Fixtures.slotForm(discipline, group, day, "10:00", "11:00", 10)))
                .isInstanceOf(AccessDeniedException.class);
        StudyGroup notAttached = fx.group();
        assertThatThrownBy(() -> slots.create(teacher,
                Fixtures.slotForm(discipline, notAttached, day, "10:00", "11:00", 10)))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void cannotShrinkBelowBookedButCanExtend() {
        Slot slot = slots.create(teacher, Fixtures.slotForm(discipline, group, day, "10:00", "11:30", 10));
        addBookings(slot, 8);

        SlotEditForm shrink = new SlotEditForm();
        shrink.setPlace("ауд. 412-4");
        shrink.setEndTime(LocalTime.parse("11:00"));
        assertThatThrownBy(() -> slots.update(teacher, slot.getId(), shrink))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("записано 8");

        SlotEditForm extend = new SlotEditForm();
        extend.setPlace("ауд. 412-4");
        extend.setEndTime(LocalTime.parse("12:00"));
        Slot updated = slots.update(teacher, slot.getId(), extend);
        assertThat(updated.getCapacity()).isEqualTo(12);
        assertThat(updated.getPlace()).isEqualTo("ауд. 412-4");
    }

    @Test
    void cancelCancelsBookingsAndNotifiesStudents() {
        Slot slot = slots.create(teacher, Fixtures.slotForm(discipline, group, day, "14:00", "15:00", 10));
        List<User> students = addBookings(slot, 2);

        assertThatThrownBy(() -> slots.cancel(teacher, slot.getId(), "  "))
                .isInstanceOf(ValidationFailedException.class);
        slots.cancel(teacher, slot.getId(), "Болезнь преподавателя");

        Slot cancelled = slotRepository.findById(slot.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(SlotStatus.CANCELLED);
        assertThat(bookings.findBySlotIdAndStatusIn(slot.getId(), BookingStatus.active())).isEmpty();
        for (User s : students) {
            assertThat(mail.sentTo(s.getEmail()))
                    .anySatisfy(m -> assertThat(m.body()).contains("Болезнь преподавателя"));
        }
        // повторная отмена невозможна
        assertThatThrownBy(() -> slots.cancel(teacher, slot.getId(), "ещё раз"))
                .hasMessageContaining("отменён");
    }

    @Test
    void studentSeesOnlyOwnGroupSlots() {
        StudyGroup otherGroup = fx.group();
        Discipline otherDiscipline = fx.discipline(teacher, otherGroup);
        slots.create(teacher, Fixtures.slotForm(discipline, group, day, "16:00", "17:00", 10));
        slots.create(teacher, Fixtures.slotForm(otherDiscipline, otherGroup, day, "17:00", "18:00", 10));
        User student = fx.student(group);

        List<StudentSlotRow> rows = slots.studentWeek(student, day, null);
        assertThat(rows).extracting(StudentSlotRow::discipline).containsOnly(discipline.getName());
        assertThat(rows).allSatisfy(r -> assertThat(r.freeSeats()).isEqualTo(r.capacity()));
    }

    private List<User> addBookings(Slot slot, int count) {
        List<User> students = new ArrayList<>();
        Slot managed = slotRepository.findById(slot.getId()).orElseThrow();
        for (int i = 1; i <= count; i++) {
            User s = fx.student(group);
            students.add(s);
            bookings.save(new Booking(managed, s, 1, i, Instant.now(), Instant.now()));
        }
        return students;
    }
}
