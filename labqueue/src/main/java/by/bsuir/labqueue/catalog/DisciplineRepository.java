package by.bsuir.labqueue.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisciplineRepository extends JpaRepository<Discipline, Long> {

    List<Discipline> findByTeacherIdOrderByName(Long teacherId);
}
