package by.bsuir.labqueue.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    boolean existsByNumber(String number);

    List<StudyGroup> findAllByOrderByNumber();
}
