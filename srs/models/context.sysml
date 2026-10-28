// SysML v2 — контекст системы LabQueue (диаграмма images/context.png)
package LabQueueContext {
    private import ScalarValues::*;

    // ---------- порты ----------
    port def HttpsPort { doc /* HTTPS (TLS 1.2+), браузер ↔ сервер приложения */ }
    port def SsePort { doc /* поток Server-Sent Events: обновления живой очереди */ }
    port def SmtpPort { doc /* исходящая почта, SMTP с STARTTLS */ }

    // ---------- пользователи ----------
    part def User {
        attribute email : String;
        attribute fullName : String;
        port browser : ~HttpsPort;
        port live : ~SsePort;
        port inbox : ~SmtpPort;
    }
    part def Student :> User {
        doc /* Студент: записывается на защиту, стоит в листе ожидания, следит за очередью. */
        attribute group : String;
    }
    part def Teacher :> User {
        doc /* Преподаватель: публикует слоты защиты и ведёт живую очередь. */
    }
    part def Admin :> User {
        doc /* Администратор кафедры: учётные записи преподавателей, группы. */
    }

    // ---------- внешние сервисы ----------
    part def SmtpServer {
        doc /* Внешний почтовый сервер (Gmail SMTP / Яндекс 360). */
        port smtp : SmtpPort;
    }

    // ---------- разрабатываемая система ----------
    part def WebUi { doc /* Thymeleaf-страницы, формы, таблицы слотов */ }
    part def AppServer { doc /* Spring Boot, REST, Spring Security */ }
    part def QueueEngine {
        doc /* Позиции, расчётное время, продвижение из листа ожидания */
        attribute freezeMinutes : Integer = 30;
    }
    part def LiveHub { doc /* Рассылка событий queue-changed подписчикам слота (SSE) */ }
    part def Database { doc /* PostgreSQL 16 */ }
    part def NotificationService {
        doc /* Очередь писем, повторы (REQ-REL-002), уведомления в приложении */
    }

    part def LabQueue {
        port web : HttpsPort;
        port live : SsePort;
        port mail : ~SmtpPort;

        part webUi : WebUi;
        part api : AppServer;
        part queueEngine : QueueEngine;
        part liveHub : LiveHub;
        part db : Database;
        part notifier : NotificationService;

        connect web to webUi;
        connect webUi to api;
        connect api to queueEngine;
        connect api to db;
        connect queueEngine to db;
        connect queueEngine to liveHub;
        connect queueEngine to notifier;
        connect liveHub to live;
        connect notifier to mail;
    }

    part context {
        part student : Student;
        part teacher : Teacher;
        part admin : Admin;
        part system : LabQueue;
        part smtp : SmtpServer;

        connect student.browser to system.web;
        connect teacher.browser to system.web;
        connect admin.browser to system.web;
        connect system.live to student.live;
        connect system.live to teacher.live;
        connect system.mail to smtp.smtp;
        connect smtp.smtp to student.inbox;
    }
}
