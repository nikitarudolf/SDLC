package by.bsuir.labqueue.slot;

import by.bsuir.labqueue.notification.MailGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Письма об отмене слота отправляются после фиксации транзакции (Observer, представление SDD 011). */
@Component
public class SlotNotifications {

    private final MailGateway mail;

    public SlotNotifications(MailGateway mail) {
        this.mail = mail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlotCancelled(SlotCancelledEvent event) {
        for (String email : event.studentEmails()) {
            mail.send(email, "Слот защиты отменён",
                    "Слот по дисциплине «" + event.discipline() + "» (" + event.when() + ") отменён.\n"
                            + "Причина: " + event.reason() + "\n"
                            + "Ваша запись отменена, выберите другой слот.");
        }
    }
}
