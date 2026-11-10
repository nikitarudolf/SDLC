package by.bsuir.labqueue.security;

public enum Role {
    STUDENT("Студент"),
    TEACHER("Преподаватель"),
    ADMIN("Администратор");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
