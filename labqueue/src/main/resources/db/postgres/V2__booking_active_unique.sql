-- Одна активная запись студента в слоте даже при ошибке в коде (представления SDD 005 и 010).
-- Частичный индекс есть только в PostgreSQL, поэтому миграция лежит в отдельном каталоге.
CREATE UNIQUE INDEX booking_one_active_per_slot
    ON booking (slot_id, student_id)
    WHERE status IN ('WAITLISTED', 'BOOKED', 'IN_QUEUE', 'DEFENDING');
