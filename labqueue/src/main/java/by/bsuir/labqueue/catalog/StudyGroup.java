package by.bsuir.labqueue.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Учебная группа, например 450501. */
@Entity
@Table(name = "study_group")
public class StudyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String number;

    protected StudyGroup() {
    }

    public StudyGroup(String number) {
        this.number = number.trim();
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof StudyGroup g && getId() != null && getId().equals(g.getId()));
    }

    @Override
    public int hashCode() {
        return 31;
    }
}
