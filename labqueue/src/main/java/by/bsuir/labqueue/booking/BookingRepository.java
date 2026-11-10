package by.bsuir.labqueue.booking;

import by.bsuir.labqueue.queue.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    long countBySlotIdAndStatusIn(Long slotId, Collection<BookingStatus> statuses);

    List<Booking> findBySlotIdAndStatusIn(Long slotId, Collection<BookingStatus> statuses);

    List<Booking> findByStudentIdAndSlotIdIn(Long studentId, Collection<Long> slotIds);

    @Query("""
            select b.slot.id, count(b) from Booking b
            where b.slot.id in :slotIds and b.status in :statuses
            group by b.slot.id""")
    List<Object[]> countBySlots(@Param("slotIds") Collection<Long> slotIds,
                                @Param("statuses") Collection<BookingStatus> statuses);

    /** Число мест, занятых в каждом слоте (BOOKED, IN_QUEUE, DEFENDING). */
    default Map<Long, Integer> seatsTaken(Collection<Long> slotIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (slotIds.isEmpty()) {
            return result;
        }
        for (Object[] row : countBySlots(slotIds, BookingStatus.seatHolding())) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    /** Длина листа ожидания каждого слота. */
    default Map<Long, Integer> waitlistLength(Collection<Long> slotIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (slotIds.isEmpty()) {
            return result;
        }
        for (Object[] row : countBySlots(slotIds, List.of(BookingStatus.WAITLISTED))) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }
}
