"""Рендер графической части SRS LabQueue в PNG.

Каждая диаграмма и макет описаны как HTML/SVG по моделям *.sysml из этого каталога
(имена частей «имя : Тип» совпадают с моделью) и сохраняются в ../images через
headless Google Chrome или Microsoft Edge.

Запуск:  python srs/models/render.py
"""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "images"
SCALE = 2

BROWSERS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "google-chrome", "chromium", "chromium-browser", "msedge",
]

CSS = """
*{box-sizing:border-box}
body{margin:0;background:#fff;font-family:'Segoe UI',Arial,sans-serif;color:#1f2937}
.frame{position:relative;margin:10px;border:1.5px solid #333;background:#fff}
.tab{position:absolute;top:-1.5px;left:-1.5px;background:#f3f4f6;border:1.5px solid #333;
 font:700 13px 'Segoe UI',Arial;padding:4px 18px 4px 8px;clip-path:polygon(0 0,100% 0,calc(100% - 10px) 100%,0 100%)}
.canvas{position:relative;padding:44px 22px 18px}
.browser{border:1px solid #cbd5e1;border-radius:10px;overflow:hidden;background:#f1f5f9}
.bar{height:34px;background:#e2e8f0;display:flex;align-items:center;padding:0 12px;gap:7px}
.dot{width:11px;height:11px;border-radius:50%}
.url{margin-left:22px;background:#fff;border-radius:12px;padding:3px 14px;font-size:11px;color:#64748b;width:340px}
.page{background:#f1f5f9;position:relative}
.p{position:relative;border:1px dashed #b4aee6;padding:17px 7px 7px;border-radius:3px}
.p::before{content:attr(data-l);position:absolute;top:3px;left:6px;font:10px/1 Consolas,monospace;color:#6a62b8;white-space:nowrap}
.note{background:#fff8c4;border:1px solid #d6c45a;padding:8px 11px 10px;font-size:11.5px;line-height:1.35;color:#3f3a12;
 clip-path:polygon(0 0,calc(100% - 14px) 0,100% 14px,100% 100%,0 100%);position:relative}
.note i{display:block;color:#8a7a1f;font-size:10.5px;margin-bottom:3px}
.notes{display:flex;gap:16px;margin-top:14px}
.notes .note{flex:1}
.nav{background:#1e293b;color:#cbd5e1;display:flex;align-items:center;padding:0 18px;height:46px;gap:26px;font-size:13px}
.nav b{color:#fff;font-size:17px;margin-right:18px}
.nav .act{color:#fff;border-bottom:2px solid #3b82f6;padding:13px 0}
.nav .sp{flex:1}
.inp{background:#fff;border:1px solid #cbd5e1;border-radius:6px;padding:8px 10px;font-size:12.5px;color:#334155;min-height:34px}
.inp.ph{color:#94a3b8}
.lbl{font-size:11.5px;font-weight:600;color:#475569;margin-bottom:3px}
.btn{display:inline-block;background:#2563eb;color:#fff;border-radius:6px;padding:8px 14px;font-size:12.5px;font-weight:600;text-align:center;border:1px solid #2563eb}
.btn.sec{background:#fff;color:#1e40af;border-color:#93c5fd}
.btn.gray{background:#e2e8f0;color:#94a3b8;border-color:#e2e8f0}
.btn.ok{background:#16a34a;border-color:#16a34a}
.btn.warn{background:#d97706;border-color:#d97706}
.btn.bad{background:#dc2626;border-color:#dc2626}
.btn.sm{padding:4px 9px;font-size:11.5px}
.bd{display:inline-block;border-radius:10px;padding:2px 9px;font-size:11px;font-weight:600;white-space:nowrap}
.g{background:#dcfce7;color:#166534}.y{background:#fef3c7;color:#92400e}.r{background:#fee2e2;color:#991b1b}
.v{background:#ede9fe;color:#5b21b6}.b{background:#dbeafe;color:#1e40af}.k{background:#e5e7eb;color:#374151}
.card{background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:12px}
.h{font-weight:700;font-size:14px;margin-bottom:8px}
table.t{border-collapse:collapse;width:100%;background:#fff;font-size:12px}
table.t th{background:#f8fafc;color:#475569;font-weight:600;text-align:left;padding:7px 8px;border-bottom:1px solid #e2e8f0}
table.t td{padding:7px 8px;border-bottom:1px solid #f1f5f9;vertical-align:middle}
.row{display:flex;gap:12px}
.col{display:flex;flex-direction:column;gap:10px}
.muted{color:#64748b;font-size:11.5px}
"""


def P(label, inner, style=""):
    return f'<div class="p" data-l="{label}" style="{style}">{inner}</div>'


def note(text, style=""):
    return f'<div class="note" style="{style}"><i>comment</i>{text}</div>'


def browser(url, inner, style=""):
    dots = ('<span class="dot" style="background:#ef4444"></span>'
            '<span class="dot" style="background:#f59e0b"></span>'
            '<span class="dot" style="background:#22c55e"></span>')
    return (f'<div class="browser" style="{style}"><div class="bar">{dots}'
            f'<div class="url">{url}</div></div><div class="page">{inner}</div></div>')


def frame(tab, inner):
    return f'<div class="frame"><div class="tab">{tab}</div><div class="canvas">{inner}</div></div>'


def navbar(items, active, user):
    links = "".join(f'<span class="{"act" if i == active else ""}">{i}</span>' for i in items)
    return P("navBar : NavBar",
             f'<div class="nav"><b>LabQueue</b>{links}<span class="sp"></span>'
             f'<span>🔔 <span class="bd r" style="padding:0 6px">2</span></span><span>{user}</span></div>',
             "padding:17px 0 0;border-color:#64748b")


