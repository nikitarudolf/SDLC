package by.bsuir.labqueue.queue;

import java.time.Instant;

/** Элемент листа ожидания: порядок определяется временем постановки. */
public record WaitlistItem(long bookingId, Instant createdAt) {
}
