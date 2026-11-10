package by.bsuir.labqueue.web;

import by.bsuir.labqueue.catalog.CatalogService;
import by.bsuir.labqueue.common.ValidationFailedException;
import by.bsuir.labqueue.security.AccountService;
import by.bsuir.labqueue.security.RegistrationForm;
import by.bsuir.labqueue.security.SecurityConfig;
import by.bsuir.labqueue.security.User;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/** Вход, регистрация, подтверждение e-mail и пароль по приглашению (UC-01, UC-02). */
@Controller
public class AuthController {

    private final AccountService accounts;
    private final CatalogService catalog;

    public AuthController(AccountService accounts, CatalogService catalog) {
        this.accounts = accounts;
        this.catalog = catalog;
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/login";
        }
        return "redirect:" + SecurityConfig.homeFor(authentication);
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegistrationForm());
        model.addAttribute("groups", catalog.allGroups());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult binding, Model model) {
        if (!binding.hasErrors()) {
            try {
                User user = accounts.registerStudent(form);
                model.addAttribute("email", user.getEmail());
                return "auth/check-email";
            } catch (ValidationFailedException e) {
                Forms.reject(binding, e);
            }
        }
        form.setPassword(null);
        model.addAttribute("groups", catalog.allGroups());
        return "auth/register";
    }

    @GetMapping("/verify")
    public String verify(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("ok", accounts.verifyEmail(token));
        return "auth/verified";
    }

    @GetMapping("/invite")
    public String inviteForm(@RequestParam(required = false) String token, Model model) {
        Optional<User> user = accounts.findByValidInvite(token);
        model.addAttribute("token", token);
        model.addAttribute("invited", user.orElse(null));
        return "auth/invite";
    }

    @PostMapping("/invite")
    public String acceptInvite(@RequestParam String token, @RequestParam String password,
                               @RequestParam String passwordRepeat, Model model) {
        String error = null;
        if (!password.equals(passwordRepeat)) {
            error = "Пароли не совпадают";
        } else {
            try {
                accounts.acceptInvite(token, password);
                return "redirect:/login?activated";
            } catch (ValidationFailedException e) {
                error = e.getMessage();
            }
        }
        model.addAttribute("token", token);
        model.addAttribute("invited", accounts.findByValidInvite(token).orElse(null));
        model.addAttribute("error", error);
        return "auth/invite";
    }
}