# ------------------------------------------------------------------ макеты экранов
def ui_login():
    form = (P("logo : TextLabel", '<div style="font:800 30px Segoe UI;color:#0f172a">LabQueue</div>')
            + P("slogan : TextLabel", '<div class="muted" style="font-size:13px">Электронная очередь на защиту ЛР</div>', "margin-top:4px")
            + P("emailField : TextField", '<div class="lbl">E-mail</div><div class="inp ph">student@bsuir.by</div>', "margin-top:14px")
            + P("passwordField : PasswordField", '<div class="lbl">Пароль</div><div class="inp">••••••••••</div>', "margin-top:8px")
            + P("rememberMe : CheckBox", '<span style="font-size:13px">☑ Запомнить меня</span>', "margin-top:8px")
            + P("loginButton : Button", '<div class="btn" style="display:block">Войти</div>', "margin-top:8px")
            + P("registerLink : Link", '<span style="color:#2563eb;text-decoration:underline;font-size:13px">Нет аккаунта? Зарегистрироваться</span>', "margin-top:8px"))
    page = (f'<div style="height:560px;display:flex;justify-content:center;align-items:center">'
            f'{P("loginForm : Panel", f"<div class=card style=width:330px;padding:18px>{form}</div>", "padding:17px 5px 5px")}</div>')
    body = (browser("https://labqueue.app/login", page)
            + note("Вход по e-mail и паролю (REQ-FUNC-001). После 5 неудачных попыток подряд вход "
                   "блокируется на 15 минут (REQ-SEC-002).", "position:absolute;left:60px;top:150px;width:250px")
            + note("Роль (студент / преподаватель / администратор) определяется учётной записью; после "
                   "входа открывается стартовый экран роли (рисунок 3.7).", "position:absolute;right:60px;top:150px;width:250px"))
    return frame("part def LoginScreen :&gt; Screen &nbsp; [Вход]", body)


def ui_slots():
    rows = [
        ("Вт 28.10", "10:00–11:30", "ЖЦРПО", "Петрова А. С.", "506-5", '<span class="bd b">Вы записаны · поз. 4</span>', '<span class="btn sm sec">Открыть</span>'),
        ("Вт 28.10", "13:55–15:30", "СиФО ВМ", "Сидоров К. Л.", "413-1", '<span class="bd r">Заполнен · ожидают 3</span>', '<span class="btn sm sec">В лист ожидания</span>'),
        ("Ср 29.10", "08:00–09:30", "Базы данных", "Ковалёв Р. Н.", "214-5", '<span class="bd g">Свободно · 6 из 9</span>', '<span class="btn sm">Записаться</span>'),
        ("Чт 30.10", "11:40–13:15", "ЖЦРПО", "Петрова А. С.", "506-5", '<span class="bd y">Мало мест · 1 из 9</span>', '<span class="btn sm">Записаться</span>'),
        ("Пт 31.10", "10:00–11:00", "КиДСВТ", "Мороз В. И.", "онлайн", '<span class="bd g">Свободно · 5 из 6</span>', '<span class="btn sm">Записаться</span>'),
        ("Пт 31.10", "15:50–17:25", "Базы данных", "Ковалёв Р. Н.", "214-5", '<span class="bd k">Запись закрыта</span>', '<span class="btn sm gray">Записаться</span>'),
    ]
    trs = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r) + "</tr>" for r in rows)
    table = (f'<table class="t"><tr><th>Дата</th><th>Время</th><th>Дисциплина</th><th>Преподаватель</th>'
             f'<th>Место</th><th>Места</th><th></th></tr>{trs}</table>')
    toolbar = ('<div class="row" style="align-items:flex-end">'
               + P("weekNav : Panel", '<div class="row" style="align-items:center"><span class="btn sm sec">◀</span>'
                   '<b style="font-size:14px">27.10 – 02.11.2026</b><span class="btn sm sec">▶</span></div>')
               + P("disciplineFilter : ComboBox", '<div class="inp" style="width:200px">Все дисциплины ▾</div>')
               + P("onlyFree : CheckBox", '<div style="font-size:12.5px;padding:8px 0">☐ Только со свободными местами</div>')
               + '</div>')
    side = ('<div class="col" style="width:270px">'
            + P("nextDefense : Panel", '<div class="card"><div class="h">Ближайшая защита</div>'
                '<div style="font-size:15px;font-weight:700">ЖЦРПО · ЛР № 4</div>'
                '<div class="muted" style="margin:4px 0 8px">Вт 28.10 · ауд. 506-5 · Петрова А. С.</div>'
                '<div class="row" style="gap:8px"><span class="bd b">Позиция 4</span><span class="bd k">≈ 10:30</span></div></div>')
            + P("legend : Panel", '<div class="card"><div class="h">Статусы</div><div class="col" style="gap:6px;align-items:flex-start">'
                '<span class="bd g">Свободно</span><span class="bd y">Мало мест (≤ 2)</span><span class="bd r">Заполнен</span>'
                '<span class="bd b">Вы записаны</span><span class="bd k">Запись закрыта</span></div></div>')
            + '</div>')
    page = (navbar(["Слоты", "Мои записи", "Прогресс"], "Слоты", "Кузьмин Д. · гр. 450501")
            + f'<div style="padding:14px" class="col">{toolbar}<div class="row">'
            + P("slotTable : Table", table, "flex:1") + side + '</div></div>')
    body = (browser("https://labqueue.app/slots", page)
            + '<div class="notes">'
            + note("Показываются только слоты групп студента и дисциплин, к которым привязана группа (REQ-FUNC-006).")
            + note("Статус мест — цветом и текстом одновременно (REQ-INT-003). «Мало мест» — осталось не более 2.")
            + note("Запись закрывается за 1 час до начала слота и открывается за 7 суток (REQ-FUNC-007).")
            + '</div>')
    return frame("part def SlotsScreen :&gt; Screen &nbsp; [Слоты защиты]", body)


def _dialog(label, slot, lab, hint, seats, estimate, rules, buttons):
    inner = ('<div class="card" style="width:440px;padding:16px;box-shadow:0 10px 30px rgba(0,0,0,.25)">'
             '<div class="h" style="font-size:16px">Запись на защиту</div>'
             + P("slotInfo : TextLabel", f'<div style="font-size:13px">{slot}</div>')
             + P("labSelect : ComboBox", f'<div class="lbl">Лабораторная работа</div><div class="inp">{lab}</div>'
                 f'<div class="muted" style="margin-top:3px">{hint}</div>', "margin-top:8px")
             + P("seatsInfo : Badge", seats, "margin-top:8px")
             + P("estimate : Panel", f'<div style="background:#eff6ff;border-radius:6px;padding:10px;font-size:12.5px">{estimate}</div>', "margin-top:8px")
             + P("rulesNote : TextLabel", f'<div class="muted">{rules}</div>', "margin-top:8px")
             + f'<div class="row" style="justify-content:flex-end;margin-top:10px">{buttons}</div></div>')
    return P(label, inner, "padding:17px 5px 5px;background:transparent")


