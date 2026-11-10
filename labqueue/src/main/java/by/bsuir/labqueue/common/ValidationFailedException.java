package by.bsuir.labqueue.common;

import by.bsuir.labqueue.queue.RuleViolation;

import java.util.List;

/** Нарушены правила предметной области; сообщения показываются у полей формы. */
public class ValidationFailedException extends RuntimeException {

    private final List<RuleViolation> violations;

    public ValidationFailedException(List<RuleViolation> violations) {
        super(violations.isEmpty() ? "Ошибка проверки" : violations.get(0).message());
        this.violations = List.copyOf(violations);
    }

    public ValidationFailedException(String field, String message) {
        this(List.of(new RuleViolation(field, message)));
    }

    public List<RuleViolation> violations() {
        return violations;
    }
}
