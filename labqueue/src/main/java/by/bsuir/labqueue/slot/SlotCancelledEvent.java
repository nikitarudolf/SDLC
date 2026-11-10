package by.bsuir.labqueue.slot;

import java.util.List;

/** Слот отменён; затронутые студенты получают уведомление с причиной (REQ-FUNC-005). */
public record SlotCancelledEvent(Long slotId, String discipline, String when, String reason,
                                 List<String> studentEmails) {
}
