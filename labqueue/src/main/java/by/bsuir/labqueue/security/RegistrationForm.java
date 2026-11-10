package by.bsuir.labqueue.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Форма регистрации студента. */
public class RegistrationForm {

    @NotBlank(message = "Укажите ФИО")
    @Size(max = 150, message = "Не длиннее 150 символов")
    private String fullName;

    @NotBlank(message = "Укажите e-mail")
    @Email(message = "Некорректный e-mail")
    @Size(max = 254)
    private String email;

    @NotBlank(message = "Укажите пароль")
    private String password;

    @NotNull(message = "Выберите группу")
    private Long groupId;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}
