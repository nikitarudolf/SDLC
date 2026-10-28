// SysML v2 — макеты экранов LabQueue (изображения images/ui-*.png)
package LabQueueUi {
    private import ScalarValues::*;

    // ---------- базовые элементы интерфейса ----------
    abstract part def Widget { attribute visible : Boolean = true; }
    part def Screen { attribute title : String; attribute route : String; }
    part def Dialog :> Screen;
    part def TextLabel :> Widget { attribute text : String; }
    part def TextField :> Widget { attribute label : String; attribute placeholder : String; }
    part def PasswordField :> TextField;
    part def DateField :> TextField;
    part def TimeField :> TextField;
    part def NumberField :> TextField { attribute min : Integer; attribute max : Integer; }
    part def ComboBox :> Widget { attribute label : String; attribute options : String[*]; }
    part def CheckBox :> Widget { attribute label : String; attribute checked : Boolean; }
    part def Button :> Widget { attribute caption : String; attribute enabled : Boolean = true; }
    part def Link :> Widget { attribute caption : String; attribute target : String; }
    part def Badge :> Widget { attribute text : String; attribute color : String; }
    part def Table :> Widget { attribute columns : String[*]; }
    part def Panel :> Widget { attribute header : String; }
    part def NavBar :> Widget { attribute items : String[*]; attribute active : String; }

    // ---------- экраны ----------
    part def LoginScreen :> Screen {
        attribute :>> title = "Вход";
        attribute :>> route = "/login";
        part loginForm : Panel {
            part logo : TextLabel { attribute :>> text = "LabQueue"; }
            part slogan : TextLabel { attribute :>> text = "Электронная очередь на защиту ЛР"; }
            part emailField : TextField { attribute :>> label = "E-mail"; }
            part passwordField : PasswordField { attribute :>> label = "Пароль"; }
            part rememberMe : CheckBox { attribute :>> label = "Запомнить меня"; }
            part loginButton : Button { attribute :>> caption = "Войти"; }
            part registerLink : Link { attribute :>> caption = "Нет аккаунта? Зарегистрироваться"; }
        }
        comment loginNote /* Вход по e-mail и паролю (REQ-FUNC-001). После 5 неудачных попыток
            подряд вход блокируется на 15 минут (REQ-SEC-002). */
        comment roleNote /* Роль (студент / преподаватель / администратор) определяется
            учётной записью; после входа открывается стартовый экран роли. */
    }

    part def SlotsScreen :> Screen {
        attribute :>> title = "Слоты защиты";
        attribute :>> route = "/slots";
        part navBar : NavBar { attribute :>> active = "Слоты"; }
        part weekNav : Panel { part prev : Button; part period : TextLabel; part next : Button; }
        part disciplineFilter : ComboBox { attribute :>> label = "Дисциплина"; }
        part onlyFree : CheckBox { attribute :>> label = "Только со свободными местами"; }
        part slotTable : Table {
            attribute :>> columns = ("Дата", "Время", "Дисциплина", "Преподаватель",
                                     "Место", "Места", "Действие");
            part seatsBadge : Badge[0..*];
            part actionButton : Button[0..*];
        }
        part nextDefense : Panel { attribute :>> header = "Ближайшая защита"; }
        part legend : Panel { attribute :>> header = "Статусы"; part items : Badge[5]; }
        comment slotsNote /* Видны только слоты групп студента (REQ-FUNC-006); статус мест —
            цветом и текстом (REQ-INT-003). */
    }

    part def BookingDialog :> Dialog {
        attribute :>> title = "Запись на защиту";
        part slotInfo : TextLabel;
        part labSelect : ComboBox { attribute :>> label = "Лабораторная работа"; }
        part seatsInfo : Badge;
        part estimate : Panel { attribute :>> header = "Позиция и расчётное время"; }
        part rulesNote : TextLabel;
        part cancelButton : Button { attribute :>> caption = "Отмена"; }
        part confirmButton : Button { attribute :>> caption = "Записаться"; }
        part waitlistButton : Button {
            attribute :>> caption = "Встать в лист ожидания";
            attribute :>> visible = false;   // показывается вместо confirmButton, если мест нет
        }
        comment bookNote /* Окно записи — от 7 суток до 1 часа до начала; не более двух
            активных записей по дисциплине (REQ-FUNC-007). */
        comment waitNote /* Если мест нет — лист ожидания (REQ-FUNC-009); место выдаётся
            автоматически до заморозки за 30 мин (REQ-FUNC-010). */
    }

    part def MyBookingsScreen :> Screen {
        attribute :>> title = "Мои записи";
        attribute :>> route = "/my";
        part navBar : NavBar { attribute :>> active = "Мои записи"; }
        part liveCard : Panel {
            attribute :>> header = "Идёт защита";
            part currentPos : TextLabel;
            part aheadCount : TextLabel;
            part myPos : TextLabel;
            part eta : TextLabel;
            part queueStrip : Badge[1..*];
            part updated : TextLabel;    // обновляется по SSE (REQ-FUNC-013)
        }
        part bookingsList : Table {
            attribute :>> columns = ("Дисциплина", "ЛР", "Слот", "Позиция", "Расчётное время",
                                     "Статус", "Действия");
            part moveButton : Button[0..*] { attribute :>> caption = "Перенести"; }
            part cancelButton : Button[0..*] { attribute :>> caption = "Отменить"; }
        }
        part waitlistList : Table { part leaveButton : Button[0..*]; }
        part progress : Panel { attribute :>> header = "Прогресс по ЛР"; }
        comment liveNote /* Позиция и расчётное время обновляются без перезагрузки,
            задержка ≤ 5 с (REQ-FUNC-013, REQ-PERF-002). */
        comment moveNote /* Перенос атомарный: старая запись сохраняется, пока не получена
            новая (REQ-FUNC-008). */
    }

    part def TeacherScheduleScreen :> Screen {
        attribute :>> title = "Расписание защит";
        attribute :>> route = "/teacher/slots";
        part navBar : NavBar { attribute :>> active = "Расписание"; }
        part weekGrid : Table { part slotBlock : Panel[0..*]; }
        part slotForm : Panel {
            attribute :>> header = "Новый слот";
            part discipline : ComboBox;
            part groups : ComboBox;
            part date : DateField;
            part startTime : TimeField;
            part endTime : TimeField;
            part perDefense : NumberField { attribute :>> min = 5; attribute :>> max = 30; }
            part place : TextField;
            part repeatWeekly : CheckBox;
            part repeatUntil : DateField;
            part capacity : TextLabel;   // вычисляется: ⌊(end − start) / perDefense⌋
            part saveButton : Button { attribute :>> caption = "Сохранить"; }
        }
        comment slotNote /* Вместимость рассчитывается автоматически (REQ-FUNC-003);
            серия создаётся по дням недели до даты (REQ-FUNC-004). */
    }

    part def LiveQueueScreen :> Screen {
        attribute :>> title = "Живая очередь";
        attribute :>> route = "/teacher/slots/{id}/live";
        part header : Panel { part finishButton : Button { attribute :>> caption = "Завершить слот"; } }
        part currentDefense : Panel {
            attribute :>> header = "Сейчас защищается";
            part passButton : Button { attribute :>> caption = "Защищено"; }
            part reworkButton : Button { attribute :>> caption = "На доработку"; }
            part noShowButton : Button { attribute :>> caption = "Не явился"; }
        }
        part queueTable : Table {
            attribute :>> columns = ("Поз.", "Студент", "ЛР", "Расчётное", "Статус", "");
            part callOutButton : Button[0..*] { attribute :>> caption = "Вне очереди"; }
        }
        part waitlistPanel : Panel { attribute :>> header = "Лист ожидания"; }
        part journal : Panel { attribute :>> header = "Журнал"; }
        comment liveNote /* Одно нажатие — отметка и вызов следующего (REQ-FUNC-012).
            «Вне очереди» требует причину. */
        comment journalNote /* Все действия попадают в журнал слота (REQ-OBS-001). */
    }

    // ---------- навигация ----------
    attribute def LoginOk { attribute role : String; }
    attribute def Logout;

    state def Navigation {
        entry; then Login;
        state Login;
        state Slots;
        state Booking;
        state MyBookings;
        state TeacherSchedule;
        state LiveQueue;

        transition first Login accept ok : LoginOk if ok.role == "Student" then Slots;
        transition first Login accept ok : LoginOk if ok.role == "Teacher" then TeacherSchedule;
        transition first Slots then Booking;           // selectSlot
        transition first Booking then MyBookings;      // confirmed
        transition first Booking then Slots;           // cancel
        transition first Slots then MyBookings;        // menu «Мои записи»
        transition first MyBookings then Slots;        // menu «Слоты»
        transition first MyBookings then Booking;      // move
        transition first TeacherSchedule then LiveQueue;   // startSlot
        transition first LiveQueue then TeacherSchedule;   // finishSlot
    }
}
