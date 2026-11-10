package by.bsuir.labqueue.slot;

/** Хранимый статус слота; подсостояния PLANNED вычисляются по времени ({@code SlotPhase}). */
public enum SlotStatus {
    PLANNED, IN_PROGRESS, FINISHED, CANCELLED
}