def ui_booking():
    left = _dialog("bookingDialog : BookingDialog  [есть места]",
                   "<b>ЖЦРПО</b> · Чт 30.10.2026 · 11:40–13:15<br>ауд. 506-5 · Петрова А. С.",
                   "ЛР № 5 ▾", "ЛР № 1–3 защищены · на ЛР № 4 уже есть запись", '<span class="bd y">Осталось мест: 1 из 9</span>',
                   "Ваша позиция: <b>9</b> · расчётное время <b>13:00</b>",
                   "Отменить или перенести запись можно до 10:40 30.10.",
                   P("cancelButton : Button", '<span class="btn sec">Отмена</span>', "min-width:150px")
                   + P("confirmButton : Button", '<span class="btn">Записаться</span>', "min-width:160px"))
    right = _dialog("bookingDialog : BookingDialog  [слот заполнен]",
                    "<b>СиФО ВМ</b> · Вт 28.10.2026 · 13:55–15:30<br>ауд. 413-1 · Сидоров К. Л.",
                    "ЛР № 3 ▾", "ЛР № 1–2 защищены", '<span class="bd r">Мест нет · в листе ожидания 3</span>',
                    "Вы будете <b>4-м</b> в листе ожидания. Если место освободится до <b>13:25</b>, "
                    "вы получите его автоматически и письмо на e-mail.",
                    "В листе ожидания можно быть не более чем в трёх слотах.",
                    P("cancelButton : Button", '<span class="btn sec">Отмена</span>', "min-width:150px")
                    + P("waitlistButton : Button", '<span class="btn" style="background:#7c3aed;border-color:#7c3aed">Встать в лист ожидания</span>'))
    shade = "".join('<div style="height:26px;margin:9px 0;background:#e2e8f0;border-radius:4px"></div>' for _ in range(9))
    page = (navbar(["Слоты", "Мои записи", "Прогресс"], "Слоты", "Кузьмин Д. · гр. 450501")
            + f'<div style="position:relative;padding:14px;height:600px;overflow:hidden">{shade}'
            '<div style="position:absolute;inset:0;background:rgba(15,23,42,.45)"></div>'
            f'<div class="row" style="position:absolute;inset:0;justify-content:center;align-items:flex-start;padding-top:22px;gap:24px">{left}{right}</div></div>')
    body = (browser("https://labqueue.app/slots#book", page)
            + '<div class="notes">'
            + note("Запись: окно от 7 суток до 1 часа до начала, в слоте — одна запись студента, по дисциплине — "
                   "не более двух активных записей, защищённую ЛР выбрать нельзя (REQ-FUNC-007).")
            + note("Мест нет → лист ожидания (REQ-FUNC-009). Освободившееся место первый в листе получает "
                   "автоматически; за 30 мин до начала лист замораживается (REQ-FUNC-010).")
            + '</div>')
    return frame("part def BookingDialog :&gt; Dialog &nbsp; [Запись на защиту]", body)


def ui_my():
    strip = "".join(
        f'<span class="bd {c}" style="min-width:34px;text-align:center">{t}</span>'
        for t, c in [("1 ✓", "g"), ("2 ▶", "y"), ("3", "k"), ("4 · вы", "b"), ("5", "k"), ("6", "k"), ("7 ✕", "k"), ("8", "k")])
    live = ('<div class="card" style="border:2px solid #22c55e">'
            '<div class="row" style="align-items:center;justify-content:space-between"><div class="h" style="margin:0">'
            '<span class="bd r">● LIVE</span> Идёт защита · ЖЦРПО · ауд. 506-5</div>'
            + P("updated : TextLabel", '<span class="muted">обновлено 3 с назад</span>') + '</div>'
            '<div class="row" style="margin-top:8px">'
            + P("currentPos : TextLabel", '<div class="muted">Сейчас защищается</div><div style="font:700 22px Segoe UI">позиция 2</div>', "flex:1")
            + P("aheadCount : TextLabel", '<div class="muted">Перед вами</div><div style="font:700 22px Segoe UI">2 человека</div>', "flex:1")
            + P("myPos : TextLabel", '<div class="muted">Ваша позиция</div><div style="font:700 22px Segoe UI;color:#1d4ed8">4</div>', "flex:1")
            + P("eta : TextLabel", '<div class="muted">Расчётное время</div><div style="font:700 22px Segoe UI">≈ 10:41</div>', "flex:1")
            + '</div>' + P("queueStrip : Badge[*]", f'<div class="row" style="gap:6px">{strip}</div>', "margin-top:8px") + '</div>')
    bookings = ('<table class="t"><tr><th>Дисциплина</th><th>ЛР</th><th>Слот</th><th>Поз.</th><th>Расчётное</th><th>Статус</th><th>Действия</th></tr>'
                '<tr><td>ЖЦРПО</td><td>№ 4</td><td>Вт 28.10 10:00</td><td>4</td><td>10:41</td><td><span class="bd y">Идёт защита</span></td>'
                '<td><span class="btn sm gray">Перенести</span> <span class="btn sm gray">Отменить</span></td></tr>'
                '<tr><td>Базы данных</td><td>№ 2</td><td>Ср 29.10 08:00</td><td>2</td><td>08:10</td><td><span class="bd b">Записан</span></td>'
                '<td><span class="btn sm sec">Перенести</span> <span class="btn sm sec" style="color:#b91c1c;border-color:#fca5a5">Отменить</span></td></tr></table>')
    wait = ('<table class="t"><tr><th>Дисциплина</th><th>ЛР</th><th>Слот</th><th>В листе</th><th></th></tr>'
            '<tr><td>СиФО ВМ</td><td>№ 3</td><td>Вт 28.10 13:55</td><td><span class="bd v">2-й из 3</span></td>'
            '<td><span class="btn sm sec">Покинуть</span></td></tr></table>')
    prog = ('<div class="card"><div class="h">Прогресс по ЛР</div>'
            '<div class="lbl">ЖЦРПО</div><div class="row" style="gap:5px;flex-wrap:wrap;margin-bottom:8px">'
            '<span class="bd g">1 ✓</span><span class="bd g">2 ✓</span><span class="bd y">3 ↻ доработка</span><span class="bd b">4 записан</span><span class="bd k">5</span></div>'
            '<div class="lbl">Базы данных</div><div class="row" style="gap:5px;flex-wrap:wrap;margin-bottom:8px">'
            '<span class="bd g">1 ✓</span><span class="bd b">2 записан</span><span class="bd k">3</span><span class="bd k">4</span></div>'
            '<div class="lbl">СиФО ВМ</div><div class="row" style="gap:5px;flex-wrap:wrap">'
            '<span class="bd g">1 ✓</span><span class="bd g">2 ✓</span><span class="bd v">3 ожидание</span></div></div>')
    page = (navbar(["Слоты", "Мои записи", "Прогресс"], "Мои записи", "Кузьмин Д. · гр. 450501")
            + '<div style="padding:14px" class="row"><div class="col" style="flex:1">'
            + P("liveCard : Panel", live)
            + P("bookingsList : Table", '<div class="h" style="padding:8px 8px 0;background:#fff;margin:0">Мои записи</div>' + bookings)
            + P("waitlistList : Table", '<div class="h" style="padding:8px 8px 0;background:#fff;margin:0">Лист ожидания</div>' + wait)
            + '</div>' + P("progress : Panel", prog, "width:260px;align-self:flex-start") + '</div>')
    body = (browser("https://labqueue.app/my", page)
            + '<div class="notes">'
            + note("Позиция и расчётное время обновляются без перезагрузки страницы, задержка не более 5 с "
                   "(REQ-FUNC-013, REQ-PERF-002). Других студентов в очереди студент видит только как «Фамилия И.» (REQ-SEC-003).")
            + note("Перенос атомарный: старая запись сохраняется, пока не получена новая (REQ-FUNC-008). "
                   "За 1 час до начала кнопки недоступны.")
            + '</div>')
    return frame("part def MyBookingsScreen :&gt; Screen &nbsp; [Мои записи]", body)


