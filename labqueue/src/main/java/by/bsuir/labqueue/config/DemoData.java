package by.bsuir.labqueue.config;

import by.bsuir.labqueue.catalog.CatalogService;
import by.bsuir.labqueue.catalog.Discipline;
import by.bsuir.labqueue.catalog.DisciplineForm;
import by.bsuir.labqueue.catalog.StudyGroup;
import by.bsuir.labqueue.catalog.StudyGroupRepository;
import by.bsuir.labqueue.security.User;
import by.bsuir.labqueue.security.UserRepository;
import by.bsuir.labqueue.slot.Slot;
import by.bsuir.labqueue.slot.SlotForm;
import by.bsuir.labqueue.slot.SlotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Демонстрационные данные для локального запуска (профиль local): две группы, преподаватель,
 * два студента, дисциплины и несколько слотов. В рабочей конфигурации не включается.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "labqueue.demo.enabled", havingValue = "true")
public class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final StudyGroupRepository groups;
    private final UserRepository users;
    private final CatalogService catalog;
    private final SlotService slots;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final ZoneId zone;
    private final String password;

    public DemoData(StudyGroupRepository groups, UserRepository users, CatalogService catalog, SlotService slots,
                    PasswordEncoder encoder, Clock clock, ZoneId appZone,
                    @Value("${labqueue.demo.password}") String password) {
        this.groups = groups;
        this.users = users;
        this.catalog = catalog;
        this.slots = slots;
        this.encoder = encoder;
        this.clock = clock;
        this.zone = appZone;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (groups.count() > 0) {
            return;
        }
        StudyGroup g1 = groups.save(new StudyGroup("450501"));
        StudyGroup g2 = groups.save(new StudyGroup("450502"));
        User teacher = verified(User.teacher("teacher@labqueue.local", "Петрова Анна Сергеевна", clock.instant()));
        verified(User.student("student@labqueue.local", "Иванов Иван Иванович", g1, clock.instant()));
        verified(User.student("student2@labqueue.local", "Сидорова Мария Олеговна", g2, clock.instant()));

        Discipline sdlc = catalog.createDiscipline(teacher,
                discipline("Жизненный цикл разработки ПО", 8, List.of(g1.getId(), g2.getId())));
        Discipline oop = catalog.createDiscipline(teacher,
                discipline("Программирование на языках высокого уровня", 6, List.of(g1.getId())));

        LocalDate today = LocalDate.now(clock.withZone(zone));
        slot(teacher, sdlc, List.of(g1.getId(), g2.getId()), today.plusDays(1), "10:00", "11:30", "ауд. 505-5", 10);
        slot(teacher, sdlc, List.of(g1.getId()), today.plusDays(2), "13:00", "13:40", "ауд. 505-5", 20);
        slot(teacher, oop, List.of(g1.getId()), today.plusDays(2), "15:00", "16:20", "https://meet.example/oop", 10);
        Slot cancelled = slot(teacher, sdlc, List.of(g1.getId()), today.plusDays(3), "08:00", "09:00",
                "ауд. 412-4", 15);
        slots.cancel(teacher, cancelled.getId(), "Преподаватель на конференции");
        slot(teacher, sdlc, List.of(g1.getId(), g2.getId()), today.plusDays(8), "10:00", "11:30", "ауд. 505-5", 10);
        log.info("Демонстрационные данные созданы: teacher@labqueue.local, student@labqueue.local, "
                + "admin@labqueue.local (пароли — в application-local.yml)");
    }

    private User verified(User user) {
        user.setPasswordHash(encoder.encode(password));
        user.confirmEmail();
        return users.save(user);
    }

    private static DisciplineForm discipline(String name, int labs, List<Long> groupIds) {
        DisciplineForm form = new DisciplineForm();
        form.setName(name);
        form.setLabCount(labs);
        form.setGroupIds(groupIds);
        return form;
    }

    private Slot slot(User teacher, Discipline discipline, List<Long> groupIds, LocalDate date, String from,
                      String to, String place, int defense) {
        SlotForm form = new SlotForm();
        form.setDisciplineId(discipline.getId());
        form.setGroupIds(groupIds);
        form.setDate(date);
        form.setStartTime(LocalTime.parse(from));
        form.setEndTime(LocalTime.parse(to));
        form.setPlace(place);
        form.setDefenseMinutes(defense);
        return slots.create(teacher, form);
    }
}
