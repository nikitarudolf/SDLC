package by.bsuir.labqueue.security;

import by.bsuir.labqueue.catalog.StudyGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Учётная запись: студент, преподаватель или администратор (REQ-FUNC-001, REQ-SEC-001, REQ-SEC-002). */
@Entity
@Table(name = "app_user")
public class User {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private StudyGroup group;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "token", length = 64, unique = true)
    private String token;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    private User(String email, String fullName, Role role, StudyGroup group, Instant now) {
        this.email = normalizeEmail(email);
        this.fullName = fullName.trim();
        this.role = role;
        this.group = group;
        this.createdAt = now;
    }

    public static User student(String email, String fullName, StudyGroup group, Instant now) {
        return new User(email, fullName, Role.STUDENT, group, now);
    }

    public static User teacher(String email, String fullName, Instant now) {
        return new User(email, fullName, Role.TEACHER, null, now);
    }

    public static User admin(String email, String fullName, Instant now) {
        return new User(email, fullName, Role.ADMIN, null, now);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // ----- пароль и подтверждение e-mail

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void issueToken(String token, Instant expiresAt) {
        this.token = token;
        this.tokenExpiresAt = expiresAt;
    }

    public boolean tokenValid(Instant now) {
        return token != null && tokenExpiresAt != null && now.isBefore(tokenExpiresAt);
    }

    /** Ссылка из письма использована: e-mail подтверждён, токен больше не действует. */
    public void confirmEmail() {
        this.emailVerified = true;
        this.token = null;
        this.tokenExpiresAt = null;
    }

    public boolean canLogIn() {
        return emailVerified && passwordHash != null;
    }

    // ----- блокировка после неудачных попыток (REQ-SEC-002)

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** @return true, если после этой попытки учётная запись заблокирована */
    public boolean registerFailedLogin(Instant now) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            lockedUntil = null;
            failedAttempts = 0;
        }
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedAttempts = 0;
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    /** «Фамилия И.» — так студент видит других студентов в очереди (REQ-SEC-003). */
    public String shortName() {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[1].charAt(0) + ".";
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public StudyGroup getGroup() {
        return group;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