def ui_teacher():
    days = ["Пн 27.10", "Вт 28.10", "Ср 29.10", "Чт 30.10", "Пт 31.10"]
    blocks = {
        1: ('<b>10:00–11:30</b><br>ЖЦРПО · 450501<br><span class="bd y">7 / 9</span>', 112),
        3: ('<b>11:40–13:15</b><br>ЖЦРПО · 450501, 450502<br><span class="bd y">8 / 9</span>', 186),
        4: ('<b>10:00–11:30</b><br>ЖЦРПО · 450503<br><span class="bd g">0 / 9</span> <span class="muted">серия</span>', 112),
    }
    cols = ""
    for i, d in enumerate(days):
        blk = ""
        if i in blocks:
            html, top = blocks[i]
            blk = P("slotBlock : Panel", f'<div style="background:#dbeafe;border-left:4px solid #2563eb;border-radius:4px;padding:6px;font-size:11.5px">{html}</div>',
                    f"position:absolute;left:4px;right:4px;top:{top}px;padding:16px 3px 3px")
        cols += (f'<div style="flex:1;border-left:1px solid #e2e8f0;position:relative">'
                 f'<div style="text-align:center;font-weight:600;font-size:12px;padding:6px;background:#f8fafc;border-bottom:1px solid #e2e8f0">{d}</div>{blk}</div>')
    hours = "".join(f'<div style="height:44px;font-size:10.5px;color:#94a3b8;border-top:1px solid #f1f5f9">{h}:00</div>' for h in range(8, 17))
    grid = (f'<div style="display:flex;background:#fff;border:1px solid #e2e8f0;border-radius:8px;height:440px;overflow:hidden">'
            f'<div style="width:44px;padding-top:30px;padding-left:4px">{hours}</div>{cols}</div>')

    def fld(label, t, inner, w=""):
        return P(f"{label} : {t}", inner, f"flex:1;{w}")
    form = ('<div class="card" style="padding:12px"><div class="h">Новый слот</div><div class="col" style="gap:6px">'
            + fld("discipline", "ComboBox", '<div class="lbl">Дисциплина</div><div class="inp">ЖЦРПО ▾</div>')
            + fld("groups", "ComboBox", '<div class="lbl">Группы</div><div class="inp"><span class="bd b">450501 ×</span> <span class="bd b">450502 ×</span> ＋</div>')
            + fld("date", "DateField", '<div class="lbl">Дата</div><div class="inp">04.11.2026</div>')
            + '<div class="row" style="gap:6px">' + fld("startTime", "TimeField", '<div class="lbl">Начало</div><div class="inp">10:00</div>')
            + fld("endTime", "TimeField", '<div class="lbl">Конец</div><div class="inp">11:30</div>') + '</div>'
            + '<div class="row" style="gap:6px">' + fld("perDefense", "NumberField", '<div class="lbl">Защита, мин</div><div class="inp">10</div>')
            + fld("place", "TextField", '<div class="lbl">Место</div><div class="inp">ауд. 506-5</div>') + '</div>'
            + '<div class="row" style="gap:6px;align-items:flex-end">' + fld("repeatWeekly", "CheckBox", '<div style="font-size:12.5px;padding:8px 0">☑ Повторять по вторникам</div>')
            + fld("repeatUntil", "DateField", '<div class="lbl">до</div><div class="inp">22.12.2026</div>') + '</div>'
            + P("capacity : TextLabel", '<div style="background:#f0fdf4;border-radius:6px;padding:8px;font-size:12.5px">Вместимость: <b>9 мест</b> · серия: <b>8 слотов</b></div>')
            + '<div class="row" style="justify-content:flex-end">' + P("saveButton : Button", '<span class="btn">Сохранить</span>') + '</div>'
            + '</div></div>')
    page = (navbar(["Расписание", "Дисциплины", "Журнал"], "Расписание", "Петрова А. С. · преподаватель")
            + '<div style="padding:14px" class="row">'
            + P("weekGrid : Table", '<div class="row" style="align-items:center;margin-bottom:6px"><span class="btn sm sec">◀</span>'
                '<b>27.10 – 31.10.2026</b><span class="btn sm sec">▶</span></div>' + grid, "flex:1")
            + P("slotForm : Panel", form, "width:380px;align-self:flex-start") + '</div>')
    body = (browser("https://labqueue.app/teacher/slots", page)
            + '<div class="notes">'
            + note("Вместимость рассчитывается автоматически: ⌊(конец − начало) / длительность защиты⌋ (REQ-FUNC-003). "
                   "Слоты преподавателя не могут пересекаться.")
            + note("Серия создаётся по выбранным дням недели до даты; конфликтующие слоты не создаются и "
                   "показываются списком (REQ-FUNC-004).")
            + '</div>')
    return frame("part def TeacherScheduleScreen :&gt; Screen &nbsp; [Расписание защит]", body)


