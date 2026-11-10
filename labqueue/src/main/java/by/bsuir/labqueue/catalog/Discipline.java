package by.bsuir.labqueue.catalog;

import by.bsuir.labqueue.security.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Дисциплина преподавателя с числом ЛР и привязанными группами (REQ-FUNC-002). */
@Entity
@Table(name = "discipline")
public class Discipline {

    public static final int MIN_LABS = 1;
    public static final int MAX_LABS = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "lab_count", nullable = false)
    private int labCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @ManyToMany
    @JoinTable(name = "discipline_group",
            joinColumns = @JoinColumn(name = "discipline_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<StudyGroup> groups = new LinkedHashSet<>();

    protected Discipline() {
    }

    public Discipline(String name, int labCount, User teacher, Set<StudyGroup> groups) {
        this.name = name.trim();
        this.labCount = labCount;
        this.teacher = teacher;
        this.groups.addAll(groups);
    }

    public boolean ownedBy(User user) {
        return teacher.getId().equals(user.getId());
    }

    public boolean hasLab(int number) {
        return number >= 1 && number <= labCount;
    }

    public List<StudyGroup> sortedGroups() {
        return groups.stream().sorted(Comparator.comparing(StudyGroup::getNumber)).toList();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getLabCount() {
        return labCount;
    }

    public User getTeacher() {
        return teacher;
    }

    public Set<StudyGroup> getGroups() {
        return groups;
    }
}
