package by.bsuir.labqueue.slot;

/** Строка списка слотов для студента (REQ-FUNC-006). */
public record StudentSlotRow(Long id, String day, String time, String discipline, String teacher, String place,
                             int capacity, int freeSeats, int waitlist, String availability, String availabilityCss,
                             String phase, String myStatus, String cancelReason) {
}
