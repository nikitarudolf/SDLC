package by.bsuir.labqueue.slot;

import by.bsuir.labqueue.queue.SlotRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Форма создания слота (рисунок 7 SRS). Время вводится в часовом поясе приложения. */
public class SlotForm {

    @NotNull(message = "Выберите дисциплину")
    private Long disciplineId;

    @NotEmpty(message = "Выберите хотя бы одну группу")
    private List<Long> groupIds = new ArrayList<>();

    @NotNull(message = "Укажите дату")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @NotNull(message = "Укажите время начала")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "Укажите время окончания")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    @NotBlank(message = "Укажите аудиторию или ссылку")
    @Size(max = 200, message = "Не длиннее 200 символов")
    private String place;

    @NotNull(message = "Укажите длительность защиты")
    private Integer defenseMinutes = SlotRules.DEFAULT_DEFENSE_MINUTES;

    public Long getDisciplineId() {
        return disciplineId;
    }

    public void setDisciplineId(Long disciplineId) {
        this.disciplineId = disciplineId;
    }

    public List<Long> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<Long> groupIds) {
        this.groupIds = groupIds;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public Integer getDefenseMinutes() {
        return defenseMinutes;
    }

    public void setDefenseMinutes(Integer defenseMinutes) {
        this.defenseMinutes = defenseMinutes;
    }
}
