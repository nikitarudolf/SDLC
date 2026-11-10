package by.bsuir.labqueue.slot;

/** Строка расписания преподавателя. */
public record TeacherSlotRow(Long id, String day, String time, String discipline, String groups, String place,
                             int defenseMinutes, int capacity, int booked, int waitlist, String phase,
                             boolean editable, String cancelReason) {
}