def ui_live():
    q = [("1", "Иванов И.", "№ 2", "10:00", '<span class="bd g">Защищено</span>', ""),
         ("2", "Петров П.", "№ 3", "10:21", '<span class="bd y">Защищается</span>', ""),
         ("3", "Сидорова А.", "№ 4", "10:31", '<span class="bd k">Ожидает</span>', '<span class="btn sm sec">Вне очереди</span>'),
         ("4", "Кузьмин Д.", "№ 4", "10:41", '<span class="bd k">Ожидает</span>', '<span class="btn sm sec">Вне очереди</span>'),
         ("5", "Орлова Е.", "№ 1", "10:51", '<span class="bd k">Ожидает</span>', '<span class="btn sm sec">Вне очереди</span>'),
         ("6", "Романов С.", "№ 3", "11:01", '<span class="bd k">Ожидает</span>', '<span class="btn sm sec">Вне очереди</span>'),
         ("7", "Ковалёва В.", "№ 2", "—", '<span class="bd k">Отменена 10:05</span>', ""),
         ("8", "Новик М.", "№ 2", "11:11", '<span class="bd k">Ожидает</span>', '<span class="btn sm sec">Вне очереди</span>')]
    trs = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r) + "</tr>" for r in q)
    table = f'<table class="t"><tr><th>Поз.</th><th>Студент</th><th>ЛР</th><th>Расчётное</th><th>Статус</th><th></th></tr>{trs}</table>'
    head = P("header : Panel", '<div class="card row" style="align-items:center;justify-content:space-between;padding:10px 14px">'
             '<div><b style="font-size:15px">ЖЦРПО · Вт 28.10.2026 · 10:00–11:30 · ауд. 506-5</b>'
             '<div class="muted">Записано 7 из 9 · лист ожидания заморожен с 09:30</div></div>'
             '<div class="row" style="align-items:center"><span class="bd r">● идёт · 00:32</span>'
             + P("finishButton : Button", '<span class="btn sec">Завершить слот</span>') + '</div></div>')
    cur = P("currentDefense : Panel", '<div class="card" style="border:2px solid #f59e0b"><div class="muted">Сейчас защищается</div>'
            '<div style="font:700 20px Segoe UI;margin:3px 0">Позиция 2 · Петров П. <span class="muted">(гр. 450501)</span></div>'
            '<div class="muted">ЛР № 3 · вызван в 10:21 · идёт 9 мин</div><div class="row" style="margin-top:10px">'
            + P("passButton : Button", '<span class="btn ok">✓ Защищено</span>')
            + P("reworkButton : Button", '<span class="btn warn">↻ На доработку</span>')
            + P("noShowButton : Button", '<span class="btn bad">✕ Не явился</span>') + '</div></div>')
    waitp = P("waitlistPanel : Panel", '<div class="card"><div class="h">Лист ожидания <span class="bd k">заморожен</span></div>'
              '<div style="font-size:12.5px;line-height:1.7">1. Белов Г. — ЛР № 2<br>2. Жук Т. — ЛР № 4</div></div>')
    jour = P("journal : Panel", '<div class="card"><div class="h">Журнал</div><div style="font-size:11.5px;line-height:1.6;color:#334155">'
             '10:21 Петров П. вызван (по порядку)<br>10:20 Иванов И. — «Защищено», ЛР № 2<br>'
             '10:05 Ковалёва В. отменила запись (поз. 7)<br>10:00 слот начат<br>09:30 лист ожидания заморожен<br>'
             '08:47 Новик М. продвинут из листа ожидания</div></div>')
    page = ('<div style="padding:14px" class="col">' + head + '<div class="row"><div class="col" style="flex:1">' + cur
            + P("queueTable : Table", table) + '</div><div class="col" style="width:300px">' + waitp + jour + '</div></div></div>')
    body = (browser("https://labqueue.app/teacher/slots/42/live", page)
            + '<div class="notes">'
            + note("Одно нажатие — отметка результата и вызов следующего по порядку (REQ-FUNC-012). "
                   "«Вне очереди» открывает поле обязательной причины.")
            + note("Каждое действие записывается в журнал слота (REQ-OBS-001); у студентов экран обновляется "
                   "не позже 5 с (REQ-PERF-002).")
            + '</div>')
    return frame("part def LiveQueueScreen :&gt; Screen &nbsp; [Живая очередь]", body)


# ------------------------------------------------------------------ SVG-диаграммы
SVG_CSS = """
text{font-family:'Segoe UI',Arial,sans-serif;fill:#1f2937}
.st{font-size:10.5px;fill:#555}.nm{font-size:13px;font-weight:700}.bd{font-size:11px;fill:#444}
.ln{stroke:#333;stroke-width:1.3;fill:none}.dl{stroke:#333;stroke-width:1.2;fill:none;stroke-dasharray:6 4}
.lb{font-size:10.5px;fill:#333}
"""


def svg_page(tab, w, h, inner):
    defs = ('<defs><marker id="ar" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto-start-reverse">'
            '<path d="M0,0 L10,5 L0,10 z" fill="#333"/></marker>'
            '<marker id="oa" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="10" markerHeight="10" orient="auto">'
            '<path d="M0,0 L10,5 L0,10" fill="none" stroke="#333" stroke-width="1.3"/></marker></defs>')
    return (f'<div class="frame"><div class="tab">{tab}</div><div class="canvas" style="padding:30px 10px 10px">'
            f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}"><style>{SVG_CSS}</style>{defs}{inner}</svg></div></div>')


def box(x, y, w, h, stereo, name, lines=(), dashed=False, fill="#fff", stroke="#333"):
    da = ' stroke-dasharray="6 4"' if dashed else ""
    s = (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}" stroke="{stroke}" stroke-width="1.3"{da}/>'
         f'<text x="{x + w / 2}" y="{y + 17}" text-anchor="middle" class="st">«{stereo}»</text>'
         f'<text x="{x + w / 2}" y="{y + 33}" text-anchor="middle" class="nm">{name}</text>'
         f'<line x1="{x}" y1="{y + 41}" x2="{x + w}" y2="{y + 41}" stroke="{stroke}" stroke-width="1"/>')
    for i, t in enumerate(lines):
        s += f'<text x="{x + 9}" y="{y + 58 + i * 15}" class="bd">{t}</text>'
    return s


def line(pts, cls="ln", arrow=False, label=None, lx=None, ly=None, anchor="middle"):
    d = " ".join(f"{x},{y}" for x, y in pts)
    m = ' marker-end="url(#ar)"' if arrow == "full" else (' marker-end="url(#oa)"' if arrow else "")
    s = f'<polyline points="{d}" class="{cls}"{m}/>'
    if label:
        s += f'<text x="{lx}" y="{ly}" class="lb" text-anchor="{anchor}">{label}</text>'
    return s


def port(x, y):
    return f'<rect x="{x - 6}" y="{y - 6}" width="12" height="12" fill="#fff" stroke="#333" stroke-width="1.3"/>'


