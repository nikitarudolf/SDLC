// SysML v2 — жизненный цикл записи на защиту (диаграмма images/booking-states.png)
package LabQueueBooking {
    private import ScalarValues::*;

    // ---------- сигналы ----------
    attribute def SeatFreed;      // место освободилось (отмена, перенос, продление слота)
    attribute def Cancel;         // отмена записи студентом или преподавателем
    attribute def SlotStarted;    // преподаватель начал слот
    attribute def Called;         // студент вызван (по порядку или вне очереди)
    attribute def Passed;         // отметка «Защищено»
    attribute def Rework;         // отметка «На доработку»
    attribute def NoShow;         // отметка «Не явился»

    part def Booking {
        attribute position : Integer;          // позиция в очереди или листе ожидания
        attribute minutesToStart : Integer;    // минут до начала слота
        attribute freeSeats : Integer;
    }

    state def BookingState {
        doc /* Записи и позиции листа ожидания одного слота. Пороги: окно записи —
               от 7 суток до 1 часа до начала; заморозка листа ожидания — 30 минут. */

        action notifyPromoted;  // письмо и уведомление «Вы записаны» (REQ-FUNC-010)
        action startPromotion;  // продвинуть первого из листа ожидания

        entry; then decide;
        state decide;
        transition decide_booked first decide if freeSeats > 0 then Booked;
        transition decide_wait   first decide if freeSeats == 0 then Waitlisted;

        state Waitlisted {
            doc /* В листе ожидания, FIFO (REQ-FUNC-009) */
        }
        state Booked {
            doc /* Место закреплено, известны позиция и расчётное время (REQ-FUNC-007, 011) */
        }
        state InQueue {
            doc /* Слот начат: студент ждёт вызова, позиция обновляется по SSE (REQ-FUNC-013) */
        }
        state Defending {
            doc /* Студент вызван и защищается */
        }
        state Passed;    // ЛР защищена
        state Rework;    // нужна повторная запись
        state NoShow;    // неявка (REQ-FUNC-016)
        state Cancelled; // отменена; место освобождено
        state Expired;   // лист ожидания не дошёл до студента

        transition promote
            first Waitlisted accept SeatFreed
            if minutesToStart > 30 and position == 1
            do action notifyPromoted
            then Booked;
        transition waitFreeze
            first Waitlisted accept SlotStarted
            then Expired;
        transition leaveWait
            first Waitlisted accept Cancel
            then Cancelled;
        transition cancelBooking
            first Booked accept Cancel
            if minutesToStart >= 60
            do action startPromotion
            then Cancelled;
        transition start
            first Booked accept SlotStarted
            then InQueue;
        transition call
            first InQueue accept Called
            then Defending;
        transition pass
            first Defending accept Passed
            then Passed;
        transition rework
            first Defending accept Rework
            then Rework;
        transition noShow
            first Defending accept NoShow
            then NoShow;
    }
}
