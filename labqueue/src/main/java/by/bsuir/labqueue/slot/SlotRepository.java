package by.bsuir.labqueue.slot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    /** SELECT … FOR UPDATE: изменения одного слота выполняются строго последовательно (ADR-0004). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> lockById(@Param("id") Long id);

    @Query("""
            select s from Slot s
            where s.teacher.id = :teacherId and s.status <> by.bsuir.labqueue.slot.SlotStatus.CANCELLED
              and s.startsAt < :endsAt and s.endsAt > :startsAt and s.id <> :excludeId
            order by s.startsAt""")
    List<Slot> findOverlapping(@Param("teacherId") Long teacherId, @Param("startsAt") Instant startsAt,
                               @Param("endsAt") Instant endsAt, @Param("excludeId") Long excludeId);

    @Query("""
            select s from Slot s
            where s.teacher.id = :teacherId and s.endsAt > :from
            order by s.startsAt""")
    List<Slot> findTeacherSlotsEndingAfter(@Param("teacherId") Long teacherId, @Param("from") Instant from);

    @Query("""
            select distinct s from Slot s join s.groups g
            where g.id = :groupId and s.startsAt >= :from and s.startsAt < :to
            order by s.startsAt""")
    List<Slot> findForGroup(@Param("groupId") Long groupId, @Param("from") Instant from, @Param("to") Instant to);
}
