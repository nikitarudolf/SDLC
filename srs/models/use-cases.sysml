// SysML v2 — варианты использования LabQueue (диаграмма images/use-cases.png)
package LabQueueUseCases {
    private import LabQueueContext::*;

    // ---------- студент ----------
    use case def Login {
        subject system : LabQueue;
        actor user : User;
        objective { doc /* Пользователь вошёл по e-mail и паролю (REQ-FUNC-001). */ }
    }
    use case def ViewSlots {
        subject system : LabQueue;
        actor student : Student;
        objective { doc /* Студент видит слоты своих групп и свободные места (REQ-FUNC-006). */ }
    }
    use case def BookDefense {
        subject system : LabQueue;
        actor student : Student;
        objective { doc /* Создана запись на защиту ЛР, известны позиция и время (REQ-FUNC-007). */ }
    }
    use case def JoinWaitlist {
        subject system : LabQueue;
        actor student : Student;
        objective { doc /* Студент поставлен в лист ожидания заполненного слота (REQ-FUNC-009). */ }
    }
    use case def CancelOrMoveBooking {
        subject system : LabQueue;
        actor student : Student;
        objective { doc /* Запись отменена или перенесена в другой слот (REQ-FUNC-008). */ }
        include use case promote : PromoteFromWaitlist;
    }
    use case def TrackLivePosition {
        subject system : LabQueue;
        actor student : Student;
        objective { doc /* Студент видит свою позицию и расчётное время (REQ-FUNC-013). */ }
    }
    use case def ViewProgress {
        subject system : LabQueue;
        actor student : Student;
        objective { doc /* Студент видит статусы своих ЛР (REQ-FUNC-015). */ }
    }

    // ---------- преподаватель ----------
    use case def ManageSlots {
        subject system : LabQueue;
        actor teacher : Teacher;
        objective { doc /* Созданы, изменены или отменены слоты и серии (REQ-FUNC-003…005). */ }
    }
    use case def RunLiveQueue {
        subject system : LabQueue;
        actor teacher : Teacher;
        objective { doc /* Студенты вызваны по порядку, результаты отмечены (REQ-FUNC-012). */ }
    }
    use case def CallOutOfTurn {
        subject system : LabQueue;
        actor teacher : Teacher;
        objective { doc /* Студент вызван вне очереди с указанием причины (REQ-FUNC-012). */ }
    }
    use case def ExportList {
        subject system : LabQueue;
        actor teacher : Teacher;
        objective { doc /* Получен CSV / печатный список записанных (REQ-FUNC-017). */ }
    }

    // ---------- администратор ----------
    use case def ManageAccounts {
        subject system : LabQueue;
        actor admin : Admin;
        objective { doc /* Заведены преподаватели, группы и списки студентов (REQ-FUNC-002). */ }
    }

    // ---------- системные ----------
    use case def PromoteFromWaitlist {
        subject system : LabQueue;
        actor student : Student;
        objective { doc /* Первый в листе ожидания получил место и уведомление (REQ-FUNC-010). */ }
    }
    use case def ReceiveNotifications {
        subject system : LabQueue;
        actor user : User;
        actor smtp : SmtpServer;
        objective { doc /* Пользователь получил письмо / уведомление в приложении (REQ-FUNC-014). */ }
    }

    // ---------- отношения ----------
    // «extend»: вызов вне очереди расширяет обычное ведение очереди
    dependency callOutExtendsRun from CallOutOfTurn to RunLiveQueue;
    // «extend»: постановка в лист ожидания расширяет запись, если мест нет
    dependency waitlistExtendsBook from JoinWaitlist to BookDefense;
}
