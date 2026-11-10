package by.bsuir.labqueue.web;

import by.bsuir.labqueue.common.ValidationFailedException;
import by.bsuir.labqueue.queue.RuleViolation;
import org.springframework.validation.BindingResult;

/** Перенос нарушений правил предметной области в ошибки полей формы. */
final class Forms {

    private Forms() {
    }

    static void reject(BindingResult binding, ValidationFailedException e) {
        for (RuleViolation v : e.violations()) {
            if (binding.getTarget() != null && hasField(binding, v.field())) {
                binding.rejectValue(v.field(), "rule", v.message());
            } else {
                binding.reject("rule", v.message());
            }
        }
    }

    private static boolean hasField(BindingResult binding, String field) {
        try {
            binding.getFieldType(field);
            return binding.getFieldType(field) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
