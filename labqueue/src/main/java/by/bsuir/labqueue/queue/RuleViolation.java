package by.bsuir.labqueue.queue;

/** Нарушенное правило с привязкой к полю формы, у которого показывается сообщение. */
public record RuleViolation(String field, String message) {
}
