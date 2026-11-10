package by.bsuir.labqueue.slot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;

/** Изменение слота: место и время окончания (REQ-FUNC-005). */
public class SlotEditForm {

    @NotBlank(message = "Укажите аудиторию или ссылку")
    @Size(max = 200, message = "Не длиннее 200 символов")
    private String place;

    @NotNull(message = "Укажите время окончания")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
}
