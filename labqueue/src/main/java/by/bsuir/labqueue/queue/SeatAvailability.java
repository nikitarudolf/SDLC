package by.bsuir.labqueue.queue;

/** Статус свободных мест в слоте для студента (REQ-FUNC-006, критерий 3). */
public enum SeatAvailability {
    FREE("Свободно", "free"),
    FEW("Мало мест", "few"),
    FULL("Заполнен", "full");

    private final String label;
    private final String css;

    SeatAvailability(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String label() {
        return label;
    }

    public String css() {
        return css;
    }

    public static SeatAvailability of(int capacity, int seatsTaken) {
        int free = Math.max(0, capacity - seatsTaken);
        if (free == 0) {
            return FULL;
        }
        return free <= 2 ? FEW : FREE;
    }
}
