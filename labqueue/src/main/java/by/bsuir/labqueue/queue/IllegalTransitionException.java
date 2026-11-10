package by.bsuir.labqueue.queue;

/** Попытка перевести запись в статус, который не разрешён диаграммой состояний или сторожевым условием. */
public class IllegalTransitionException extends RuntimeException {

    private final BookingStatus from;
    private final BookingStatus to;

    public IllegalTransitionException(BookingStatus from, BookingStatus to, String reason) {
        super(reason);
        this.from = from;
        this.to = to;
    }

    public BookingStatus from() {
        return from;
    }

    public BookingStatus to() {
        return to;
    }
}
