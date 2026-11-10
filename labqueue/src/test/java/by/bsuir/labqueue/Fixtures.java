package by.bsuir.labqueue;

import by.bsuir.labqueue.catalog.CatalogService;
import by.bsuir.labqueue.catalog.Discipline;
import by.bsuir.labqueue.catalog.DisciplineForm;
import by.bsuir.labqueue.catalog.StudyGroup;
import by.bsuir.labqueue.catalog.StudyGroupRepository;
import by.bsuir.labqueue.security.User;
import by.bsuir.labqueue.security.UserRepository;
import by.bsuir.labqueue.slot.SlotForm;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Тестовые данные с уникальными e-mail и номерами групп: тесты делят одну базу H2. */
@TestComponent
public class Fixtures {

    public static final String PASSWORD = "secret123";
    private static final AtomicInteger SEQ = new AtomicInteger(100);

    private final StudyGroupRepository groups;
    private final UserRepository users;
    private final CatalogService catalog;
    private final PasswordEncoder encoder;

    public Fixtures(StudyGroupRepository groups, UserRepository users, CatalogService catalog,
                    PasswordEncoder encoder) {
        this.groups = groups;
        this.users = users;
        this.catalog = catalog;
        this.encoder = encoder;
    }

    public static int next() {
        return SEQ.incrementAndGet();
    }

    public StudyGroup group() {
        return groups.save(new StudyGroup(String.valueOf(900000 + next())));
    }

    public User teacher() {
        return active(User.teacher("teacher" + next() + "@test.local", "Петрова Анна Сергеевна", Instant.now()));
    }

    public User student(StudyGroup group) {
        return active(User.student("student" + next() + "@test.local", "Иванов Иван Иванович", group, Instant.now()));
    }

    private User active(User user) {
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.confirmEmail();
        return users.save(user);
    }

    @Transactional
    public Discipline discipline(User teacher, StudyGroup... groupList) {
        DisciplineForm form = new DisciplineForm();
        form.setName("Дисциплина " + next());
        form.setLabCount(8);
        form.setGroupIds(List.of(groupList).stream().map(StudyGroup::getId).toList());
        return catalog.createDiscipline(teacher, form);
    }

    public static SlotForm slotForm(Discipline discipline, StudyGroup group, LocalDate date, String from, String to,
                                    int defense) {
        SlotForm form = new SlotForm();
        form.setDisciplineId(discipline.getId());
        form.setGroupIds(List.of(group.getId()));
        form.setDate(date);
        form.setStartTime(LocalTime.parse(from));
        form.setEndTime(LocalTime.parse(to));
        form.setPlace("ауд. 505-5");
        form.setDefenseMinutes(defense);
        return form;
    }
}
