package by.bsuir.labqueue.security;

import by.bsuir.labqueue.catalog.StudyGroup;
import by.bsuir.labqueue.catalog.StudyGroupRepository;
import by.bsuir.labqueue.common.ValidationFailedException;
import by.bsuir.labqueue.config.LabQueueProperties;
import by.bsuir.labqueue.journal.Journal;
import by.bsuir.labqueue.notification.MailGateway;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Регистрация, подтверждение e-mail, приглашения и учёт неудачных входов (F02). */
@Service
public class AccountService {

    static final Duration VERIFY_TOKEN_TTL = Duration.ofHours(24);
    static final Duration INVITE_TOKEN_TTL = Duration.ofDays(7);

    private final UserRepository users;
    private final StudyGroupRepository groups;
    private final PasswordEncoder passwordEncoder;
    private final MailGateway mail;
    private final Journal journal;
    private final LabQueueProperties properties;
    private final Clock clock;

    public AccountService(UserRepository users, StudyGroupRepository groups, PasswordEncoder passwordEncoder,
                          MailGateway mail, Journal journal, LabQueueProperties properties, Clock clock) {
        this.users = users;
        this.groups = groups;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.journal = journal;
        this.properties = properties;
        this.clock = clock;
    }

    /** Регистрация студента: учётная запись создаётся неподтверждённой, письмо содержит ссылку. */
    @Transactional
    public User registerStudent(RegistrationForm form) {
        String email = User.normalizeEmail(form.getEmail());
        if (users.existsByEmail(email)) {
            throw new ValidationFailedException("email", "Этот e-mail уже зарегистрирован");
        }
        PasswordPolicy.problem(form.getPassword()).ifPresent(p -> {
            throw new ValidationFailedException("password", p);
        });
        StudyGroup group = groups.findById(form.getGroupId())
                .orElseThrow(() -> new ValidationFailedException("groupId", "Группа не найдена"));
        Instant now = clock.instant();
        User user = User.student(email, form.getFullName(), group, now);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        String token = Tokens.next();
        user.issueToken(token, now.plus(VERIFY_TOKEN_TTL));
        users.save(user);
        journal.record(email, "REGISTERED", "группа " + group.getNumber());
        mail.send(email, "Подтвердите e-mail в LabQueue",
                "Здравствуйте, " + user.getFullName() + "!\n"
                        + "Чтобы завершить регистрацию, перейдите по ссылке (действует 24 часа):\n"
                        + properties.baseUrl() + "/verify?token=" + token);
        return user;
    }

    /** Переход по ссылке из письма. */
    @Transactional
    public boolean verifyEmail(String token) {
        Optional<User> found = token == null ? Optional.empty() : users.findByToken(token);
        if (found.isEmpty() || !found.get().tokenValid(clock.instant()) || found.get().getPasswordHash() == null) {
            return false;
        }
        User user = found.get();
        user.confirmEmail();
        journal.record(user.getEmail(), "EMAIL_VERIFIED", null);
        return true;
    }

    /** Учётную запись преподавателя создаёт администратор; пароль задаётся по ссылке-приглашению. */
    @Transactional
    public User inviteTeacher(String fullName, String email, String admin) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new ValidationFailedException("email", "Этот e-mail уже зарегистрирован");
        }
        User user = User.teacher(normalized, fullName, clock.instant());
        sendInvite(user);
        users.save(user);
        journal.record(admin, "TEACHER_INVITED", normalized);
        return user;
    }

    /** Студент из импортированного списка группы получает приглашение задать пароль. */
    @Transactional
    public User inviteStudent(String fullName, String email, StudyGroup group) {
        User user = User.student(email, fullName, group, clock.instant());
        sendInvite(user);
        return users.save(user);
    }

    private void sendInvite(User user) {
        String token = Tokens.next();
        user.issueToken(token, clock.instant().plus(INVITE_TOKEN_TTL));
        mail.send(user.getEmail(), "Приглашение в LabQueue",
                "Здравствуйте, " + user.getFullName() + "!\n"
                        + "Для вас создана учётная запись (" + user.getRole().label().toLowerCase() + ").\n"
                        + "Задайте пароль по ссылке (действует 7 дней):\n"
                        + properties.baseUrl() + "/invite?token=" + token);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByValidInvite(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return users.findByToken(token)
                .filter(u -> u.getPasswordHash() == null)
                .filter(u -> u.tokenValid(clock.instant()));
    }

    /** Пароль по приглашению: e-mail считается подтверждённым, раз ссылка пришла на него. */
    @Transactional
    public void acceptInvite(String token, String password) {
        User user = findByValidInvite(token)
                .orElseThrow(() -> new ValidationFailedException("password", "Ссылка недействительна или устарела"));
        PasswordPolicy.problem(password).ifPresent(p -> {
            throw new ValidationFailedException("password", p);
        });
        user = users.findById(user.getId()).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.confirmEmail();
        journal.record(user.getEmail(), "INVITE_ACCEPTED", null);
    }

    @Transactional
    public void registerFailedLogin(String email) {
        users.findByEmail(User.normalizeEmail(email)).ifPresent(user -> {
            if (user.registerFailedLogin(clock.instant())) {
                journal.record(user.getEmail(), "LOGIN_LOCKED", "5 неудачных попыток, блокировка на 15 минут");
            }
        });
    }

    @Transactional
    public void registerSuccessfulLogin(String email) {
        users.findByEmail(User.normalizeEmail(email)).ifPresent(User::registerSuccessfulLogin);
    }

    /** Администратор из настроек создаётся при первом запуске, если его ещё нет. */
    @Transactional
    public boolean ensureAdmin(String email, String password) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            return false;
        }
        User admin = User.admin(normalized, "Администратор", clock.instant());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.confirmEmail();
        users.save(admin);
        journal.record("system", "ADMIN_CREATED", normalized);
        return true;
    }
}