def context():
    s = ""
    s += box(60, 160, 230, 100, "part", "student : Student", ["веб-браузер,", "смартфон или ноутбук"])
    s += box(60, 410, 230, 100, "part", "teacher : Teacher", ["ведёт живую очередь", "в аудитории"])
    s += box(60, 660, 230, 100, "part", "admin : Admin", ["кафедра: преподаватели,", "группы"])
    s += (f'<rect x="420" y="100" width="830" height="770" fill="#f4f7fc" stroke="#333" stroke-width="1.6"/>'
          f'<text x="835" y="120" text-anchor="middle" class="st">«part»</text>'
          f'<text x="835" y="138" text-anchor="middle" class="nm">system : LabQueue</text>')
    s += box(470, 190, 300, 115, "part", "webUi : WebUi", ["Thymeleaf-страницы,", "формы, таблицы слотов"])
    s += box(900, 190, 300, 115, "part", "api : AppServer", ["Spring Boot, REST,", "Spring Security"])
    s += box(470, 410, 300, 115, "part", "db : Database", ["PostgreSQL 16"])
    s += box(900, 410, 300, 115, "part", "queueEngine : QueueEngine", ["позиции, расчётное время,", "продвижение, заморозка 30 мин"])
    s += box(470, 640, 300, 115, "part", "liveHub : LiveHub", ["события queue-changed", "подписчикам слота (SSE)"])
    s += box(900, 640, 300, 115, "part", "notifier : NotificationService", ["очередь писем, повторы,", "уведомления в приложении"])
    s += box(1360, 640, 280, 115, "part", "smtp : SmtpServer", ["почтовый сервер", "(внешний)"], dashed=True)
    # порты на границе системы
    s += port(420, 250) + port(420, 700) + port(1250, 700)
    s += '<text x="430" y="240" class="lb">web</text><text x="430" y="690" class="lb">live</text><text x="1205" y="690" class="lb">mail</text>'
    # HTTPS от пользователей
    s += line([(290, 210), (360, 210), (360, 250), (414, 250)], label="HTTPS", lx=376, ly=244, anchor="start")
    s += line([(290, 460), (360, 460), (360, 250)])
    s += line([(290, 710), (360, 710), (360, 460)])
    # SSE к студенту и преподавателю
    s += line([(414, 700), (330, 700), (330, 235), (290, 235)], cls="dl")
    s += line([(330, 485), (290, 485)], cls="dl")
    s += '<text x="372" y="694" class="lb" text-anchor="middle">SSE</text>'
    # внутренние связи
    s += line([(426, 250), (470, 250)])
    s += line([(770, 248), (900, 248)], label="HTTP", lx=835, ly=242)
    s += line([(910, 305), (760, 410)], label="JDBC", lx=850, ly=350)
    s += line([(1050, 305), (1050, 410)])
    s += line([(900, 468), (770, 468)], label="JDBC", lx=835, ly=462)
    s += line([(910, 525), (760, 640)], label="события", lx=860, ly=575)
    s += line([(1050, 525), (1050, 640)])
    s += line([(470, 700), (426, 700)])
    s += line([(1200, 700), (1244, 700)])
    s += line([(1256, 700), (1360, 700)], label="SMTP / TLS", lx=1308, ly=692)
    # доставка писем пользователям
    s += line([(1500, 755), (1500, 930), (35, 930), (35, 230), (60, 230)], cls="dl", label="e-mail студентам и преподавателям", lx=760, ly=922)
    s += line([(35, 480), (60, 480)], cls="dl")
    s += ('<text x="835" y="965" text-anchor="middle" class="lb" style="font-style:italic;fill:#666">'
          'Сплошная рамка — разрабатываемая система и её пользователи; пунктир — внешний сервис. '
          'Квадраты на границе system — порты (web, live, mail); пунктирные линии — SSE и доставка писем.</text>')
    return svg_page("part context : LabQueueContext &nbsp;[interconnection view]", 1680, 985, s)


def actor(x, y, name, sub):
    return (f'<circle cx="{x}" cy="{y}" r="11" fill="#fff" stroke="#333" stroke-width="1.4"/>'
            f'<line x1="{x}" y1="{y + 11}" x2="{x}" y2="{y + 42}" class="ln"/>'
            f'<line x1="{x - 18}" y1="{y + 22}" x2="{x + 18}" y2="{y + 22}" class="ln"/>'
            f'<line x1="{x}" y1="{y + 42}" x2="{x - 14}" y2="{y + 64}" class="ln"/>'
            f'<line x1="{x}" y1="{y + 42}" x2="{x + 14}" y2="{y + 64}" class="ln"/>'
            f'<text x="{x}" y="{y + 82}" text-anchor="middle" class="nm" style="font-size:12px">{name}</text>'
            f'<text x="{x}" y="{y + 96}" text-anchor="middle" class="st" style="font-style:italic">{sub}</text>')


def uc(cx, cy, text, rx=165, ry=30):
    parts = text.split("|")
    t = "".join(f'<text x="{cx}" y="{cy + 4 + (i - (len(parts) - 1) / 2) * 14}" text-anchor="middle" style="font-size:12px">{p}</text>'
                for i, p in enumerate(parts))
    return f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="#fff" stroke="#333" stroke-width="1.3"/>' + t


