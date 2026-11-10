package by.bsuir.labqueue.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class DisciplineForm {

    @NotBlank(message = "Укажите название")
    @Size(max = 150, message = "Не длиннее 150 символов")
    private String name;

    @NotNull(message = "Укажите число ЛР")
    @Min(value = Discipline.MIN_LABS, message = "Число ЛР — от 1 до 15")
    @Max(value = Discipline.MAX_LABS, message = "Число ЛР — от 1 до 15")
    private Integer labCount = 8;

    @NotEmpty(message = "Выберите хотя бы одну группу")
    private List<Long> groupIds = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLabCount() {
        return labCount;
    }

    public void setLabCount(Integer labCount) {
        this.labCount = labCount;
    }

    public List<Long> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<Long> groupIds) {
        this.groupIds = groupIds;
    }
}
