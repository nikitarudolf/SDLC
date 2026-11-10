package by.bsuir.labqueue.web;

import by.bsuir.labqueue.catalog.CatalogService;
import by.bsuir.labqueue.catalog.DisciplineForm;
import by.bsuir.labqueue.common.ValidationFailedException;
import by.bsuir.labqueue.security.CurrentUser;
import by.bsuir.labqueue.security.User;
import by.bsuir.labqueue.slot.Slot;
import by.bsuir.labqueue.slot.SlotEditForm;
import by.bsuir.labqueue.slot.SlotForm;
import by.bsuir.labqueue.slot.SlotService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import by.bsuir.labqueue.catalog.Discipline;
import by.bsuir.labqueue.catalog.StudyGroup;

import java.security.Principal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Расписание преподавателя, дисциплины, создание, изменение и отмена слотов (UC-07, UC-08). */
@Controller
@RequestMapping("/teacher")
public class TeacherController {

    private final SlotService slots;
    private final CatalogService catalog;
    private final CurrentUser currentUser;

    public TeacherController(SlotService slots, CatalogService catalog, CurrentUser currentUser) {
        this.slots = slots;
        this.catalog = catalog;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------ слоты

    @GetMapping("/slots")
    public String schedule(Principal principal, Model model) {
        User teacher = currentUser.get(principal);
        model.addAttribute("teacher", teacher);
        model.addAttribute("rows", slots.teacherSchedule(teacher));
        model.addAttribute("hasDisciplines", !catalog.disciplinesOf(teacher).isEmpty());
        return "teacher/slots";
    }

    @GetMapping("/slots/new")
    public String newSlot(Principal principal, Model model) {
        User teacher = currentUser.get(principal);
        SlotForm form = new SlotForm();
        form.setDate(slots.today().plusDays(1));
        model.addAttribute("form", form);
        return slotForm(teacher, model);
    }

    @PostMapping("/slots")
    public String createSlot(@Valid @ModelAttribute("form") SlotForm form, BindingResult binding,
                             Principal principal, Model model, RedirectAttributes flash) {
        User teacher = currentUser.get(principal);
        if (!binding.hasErrors()) {
            try {
                Slot slot = slots.create(teacher, form);
                flash.addFlashAttribute("message",
                        "Слот создан, вместимость — " + slot.getCapacity() + " защит");
                return "redirect:/teacher/slots";
            } catch (ValidationFailedException e) {
                Forms.reject(binding, e);
            }
        }
        return slotForm(teacher, model);
    }

    private String slotForm(User teacher, Model model) {
        List<Discipline> disciplines = catalog.disciplinesOf(teacher);
        Map<Long, StudyGroup> groups = new LinkedHashMap<>();
        disciplines.stream()
                .flatMap(d -> d.sortedGroups().stream())
                .sorted(Comparator.comparing(StudyGroup::getNumber))
                .forEach(g -> groups.putIfAbsent(g.getId(), g));
        model.addAttribute("disciplines", disciplines);
        model.addAttribute("groups", groups.values());
        return "teacher/slot-form";
    }

    @GetMapping("/slots/{id}/edit")
    public String editSlot(@PathVariable Long id, Principal principal, Model model) {
        User teacher = currentUser.get(principal);
        Slot slot = slots.ownSlot(teacher, id);
        model.addAttribute("slot", slot);
        model.addAttribute("timeRange", slots.timeRange(slot));
        model.addAttribute("form", slots.editFormFor(slot));
        return "teacher/slot-edit";
    }

    @PostMapping("/slots/{id}")
    public String updateSlot(@PathVariable Long id, @Valid @ModelAttribute("form") SlotEditForm form,
                             BindingResult binding, Principal principal, Model model, RedirectAttributes flash) {
        User teacher = currentUser.get(principal);
        if (!binding.hasErrors()) {
            try {
                Slot slot = slots.update(teacher, id, form);
                flash.addFlashAttribute("message", "Слот изменён, вместимость — " + slot.getCapacity());
                return "redirect:/teacher/slots";
            } catch (ValidationFailedException e) {
                Forms.reject(binding, e);
            }
        }
        Slot slot = slots.ownSlot(teacher, id);
        model.addAttribute("slot", slot);
        model.addAttribute("timeRange", slots.timeRange(slot));
        return "teacher/slot-edit";
    }

    @PostMapping("/slots/{id}/cancel")
    public String cancelSlot(@PathVariable Long id, @RequestParam(defaultValue = "") String reason,
                             Principal principal, RedirectAttributes flash) {
        User teacher = currentUser.get(principal);
        try {
            slots.cancel(teacher, id, reason);
            flash.addFlashAttribute("message", "Слот отменён, студенты уведомлены");
            return "redirect:/teacher/slots";
        } catch (ValidationFailedException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/teacher/slots/" + id + "/edit";
        }
    }

    // ------------------------------------------------------------ дисциплины

    @GetMapping("/disciplines")
    public String disciplines(Principal principal, Model model) {
        User teacher = currentUser.get(principal);
        model.addAttribute("form", new DisciplineForm());
        return disciplinesPage(teacher, model);
    }

    @PostMapping("/disciplines")
    public String createDiscipline(@Valid @ModelAttribute("form") DisciplineForm form, BindingResult binding,
                                   Principal principal, Model model, RedirectAttributes flash) {
        User teacher = currentUser.get(principal);
        if (!binding.hasErrors()) {
            try {
                catalog.createDiscipline(teacher, form);
                flash.addFlashAttribute("message", "Дисциплина «" + form.getName().trim() + "» создана");
                return "redirect:/teacher/disciplines";
            } catch (ValidationFailedException e) {
                Forms.reject(binding, e);
            }
        }
        return disciplinesPage(teacher, model);
    }

    private String disciplinesPage(User teacher, Model model) {
        model.addAttribute("disciplines", catalog.disciplinesOf(teacher));
        model.addAttribute("groups", catalog.allGroups());
        return "teacher/disciplines";
    }
}