def use_cases():
    s = ('<rect x="330" y="40" width="1010" height="900" fill="#fafafa" stroke="#333" stroke-width="1.6"/>'
         '<text x="835" y="62" text-anchor="middle" class="nm">«subject»  LabQueue</text>')
    L, R = 560, 1110
    left = [("ViewSlots", 220, "Просматривать слоты"), ("Book", 310, "Записаться на защиту"),
            ("Wait", 400, "Встать в лист ожидания"), ("Cancel", 490, "Отменить / перенести запись"),
            ("Track", 580, "Следить за позицией|в живой очереди"), ("Progress", 670, "Просматривать прогресс по ЛР")]
    right = [("Slots", 255, "Управлять слотами и сериями"), ("Run", 345, "Вести живую очередь"),
             ("Call", 435, "Вызвать вне очереди"), ("Export", 525, "Экспортировать список"),
             ("Accounts", 635, "Управлять преподавателями|и группами"), ("Promote", 755, "Продвинуть из листа ожидания")]
    s += uc(835, 110, "Войти в систему") + uc(835, 870, "Получать уведомления")
    for _, y, t in left:
        s += uc(L, y, t)
    for _, y, t in right:
        s += uc(R, y, t)
    s += actor(150, 360, "Студент", "Student :&gt; User")
    s += actor(1530, 300, "Преподаватель", "Teacher :&gt; User")
    s += actor(1530, 720, "Администратор", "Admin :&gt; User")
    sx, sy = 172, 392
    for _, y, _ in left:
        s += line([(sx, sy), (L - 165, y)])
    s += line([(sx, sy - 10), (670, 117)])
    s += line([(sx, sy + 20), (672, 878)])
    tx, ty = 1508, 330
    for key, y, _ in right:
        if key in ("Slots", "Run", "Export"):
            s += line([(tx, ty), (R + 165, y)])
    s += line([(tx - 3, ty - 30), (990, 120)])
    s += line([(tx, ty + 30), (1320, 850), (1000, 872)])
    s += line([(1508, 752), (R + 165, 635)])
    # «include» / «extend»
    s += line([(700, 508), (975, 738)], cls="dl", arrow=True, label="«include»", lx=850, ly=610, anchor="start")
    s += line([(725, 400), (760, 400), (760, 310), (727, 310)], cls="dl", arrow=True, label="«extend»", lx=766, ly=360, anchor="start")
    s += line([(1275, 435), (1305, 435), (1305, 345), (1277, 345)], cls="dl", arrow=True, label="«extend»", lx=1311, ly=404, anchor="start")
    s += line([(1010, 780), (950, 842)], cls="dl", arrow=True, label="«include»", lx=990, ly=822, anchor="start")
    # внешний сервис
    s += ('<rect x="735" y="975" width="200" height="34" fill="#fff" stroke="#333" stroke-dasharray="6 4"/>'
          '<text x="835" y="997" text-anchor="middle" class="nm" style="font-size:11.5px">«part» smtp : SmtpServer</text>')
    s += line([(835, 900), (835, 975)], cls="dl")
    s += ('<text x="345" y="928" class="lb" style="font-style:italic;fill:#666">Student, Teacher и Admin наследуют User: вход и уведомления '
          'доступны всем ролям; «Продвинуть из листа ожидания» выполняет система.</text>')
    return svg_page("use case view &nbsp;LabQueueUseCases", 1680, 1020, s)


def st(x, y, w, h, name, body, fill, stroke):
    parts = body.split("|")
    t = "".join(f'<text x="{x + w / 2}" y="{y + 44 + i * 14}" text-anchor="middle" class="bd">{p}</text>' for i, p in enumerate(parts))
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>'
            f'<text x="{x + w / 2}" y="{y + 19}" text-anchor="middle" class="nm">{name}</text>'
            f'<line x1="{x}" y1="{y + 27}" x2="{x + w}" y2="{y + 27}" stroke="{stroke}"/>' + t)


def final(x, y):
    return (f'<circle cx="{x}" cy="{y}" r="9" fill="#fff" stroke="#333" stroke-width="1.4"/>'
            f'<circle cx="{x}" cy="{y}" r="5" fill="#333"/>')


def booking_states():
    s = ('<rect x="230" y="60" width="1080" height="560" rx="16" fill="#fbfbfb" stroke="#555" stroke-width="1.3"/>'
         '<text x="246" y="82" class="nm">Active</text>'
         '<text x="310" y="82" class="lb" style="font-style:italic;fill:#666">— запись участвует в очереди слота; позиции пересчитывает queueEngine</text>')
    s += '<circle cx="70" cy="420" r="9" fill="#333"/>'
    s += '<polygon points="160,420 178,402 196,420 178,438" fill="#fff" stroke="#333" stroke-width="1.4"/>'
    s += line([(79, 420), (160, 420)], arrow="full")
    s += st(290, 130, 240, 90, "Waitlisted", "в листе ожидания, FIFO|позиция 1…n", "#f5f3ff", "#7c3aed")
    s += st(290, 380, 240, 90, "Booked", "место закреплено,|позиция и расчётное время", "#eff6ff", "#2563eb")
    s += st(660, 380, 240, 90, "InQueue", "слот начат, ожидание|вызова (обновление SSE)", "#f0f9ff", "#0891b2")
    s += st(1030, 380, 240, 90, "Defending", "студент вызван|и защищается", "#fff7ed", "#ea580c")
    s += st(1400, 230, 200, 80, "Passed", "ЛР защищена", "#f0fdf4", "#16a34a")
    s += st(1400, 385, 200, 80, "Rework", "на доработку,|нужна новая запись", "#fffbeb", "#d97706")
    s += st(1400, 540, 200, 80, "NoShow", "неявка|(REQ-FUNC-016)", "#fef2f2", "#dc2626")
    s += st(660, 690, 240, 80, "Cancelled", "запись отменена,|место освобождено", "#f3f4f6", "#6b7280")
    s += st(660, 130, 240, 90, "Expired", "лист ожидания не дошёл|до студента", "#f3f4f6", "#6b7280")
    # из решения
    s += line([(178, 402), (178, 175), (290, 175)], arrow="full", label="[freeSeats == 0]", lx=186, ly=240, anchor="start")
    s += line([(196, 420), (290, 420)], arrow="full", label="[freeSeats &gt; 0]", lx=243, ly=412)
    # переходы
    s += line([(410, 220), (410, 380)], arrow="full")
    s += ('<text x="402" y="262" class="lb" text-anchor="end">accept SeatFreed</text>'
          '<text x="402" y="276" class="lb" text-anchor="end">[minutesToStart &gt; 30</text>'
          '<text x="402" y="290" class="lb" text-anchor="end">and position == 1]</text>'
          '<text x="402" y="304" class="lb" text-anchor="end">/ notifyPromoted</text>')
    s += line([(530, 175), (660, 175)], arrow="full", label="accept SlotStarted", lx=595, ly=167)
    s += line([(530, 425), (660, 425)], arrow="full", label="accept SlotStarted", lx=595, ly=417)
    s += line([(900, 425), (1030, 425)], arrow="full", label="accept Called", lx=965, ly=417)
    s += line([(1270, 400), (1330, 400), (1330, 270), (1400, 270)], arrow="full", label="accept Passed", lx=1336, ly=262, anchor="start")
    s += line([(1270, 425), (1400, 425)], arrow="full", label="accept Rework", lx=1336, ly=417)
    s += line([(1270, 450), (1330, 450), (1330, 580), (1400, 580)], arrow="full", label="accept NoShow", lx=1336, ly=572, anchor="start")
    s += line([(300, 470), (300, 730), (660, 730)], arrow="full")
    s += ('<text x="308" y="640" class="lb">accept Cancel [minutesToStart ≥ 60]</text>'
          '<text x="308" y="654" class="lb">/ startPromotion</text>')
    s += line([(470, 220), (470, 330), (600, 330), (600, 710), (660, 710)], arrow="full", label="accept Cancel", lx=606, ly=560, anchor="start")
    for x, y in [(1625, 270), (1625, 425), (1625, 580), (950, 730), (950, 175)]:
        s += final(x, y)
    s += line([(1600, 270), (1616, 270)]) + line([(1600, 425), (1616, 425)]) + line([(1600, 580), (1616, 580)])
    s += line([(900, 730), (941, 730)]) + line([(900, 175), (941, 175)])
    s += ('<rect x="20" y="800" width="1660" height="78" fill="#fff8c4" stroke="#d6c45a"/>'
          '<text x="32" y="818" class="lb" style="font-style:italic;fill:#8a7a1f">comment</text>'
          '<text x="32" y="836" class="lb">minutesToStart — минут до начала слота; position — позиция в листе ожидания. Окно записи — от 7 суток до 1 ч до начала (REQ-FUNC-007);</text>'
          '<text x="32" y="851" class="lb">за 30 мин до начала лист ожидания замораживается (REQ-FUNC-010), освободившиеся места не перераспределяются, расчётное время пересчитывается (REQ-FUNC-011).</text>'
          '<text x="32" y="866" class="lb">Rework не закрывает ЛР — студент записывается снова; две записи NoShow подряд ограничивают запись по дисциплине на 7 суток (REQ-FUNC-016).</text>')
    return svg_page("state def BookingState &nbsp;[state transition view]", 1700, 890, s)


