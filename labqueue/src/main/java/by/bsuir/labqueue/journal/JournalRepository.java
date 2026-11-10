package by.bsuir.labqueue.journal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JournalRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findTop50ByOrderByIdDesc();

    List<JournalEntry> findByActionOrderByIdDesc(String action);
}
