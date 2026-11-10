package by.bsuir.labqueue.queue;

import java.time.Instant;

/** Запись слота в том виде, который нужен для расчётов очереди. */
public record QueueItem(long bookingId, int position, BookingStatus status, Instant calledAt) {
}
