package by.bsuir.labqueue.web;

import by.bsuir.labqueue.Fixtures;
import by.bsuir.labqueue.catalog.StudyGroup;
import by.bsuir.labqueue.notification.LoggingMailGateway;
import by.bsuir.labqueue.security.User;
import by.bsuir.labqueue.security.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Сквозные сценарии через HTTP: UC-01, UC-02, права доступа и CSRF (REQ-FUNC-001, REQ-SEC-001…004). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Fixtures.class)
class WebFlowTest {

    private static final Pattern LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mvc;
    @Autowired
    LoggingMailGateway mail;
    @Autowired
    UserRepository users;
    @Autowired
    Fixtures fx;

    @Test
    void registerVerifyAndLogin() throws Exception {
        StudyGroup group = fx.group();
        String email = "new" + Fixtures.next() + "@test.local";

        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Новиков Олег Петрович")
                        .param("email", email)
                        .param("password", "labqueue2026")
                        .param("groupId", group.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Подтвердите e-mail")));

        User saved = users.findByEmail(email).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2a$12$");
        assertThat(saved.getPasswordHash()).doesNotContain("labqueue2026");

        // без подтверждения e-mail вход невозможен
        mvc.perform(formLogin("/login").userParameter("email").user(email).password("labqueue2026"))
                .andExpect(redirectedUrl("/login?unverified"));

        String token = lastToken(email);
        mvc.perform(get("/verify").param("token", token))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("E-mail подтверждён")));

        // после входа студент попадает на экран слотов
        mvc.perform(formLogin("/login").userParameter("email").user(email).password("labqueue2026"))
                .andExpect(redirectedUrl("/slots"));
    }

    @Test
    void weakPasswordAndDuplicateEmailRejected() throws Exception {
        StudyGroup group = fx.group();
        User existing = fx.student(group);
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Тест Тест")
                        .param("email", "weak" + Fixtures.next() + "@test.local")
                        .param("password", "abcdefgh")
                        .param("groupId", group.getId().toString()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("буквы и цифры")));
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Тест Тест")
                        .param("email", existing.getEmail())
                        .param("password", "abcdefgh1")
                        .param("groupId", group.getId().toString()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("уже зарегистрирован")));
    }

    @Test
    void wrongPasswordMessageIsGenericAndSixthAttemptLocked() throws Exception {
        User teacher = fx.teacher();
        // неизвестный e-mail и неверный пароль дают одинаковый результат
        mvc.perform(formLogin("/login").userParameter("email").user("nobody@test.local").password("x"))
                .andExpect(redirectedUrl("/login?error"));
        for (int i = 0; i < 5; i++) {
            mvc.perform(formLogin("/login").userParameter("email").user(teacher.getEmail()).password("wrong"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        // шестая попытка отклоняется даже с верным паролем
        mvc.perform(formLogin("/login").userParameter("email").user(teacher.getEmail()).password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/login?locked"));
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Неверный e-mail или пароль")));
    }

    @Test
    void teacherLandsOnSchedule() throws Exception {
        User teacher = fx.teacher();
        mvc.perform(formLogin("/login").userParameter("email").user(teacher.getEmail()).password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/teacher/slots"));
    }

    @Test
    void rolesAreCheckedOnServer() throws Exception {
        mvc.perform(get("/teacher/slots").with(user("s@test.local").roles("STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/groups").with(user("t@test.local").roles("TEACHER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/slots"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void postWithoutCsrfRejected() throws Exception {
        mvc.perform(post("/admin/groups").with(user("admin@test.local").roles("ADMIN")).param("number", "123456"))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentPagesRender() throws Exception {
        User student = fx.student(fx.group());
        mvc.perform(get("/slots").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Слоты защиты")));
    }

    @Test
    void teacherAndAdminPagesRender() throws Exception {
        StudyGroup group = fx.group();
        User teacher = fx.teacher();
        var discipline = fx.discipline(teacher, group);
        var slot = slotService.create(teacher, Fixtures.slotForm(discipline, group,
                java.time.LocalDate.now().plusDays(4), "10:00", "11:30", 10));
        var asTeacher = user(teacher.getEmail()).roles("TEACHER");
        mvc.perform(get("/teacher/slots").with(asTeacher))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("0 / 9")));
        mvc.perform(get("/teacher/slots/new").with(asTeacher))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(group.getNumber())));
        mvc.perform(get("/teacher/slots/{id}/edit", slot.getId()).with(asTeacher))
                .andExpect(status().isOk());
        mvc.perform(get("/teacher/disciplines").with(asTeacher))
                .andExpect(status().isOk());
        // чужой слот недоступен
        mvc.perform(get("/teacher/slots/{id}/edit", slot.getId())
                        .with(user(fx.teacher().getEmail()).roles("TEACHER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/groups").with(user("admin@test.local").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/teachers").with(user("admin@test.local").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void slotFormShowsFieldErrors() throws Exception {
        StudyGroup group = fx.group();
        User teacher = fx.teacher();
        var discipline = fx.discipline(teacher, group);
        mvc.perform(post("/teacher/slots").with(csrf()).with(user(teacher.getEmail()).roles("TEACHER"))
                        .param("disciplineId", discipline.getId().toString())
                        .param("groupIds", group.getId().toString())
                        .param("date", java.time.LocalDate.now().plusDays(2).toString())
                        .param("startTime", "12:00")
                        .param("endTime", "12:10")
                        .param("place", "ауд. 505-5")
                        .param("defenseMinutes", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("не меньше 15 минут")));
    }

    @Autowired
    by.bsuir.labqueue.slot.SlotService slotService;

    @Test
    void adminImportsGroupAndStudentAcceptsInvite() throws Exception {
        StudyGroup group = fx.group();
        String email = "imported" + Fixtures.next() + "@test.local";
        String csv = "ФИО;e-mail\nКозлов Артём Ильич;" + email + "\nбез почты\n";
        mvc.perform(multipart("/admin/groups/{id}/import", group.getId())
                        .file(new MockMultipartFile("file", "group.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .with(csrf()).with(user("admin@test.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Строка 3")));

        mvc.perform(multipart("/admin/groups/{id}/import", group.getId())
                        .file(new MockMultipartFile("file", "group.txt", "text/plain", "x".getBytes()))
                        .with(csrf()).with(user("admin@test.local").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        String token = lastToken(email);
        mvc.perform(post("/invite").with(csrf())
                        .param("token", token)
                        .param("password", "student2026")
                        .param("passwordRepeat", "student2026"))
                .andExpect(redirectedUrl("/login?activated"));
        mvc.perform(formLogin("/login").userParameter("email").user(email).password("student2026"))
                .andExpect(redirectedUrl("/slots"));
    }

    @Test
    void adminBootstrapCreatesAdmin() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user("admin@test.local").password("admin12345"))
                .andExpect(redirectedUrl("/admin/groups"));
    }

    private String lastToken(String email) {
        String body = mail.sentTo(email).getLast().body();
        Matcher m = LINK.matcher(body);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
