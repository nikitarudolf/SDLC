package by.bsuir.labqueue.web;

import by.bsuir.labqueue.catalog.Discipline;
import by.bsuir.labqueue.catalog.DisciplineRepository;
import by.bsuir.labqueue.security.CurrentUser;
import by.bsuir.labqueue.security.User;
import by.bsuir.labqueue.slot.SlotService;
import by.bsuir.labqueue.slot.StudentSlotRow;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Экран слотов студента: неделя и фильтр по дисциплине (REQ-FUNC-006). */
@Controller
public class StudentController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SlotService slots;
    private final CurrentUser currentUser;
    private final DisciplineRepository disciplines;

    public StudentController(SlotService slots, CurrentUser currentUser, DisciplineRepository disciplines) {
        this.slots = slots;
        this.currentUser = currentUser;
        this.disciplines = disciplines;
    }

    @GetMapping("/slots")
    public String week(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       @RequestParam(required = false) Long discipline,
                       Principal principal, Model model) {
        User student = currentUser.get(principal);
        LocalDate monday = slots.weekStart(week == null ? slots.today() : week);
        List<StudentSlotRow> rows = slots.studentWeek(student, monday, discipline);
        model.addAttribute("student", student);
        model.addAttribute("rows", rows);
        model.addAttribute("monday", monday);
        model.addAttribute("weekLabel", monday.format(DAY) + " – " + monday.plusDays(6).format(DAY));
        model.addAttribute("prevWeek", monday.minusWeeks(1));
        model.addAttribute("nextWeek", monday.plusWeeks(1));
        model.addAttribute("discipline", discipline);
        model.addAttribute("disciplines", disciplinesOfGroup(student));
        return "student/slots";
    }

    private Map<Long, String> disciplinesOfGroup(User student) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (student.getGroup() == null) {
            return result;
        }
        Long groupId = student.getGroup().getId();
        disciplines.findAll().stream()
                .filter(d -> d.getGroups().stream().anyMatch(g -> g.getId().equals(groupId)))
                .sorted(Comparator.comparing(Discipline::getName))
                .forEach(d -> result.put(d.getId(), d.getName()));
        return result;
    }
}
