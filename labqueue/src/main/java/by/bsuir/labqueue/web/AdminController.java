package by.bsuir.labqueue.web;

import by.bsuir.labqueue.catalog.CatalogService;
import by.bsuir.labqueue.catalog.ImportResult;
import by.bsuir.labqueue.catalog.StudyGroup;
import by.bsuir.labqueue.common.ValidationFailedException;
import by.bsuir.labqueue.security.AccountService;
import by.bsuir.labqueue.security.Role;
import by.bsuir.labqueue.security.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Справочник групп, импорт списка группы и приглашение преподавателей (F02, F03). */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final CatalogService catalog;
    private final AccountService accounts;
    private final UserRepository users;

    public AdminController(CatalogService catalog, AccountService accounts, UserRepository users) {
        this.catalog = catalog;
        this.accounts = accounts;
        this.users = users;
    }

    @GetMapping("/groups")
    public String groups(Model model) {
        return groupsPage(model);
    }

    private String groupsPage(Model model) {
        List<StudyGroup> groups = catalog.allGroups();
        Map<Long, Integer> sizes = new LinkedHashMap<>();
        for (StudyGroup g : groups) {
            sizes.put(g.getId(), users.findByGroupIdOrderByFullName(g.getId()).size());
        }
        model.addAttribute("groups", groups);
        model.addAttribute("sizes", sizes);
        return "admin/groups";
    }

    @PostMapping("/groups")
    public String createGroup(@RequestParam(defaultValue = "") String number, Principal principal,
                              RedirectAttributes flash) {
        try {
            StudyGroup group = catalog.createGroup(number, principal.getName());
            flash.addFlashAttribute("message", "Группа " + group.getNumber() + " добавлена");
        } catch (ValidationFailedException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/groups/{id}/import")
    public String importStudents(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                 Principal principal, Model model, HttpServletResponse response) throws IOException {
        try {
            ImportResult result = catalog.importStudents(id, file.getOriginalFilename(), file.getBytes(),
                    principal.getName());
            model.addAttribute("result", result);
        } catch (ValidationFailedException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", e.getMessage());
        }
        return groupsPage(model);
    }

    @GetMapping("/teachers")
    public String teachers(Model model) {
        model.addAttribute("teachers", users.findByRoleOrderByFullName(Role.TEACHER));
        return "admin/teachers";
    }

    @PostMapping("/teachers")
    public String inviteTeacher(@RequestParam(defaultValue = "") String fullName,
                                @RequestParam(defaultValue = "") String email,
                                Principal principal, RedirectAttributes flash) {
        if (fullName.isBlank() || !email.contains("@")) {
            flash.addFlashAttribute("error", "Укажите ФИО и корректный e-mail");
            return "redirect:/admin/teachers";
        }
        try {
            accounts.inviteTeacher(fullName, email, principal.getName());
            flash.addFlashAttribute("message", "Приглашение отправлено на " + email.trim());
        } catch (ValidationFailedException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/teachers";
    }
}