def navigation():
    s = '<circle cx="40" cy="375" r="9" fill="#333"/>'
    s += line([(49, 375), (80, 375)], arrow="full")
    s += st(80, 330, 200, 90, "Login", "LoginScreen|/login", "#fff", "#333")
    s += st(430, 160, 240, 90, "Slots", "SlotsScreen|/slots", "#eff6ff", "#2563eb")
    s += st(860, 60, 260, 90, "Booking", "BookingDialog|(модальное окно)", "#eff6ff", "#2563eb")
    s += st(860, 300, 260, 90, "MyBookings", "MyBookingsScreen|/my", "#eff6ff", "#2563eb")
    s += st(430, 560, 260, 90, "TeacherSchedule", "TeacherScheduleScreen|/teacher/slots", "#f0fdf4", "#16a34a")
    s += st(860, 560, 260, 90, "LiveQueue", "LiveQueueScreen|/teacher/slots/{id}/live", "#f0fdf4", "#16a34a")
    s += line([(280, 360), (350, 360), (350, 205), (430, 205)], arrow="full", label="loginOk [role == Student]", lx=356, ly=290, anchor="start")
    s += line([(280, 395), (350, 395), (350, 605), (430, 605)], arrow="full", label="loginOk [role == Teacher]", lx=356, ly=500, anchor="start")
    s += line([(670, 185), (860, 115)], arrow="full", label="selectSlot", lx=760, ly=140)
    s += line([(860, 85), (550, 85), (550, 160)], arrow="full", label="cancel", lx=700, ly=78)
    s += line([(960, 150), (960, 300)], arrow="full", label="confirmed", lx=953, ly=230, anchor="end")
    s += line([(1030, 300), (1030, 150)], arrow="full", label="move", lx=1037, ly=230, anchor="start")
    s += line([(670, 230), (860, 330)], arrow="full", label="«Мои записи»", lx=785, ly=268, anchor="start")
    s += line([(860, 360), (650, 250)], arrow="full", label="«Слоты»", lx=720, ly=320, anchor="end")
    s += line([(690, 590), (860, 590)], arrow="full", label="startSlot", lx=775, ly=582)
    s += line([(860, 625), (690, 625)], arrow="full", label="finishSlot", lx=775, ly=642)
    s += ('<rect x="1190" y="300" width="270" height="110" fill="#fff8c4" stroke="#d6c45a"/>'
          '<text x="1202" y="318" class="lb" style="font-style:italic;fill:#8a7a1f">comment</text>'
          '<text x="1202" y="338" class="lb">Из любого экрана: logout → Login.</text>'
          '<text x="1202" y="355" class="lb">Слоты и «Мои записи» доступны</text>'
          '<text x="1202" y="370" class="lb">только роли Student, расписание и</text>'
          '<text x="1202" y="385" class="lb">живая очередь — роли Teacher</text>'
          '<text x="1202" y="400" class="lb">(REQ-SEC-003).</text>')
    return svg_page("state def Navigation &nbsp;[state transition view]", 1480, 690, s)


FIGURES = {
    "context": (context, 1720, 1050),
    "use-cases": (use_cases, 1720, 1085),
    "booking-states": (booking_states, 1740, 955),
    "ui-navigation": (navigation, 1520, 755),
    "ui-login": (ui_login, 1180, 700),
    "ui-slots": (ui_slots, 1180, 770),
    "ui-booking-dialog": (ui_booking, 1180, 880),
    "ui-my-bookings": (ui_my, 1180, 870),
    "ui-teacher-schedule": (ui_teacher, 1180, 1010),
    "ui-live-queue": (ui_live, 1180, 945),
}


def find_browser():
    for b in BROWSERS:
        p = shutil.which(b) or (b if os.path.exists(b) else None)
        if p:
            return p
    sys.exit("Не найден Google Chrome / Microsoft Edge")


def render(name, html, w, h, exe, tmp):
    src = Path(tmp) / f"{name}.html"
    src.write_text(f"<!doctype html><meta charset='utf-8'><style>{CSS}</style><body>{html}</body>", encoding="utf-8")
    out = OUT / f"{name}.png"
    subprocess.run([exe, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
                    f"--user-data-dir={Path(tmp) / 'profile'}", f"--force-device-scale-factor={SCALE}",
                    f"--window-size={w},{h}", f"--screenshot={out}", src.as_uri()],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return out


def main():
    OUT.mkdir(exist_ok=True)
    exe = find_browser()
    only = sys.argv[1:]
    with tempfile.TemporaryDirectory() as tmp:
        for name, (fn, w, h) in FIGURES.items():
            if only and name not in only:
                continue
            render(name, fn(), w, h, exe, tmp)
            print(f"images/{name}.png ({w * SCALE}, {h * SCALE})")
    lines = sum(1 for _ in open(HERE / "ui-mockups.sysml", encoding="utf-8"))
    print(f"models/ui-mockups.sysml {lines} lines")


if __name__ == "__main__":
    main()
