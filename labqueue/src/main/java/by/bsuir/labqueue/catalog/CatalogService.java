package by.bsuir.labqueue.catalog;

import by.bsuir.labqueue.common.ValidationFailedException;
import by.bsuir.labqueue.journal.Journal;
import by.bsuir.labqueue.security.AccountService;
import by.bsuir.labqueue.security.Role;
import by.bsuir.labqueue.security.User;
import by.bsuir.labqueue.security.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Справочник групп и дисциплин, импорт списка группы (F03). */
@Service
public class CatalogService {

    public static final long MAX_IMPORT_BYTES = 1024 * 1024;

    private final StudyGroupRepository groups;
    private final DisciplineRepository disciplines;
    private final UserRepository users;
    private final AccountService accounts;
    private final Journal journal;

    public CatalogService(StudyGroupRepository groups, DisciplineRepository disciplines, UserRepository users,
                          AccountService accounts, Journal journal) {
        this.groups = groups;
        this.disciplines = disciplines;
        this.users = users;
        this.accounts = accounts;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public List<StudyGroup> allGroups() {
        return groups.findAllByOrderByNumber();
    }

    @Transactional
    public StudyGroup createGroup(String number, String admin) {
        String value = number == null ? "" : number.trim();
        if (!value.matches("\\d{6}")) {
            throw new ValidationFailedException("number", "Номер группы — шесть цифр, например 450501");
        }
        if (groups.existsByNumber(value)) {
            throw new ValidationFailedException("number", "Группа " + value + " уже есть");
        }
        StudyGroup group = groups.save(new StudyGroup(value));
        journal.record(admin, "GROUP_CREATED", value);
        return group;
    }

    /**
     * Импорт списка студентов группы из .csv (не больше 1 МБ). Новые студенты получают
     * приглашение задать пароль; уже зарегистрированные e-mail пропускаются.
     */
    @Transactional
    public ImportResult importStudents(Long groupId, String fileName, byte[] content, String admin) {
        StudyGroup group = groups.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена"));
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ValidationFailedException("file", "Нужен файл в формате .csv");
        }
        if (content.length == 0) {
            throw new ValidationFailedException("file", "Файл пустой");
        }
        if (content.length > MAX_IMPORT_BYTES) {
            throw new ValidationFailedException("file", "Файл больше 1 МБ");
        }
        StudentCsvParser.Result parsed = StudentCsvParser.parse(new String(content, StandardCharsets.UTF_8));
        List<String> skipped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int created = 0;
        for (StudentCsvParser.Row row : parsed.rows()) {
            String email = User.normalizeEmail(row.email());
            if (!seen.add(email)) {
                skipped.add(row.fullName() + " — e-mail повторяется в файле");
            } else if (users.existsByEmail(email)) {
                skipped.add(row.fullName() + " — " + email + " уже зарегистрирован");
            } else {
                accounts.inviteStudent(row.fullName(), email, group);
                created++;
            }
        }
        journal.record(admin, "GROUP_IMPORTED",
                group.getNumber() + ": создано " + created + ", пропущено " + skipped.size()
                        + ", ошибок " + parsed.errors().size());
        return new ImportResult(group.getNumber(), created, skipped, parsed.errors());
    }

    @Transactional(readOnly = true)
    public List<Discipline> disciplinesOf(User teacher) {
        return disciplines.findByTeacherIdOrderByName(teacher.getId());
    }

    @Transactional
    public Discipline createDiscipline(User teacher, DisciplineForm form) {
        if (teacher.getRole() != Role.TEACHER) {
            throw new ValidationFailedException("name", "Дисциплину создаёт преподаватель");
        }
        int labs = form.getLabCount() == null ? 0 : form.getLabCount();
        if (labs < Discipline.MIN_LABS || labs > Discipline.MAX_LABS) {
            throw new ValidationFailedException("labCount", "Число ЛР — от 1 до 15");
        }
        Set<StudyGroup> selected = new LinkedHashSet<>(groups.findAllById(form.getGroupIds()));
        if (selected.isEmpty() || selected.size() != new HashSet<>(form.getGroupIds()).size()) {
            throw new ValidationFailedException("groupIds", "Выберите существующие группы");
        }
        Discipline discipline = disciplines.save(new Discipline(form.getName(), labs, teacher, selected));
        journal.record(teacher.getEmail(), "DISCIPLINE_CREATED",
                discipline.getName() + " (" + labs + " ЛР)");
        return discipline;
    }
}
