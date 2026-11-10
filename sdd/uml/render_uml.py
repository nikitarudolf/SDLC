"""Рендер UML-диаграмм LabQueue в PNG (sdd/images/uml-*.png).

Диаграммы описаны текстом PlantUML в файлах *.puml этого каталога; изображения строит
этот скрипт: геометрия каждой диаграммы задана ниже, а имена, подписи и переходы
совпадают с *.puml. Для растеризации используется headless Chrome или Edge.

Запуск:  python sdd/uml/render_uml.py [имя диаграммы ...]
"""
import math
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "images"
sys.path.insert(0, str(HERE.parent.parent / "srs" / "models"))
from render import CSS, find_browser  # noqa: E402

SCALE = 2
FONT = "'Segoe UI',Arial,sans-serif"
STYLE = f"""
text{{font-family:{FONT};fill:#1f2937;font-size:16px}}
.nm{{font-size:17px;font-weight:700}}.st{{font-size:14px;fill:#555}}.lb{{font-size:14px;fill:#333}}
.sm{{font-size:13px;fill:#444}}.red{{font-size:14px;fill:#c00000;font-weight:600}}
.ln{{stroke:#333;stroke-width:1.6;fill:none}}.dl{{stroke:#333;stroke-width:1.5;fill:none;stroke-dasharray:7 5}}
"""
DEFS = ('<defs><marker id="ar" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" '
        'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#333"/></marker>'
        '<marker id="oa" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="10" markerHeight="10" orient="auto">'
        '<path d="M0,0 L10,5 L0,10" fill="none" stroke="#333" stroke-width="1.5"/></marker>'
        '<marker id="ra" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto">'
        '<path d="M0,0 L10,5 L0,10 z" fill="#c00000"/></marker>'
        '<marker id="ho" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" orient="auto">'
        '<path d="M1,1 L11,6 L1,11 z" fill="#fff" stroke="#333" stroke-width="1.4"/></marker></defs>')


def page(tab, w, h, inner):
    return (f'<div class="frame"><div class="tab" style="font-size:16px">{tab}</div>'
            f'<div class="canvas" style="padding:34px 10px 10px"><svg width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}"><style>{STYLE}</style>{DEFS}{inner}</svg></div></div>')


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def txt(x, y, text, cls="", anchor="middle", dy=17, style=""):
    parts = text.split("|")
    y0 = y - (len(parts) - 1) * dy / 2
    out = ""
    for i, p in enumerate(parts):
        out += (f'<text x="{x}" y="{y0 + i * dy + 5}" text-anchor="{anchor}" class="{cls}" '
                f'style="{style}">{esc(p)}</text>')
    return out


def poly(pts, kind="solid", arrow="full", label=None, lx=None, ly=None, anchor="middle", cls="lb", color=None):
    d = " ".join(f"{x},{y}" for x, y in pts)
    m = {"full": ' marker-end="url(#ar)"', "open": ' marker-end="url(#oa)"', "hollow": ' marker-end="url(#ho)"',
         "red": ' marker-end="url(#ra)"', None: ""}[arrow]
    c = "dl" if kind == "dashed" else "ln"
    extra = f' style="stroke:{color}"' if color else ""
    s = f'<polyline points="{d}" class="{c}"{m}{extra}/>'
    if label:
        s += txt(lx, ly, label, cls, anchor)
    return s


# ------------------------------------------------------------------ прецеденты
def actor_fig(x, y, name, sub=None, system=False):
    if system:
        s = (f'<rect x="{x - 78}" y="{y - 26}" width="156" height="52" fill="#fff" stroke="#333" stroke-width="1.5" '
             f'stroke-dasharray="6 4"/>' + txt(x, y - 8, "«system»", "st") + txt(x, y + 12, name, "nm", style="font-size:15px"))
        return s
    s = (f'<circle cx="{x}" cy="{y - 38}" r="12" fill="#fff" stroke="#333" stroke-width="1.5"/>'
         f'<line x1="{x}" y1="{y - 26}" x2="{x}" y2="{y + 8}" class="ln"/>'
         f'<line x1="{x - 20}" y1="{y - 14}" x2="{x + 20}" y2="{y - 14}" class="ln"/>'
         f'<line x1="{x}" y1="{y + 8}" x2="{x - 15}" y2="{y + 32}" class="ln"/>'
         f'<line x1="{x}" y1="{y + 8}" x2="{x + 15}" y2="{y + 32}" class="ln"/>'
         + txt(x, y + 52, name, "nm", style="font-size:15px"))
    return s


RX, RY = 135, 37


def ell(cx, cy, text, fill="#fff"):
    return (f'<ellipse cx="{cx}" cy="{cy}" rx="{RX}" ry="{RY}" fill="{fill}" stroke="#333" stroke-width="1.5"/>'
            + txt(cx, cy, text, "lb", style="font-size:15px", dy=18))


def edge_pt(cx, cy, tx, ty):
    dx, dy = tx - cx, ty - cy
    k = 1 / math.sqrt((dx / RX) ** 2 + (dy / RY) ** 2)
    return cx + dx * k, cy + dy * k


def use_cases():
    W, H = 1340, 990
    U = {  # id: (cx, cy, текст)
        "01": (655, 105, "UC-01 Войти|в систему"),
        "02": (325, 190, "UC-02 Зарегистрироваться"),
        "03": (325, 285, "UC-03 Просмотреть|слоты"),
        "04": (325, 385, "UC-04 Записаться|на защиту"),
        "06": (325, 525, "UC-06 Отменить или|перенести запись"),
        "07": (325, 640, "UC-07 Следить за|позицией в очереди"),
        "10": (655, 255, "UC-10 Вызвать|вне очереди"),
        "11": (655, 350, "UC-11 Отметить|неявку"),
        "05": (655, 445, "UC-05 Встать в|лист ожидания"),
        "15": (655, 585, "UC-15 Продвинуть|из листа ожидания"),
        "08": (985, 190, "UC-08 Управлять слотами|и сериями"),
        "09": (985, 305, "UC-09 Вести|живую очередь"),
        "12": (985, 565, "UC-12 Просмотреть ведомость|и отчёт о записи"),
        "13": (985, 670, "UC-13 Выгрузить|отчёт в Excel"),
        "14": (985, 790, "UC-14 Управлять преподавателями|и группами"),
        "16": (325, 770, "UC-16 Заморозить|лист ожидания"),
        "17": (655, 860, "UC-17 Отправить|уведомления"),
    }
    s = ('<rect x="175" y="40" width="1000" height="900" fill="#fafafa" stroke="#333" stroke-width="1.8"/>'
         + txt(190, 62, "«subject» LabQueue", "nm", anchor="start"))
    for k, (cx, cy, t) in U.items():
        s += ell(cx, cy, t)

    def link(a, b, kind="solid", arrow=None, label=None, off=(0, -8)):
        ax, ay, _ = U[a]
        bx, by, _ = U[b]
        p1 = edge_pt(ax, ay, bx, by)
        p2 = edge_pt(bx, by, ax, ay)
        out = poly([p1, p2], kind, arrow)
        if label:
            out += txt((p1[0] + p2[0]) / 2 + off[0], (p1[1] + p2[1]) / 2 + off[1], label, "lb", style="font-size:13px")
        return out

    def to_actor(ax, ay, uid, side="left"):
        cx, cy, _ = U[uid]
        px = cx - RX if side == "left" else cx + RX
        return poly([(ax, ay), (px, cy)], arrow=None)

    S, T, A, TM, SM = (88, 420), (1245, 340), (1245, 790), (88, 870), (1245, 905)
    s += actor_fig(*S, "Студент") + actor_fig(*T, "Преподаватель") + actor_fig(*A, "Администратор")
    s += actor_fig(*TM, "Время|(Планировщик)")
    s += actor_fig(*SM, "Почтовый сервер", system=True)
    # ассоциации
    for u in ("02", "03", "04", "06", "07"):
        s += to_actor(S[0] + 24, S[1] - 14, u, "left")
    s += poly([(S[0] + 24, S[1] - 14), (150, 410), (150, 105), (520, 105)], arrow=None)
    for u in ("08", "09", "12"):
        s += to_actor(T[0] - 24, T[1] - 14, u, "right")
    s += poly([(T[0] - 24, T[1] - 14), (1135, 326), (1135, 105), (790, 105)], arrow=None)
    s += to_actor(A[0] - 24, A[1] - 14, "14", "right")
    ex, ey = edge_pt(655, 105, 1155, 62)
    s += poly([(A[0] - 24, A[1] - 14), (1155, 776), (1155, 62), (ex, ey)], arrow=None)
    s += to_actor(TM[0] + 24, TM[1] - 14, "16")
    s += poly([(TM[0] + 24, TM[1] - 14), (200, 860), (520, 860)], arrow=None)
    px, py = edge_pt(*U["17"][:2], SM[0] - 78, SM[1])
    s += poly([(SM[0] - 78, SM[1]), (px, py)], arrow=None)
    # расширения и включения
    s += link("05", "04", "dashed", "open", "«extend»|[мест нет]", (-6, 34))
    s += link("10", "09", "dashed", "open", "«extend»|[причина указана]", (0, -24))
    s += link("11", "09", "dashed", "open", "«extend»|[прошло ≥ 5 мин]", (0, 34))
    s += link("13", "12", "dashed", "open", "«extend»", (-48, 0))
    s += link("06", "15", "dashed", "open", "«include»", (-18, 24))
    s += link("15", "17", "dashed", "open", "«include»", (46, 0))
    # пояснение условий расширения
    s += ('<rect x="195" y="880" width="270" height="44" fill="#fff8c4" stroke="#d6c45a"/>'
          + txt(330, 902, "UC-02, UC-11 и UC-14 ставят письма|в очередь отправки (UC-17)", "sm", dy=15))
    return page("uc LabQueue", W, H, s)



# ------------------------------------------------------------------ активность
def act_box(cx, cy, text, w=250, h=54, fill="#fff"):
    return (f'<rect x="{cx - w / 2}" y="{cy - h / 2}" width="{w}" height="{h}" rx="14" fill="{fill}" stroke="#333" '
            f'stroke-width="1.5"/>' + txt(cx, cy, text, "lb", style="font-size:14.5px", dy=17))


def dec(cx, cy, text, w=210, h=76):
    return (f'<polygon points="{cx},{cy - h / 2} {cx + w / 2},{cy} {cx},{cy + h / 2} {cx - w / 2},{cy}" fill="#fff" '
            f'stroke="#333" stroke-width="1.5"/>' + txt(cx, cy, text, "lb", style="font-size:13.5px", dy=16))


def merge(cx, cy):
    return (f'<polygon points="{cx},{cy - 11} {cx + 14},{cy} {cx},{cy + 11} {cx - 14},{cy}" fill="#fff" '
            f'stroke="#333" stroke-width="1.5"/>')


def init(cx, cy):
    return f'<circle cx="{cx}" cy="{cy}" r="10" fill="#333"/>'


def final(cx, cy):
    return (f'<circle cx="{cx}" cy="{cy}" r="12" fill="#fff" stroke="#333" stroke-width="1.6"/>'
            f'<circle cx="{cx}" cy="{cy}" r="7" fill="#333"/>')


def bar(x1, x2, y):
    return f'<rect x="{x1}" y="{y - 4}" width="{x2 - x1}" height="8" fill="#333"/>'


def lanes(names, xs, h):
    s = ""
    for n, (a, b) in zip(names, xs):
        s += (f'<rect x="{a}" y="10" width="{b - a}" height="{h}" fill="none" stroke="#333" stroke-width="1.5"/>'
              f'<rect x="{a}" y="10" width="{b - a}" height="36" fill="#e2e8f0" stroke="#333" stroke-width="1.5"/>'
              + txt((a + b) / 2, 28, n, "nm"))
    return s


def activity_booking():
    W, H = 1160, 1090
    s = lanes(["Студент", "Система"], [(20, 420), (420, 1140)], H - 20)
    s += init(220, 90)
    s += act_box(220, 150, "Выбрать слот и|нажать «Записаться»", 240)
    s += act_box(600, 240, "Проверить окно записи|и правила записи", 260)
    s += dec(600, 335, "Правила|выполнены?")
    s += act_box(930, 335, "Показать причину|отказа", 210)
    s += final(930, 425)
    s += act_box(220, 425, "Выбрать ЛР, поставить флажок|о неявке, подтвердить", 290)
    s += act_box(600, 515, "Заблокировать слот|(SELECT … FOR UPDATE)", 260)
    s += dec(600, 610, "Есть свободные|места?")
    s += act_box(600, 715, "Создать запись BOOKED,|присвоить позицию", 260)
    s += act_box(930, 715, "Создать запись WAITLISTED|(лист ожидания)", 250)
    s += merge(600, 790)
    s += act_box(600, 860, "Записать в журнал,|поставить письмо в outbox", 270)
    s += act_box(220, 945, "Увидеть позицию|и расчётное время", 240)
    s += final(220, 1030)
    s += poly([(220, 100), (220, 123)])
    s += poly([(220, 177), (220, 240), (470, 240)])
    s += poly([(600, 267), (600, 297)])
    s += poly([(705, 335), (825, 335)], label="нет", lx=765, ly=325)
    s += poly([(930, 362), (930, 413)])
    s += poly([(600, 373), (600, 425), (365, 425)], label="да", lx=612, ly=400, anchor="start")
    s += poly([(220, 452), (220, 515), (470, 515)])
    s += poly([(600, 542), (600, 572)])
    s += poly([(600, 648), (600, 688)], label="да", lx=612, ly=668, anchor="start")
    s += poly([(705, 610), (930, 610), (930, 688)], label="нет [лист ожидания < 3]", lx=820, ly=598)
    s += poly([(600, 742), (600, 779)])
    s += poly([(930, 742), (930, 790), (614, 790)])
    s += poly([(600, 801), (600, 833)])
    s += poly([(465, 860), (220, 860), (220, 918)])
    s += poly([(220, 972), (220, 1018)])
    return page("act Запись на защиту (UC-04, UC-05)", W, H, s)


def activity_cancel():
    W, H = 1180, 1090
    s = lanes(["Студент", "Система", "Планировщик"], [(20, 370), (370, 880), (880, 1160)], H - 20)
    s += init(195, 90)
    s += act_box(195, 150, "Нажать «Отменить»|и подтвердить", 230)
    s += dec(600, 245, "До начала|≥ 60 мин?", w=190)
    s += act_box(810, 245, "Отказать:|«Сообщите|преподавателю»", 150, 66)
    s += final(810, 345)
    s += act_box(600, 345, "Заблокировать слот, запись|→ CANCELLED", 250)
    s += act_box(600, 435, "Записать в журнал", 220)
    s += dec(600, 530, "До начала > 30 мин|и лист не пуст?", 230)
    s += act_box(600, 625, "Перевести первого из листа|в BOOKED", 250)
    s += bar(470, 1020, 690)
    s += act_box(470, 760, "Пересчитать позиции|и расчётное время", 200)
    s += act_box(1020, 760, "Отправить письмо|«Вы записаны» (SMTP)", 230)
    s += bar(470, 1020, 835)
    s += merge(600, 895)
    s += act_box(195, 965, "Получить уведомление,|увидеть новый статус", 250)
    s += final(195, 1045)
    s += poly([(195, 100), (195, 123)])
    s += poly([(195, 177), (195, 245), (505, 245)])
    s += poly([(695, 245), (735, 245)], label="нет", lx=715, ly=233)
    s += poly([(810, 278), (810, 333)])
    s += poly([(600, 283), (600, 318)], label="да", lx=612, ly=302, anchor="start")
    s += poly([(600, 372), (600, 408)])
    s += poly([(600, 462), (600, 492)])
    s += poly([(600, 568), (600, 598)], label="да", lx=612, ly=585, anchor="start")
    s += poly([(600, 652), (600, 686)])
    s += poly([(470, 694), (470, 733)])
    s += poly([(1020, 694), (1020, 733)])
    s += poly([(470, 787), (470, 831)])
    s += poly([(1020, 787), (1020, 831)])
    s += poly([(600, 839), (600, 883)])
    s += poly([(715, 530), (1135, 530), (1135, 895), (614, 895)], label="нет", lx=730, ly=518)
    s += poly([(586, 895), (195, 895), (195, 938)])
    s += poly([(195, 992), (195, 1033)])
    return page("act Отмена записи и продвижение (UC-06, UC-15)", W, H, s)


# ------------------------------------------------------------------ состояния
def state(cx, cy, name, lines="", w=210, h=74, fill="#fff", stroke="#333"):
    s = (f'<rect x="{cx - w / 2}" y="{cy - h / 2}" width="{w}" height="{h}" rx="16" fill="{fill}" stroke="{stroke}" '
         f'stroke-width="1.6"/>' + txt(cx, cy - h / 2 + 20, name, "nm", style="font-size:16px"))
    if lines:
        s += f'<line x1="{cx - w / 2}" y1="{cy - h / 2 + 32}" x2="{cx + w / 2}" y2="{cy - h / 2 + 32}" stroke="{stroke}"/>'
        s += txt(cx, cy + 14, lines, "sm", dy=15)
    return s


def state_booking():
    W, H = 1700, 900
    s = ""
    s += state(330, 190, "Waitlisted", "в листе ожидания|(FIFO)", fill="#f5f3ff", stroke="#7c3aed")
    s += state(330, 430, "Booked", "место закреплено,|позиция и ETA", fill="#eff6ff", stroke="#2563eb")
    s += state(720, 190, "Expired", "лист не дошёл|до студента", fill="#f3f4f6", stroke="#6b7280")
    s += state(720, 430, "InQueue", "слот начат,|ждёт вызова", fill="#f0f9ff", stroke="#0891b2")
    s += state(1110, 430, "Defending", "вызван,|защищается", fill="#fff7ed", stroke="#ea580c")
    s += state(1500, 230, "Passed", "ЛР защищена", fill="#f0fdf4", stroke="#16a34a")
    s += state(1500, 430, "Rework", "на доработку,|нужна новая запись", fill="#fffbeb", stroke="#d97706")
    s += state(1500, 630, "NoShow", "запись аннулирована", fill="#fef2f2", stroke="#dc2626")
    s += state(720, 720, "Cancelled", "запись отменена,|место освобождено", fill="#f3f4f6", stroke="#6b7280")
    s += '<circle cx="60" cy="430" r="10" fill="#333"/>'
    s += '<polygon points="140,430 158,412 176,430 158,448" fill="#fff" stroke="#333" stroke-width="1.5"/>'
    s += poly([(70, 430), (140, 430)], arrow=None)
    s += poly([(176, 430), (225, 430)], label="[freeSeats > 0]", lx=200, ly=458)
    s += poly([(158, 412), (158, 190), (225, 190)], label="[freeSeats == 0]", lx=165, ly=300, anchor="start")
    s += poly([(330, 227), (330, 393)], label="SeatFreed [> 30 мин до начала,|первый в листе] / письмо|«Вы записаны»",
              lx=342, ly=300, anchor="start")
    s += poly([(435, 190), (615, 190)], label="SlotStarted", lx=525, ly=178)
    s += poly([(435, 430), (615, 430)], label="SlotStarted", lx=525, ly=418)
    s += poly([(825, 430), (1005, 430)], label="Called", lx=915, ly=418)
    s += poly([(1215, 410), (1280, 410), (1280, 230), (1395, 230)], label="Passed", lx=1288, ly=320, anchor="start")
    s += poly([(1215, 430), (1395, 430)], label="Rework", lx=1305, ly=418)
    s += poly([(1215, 450), (1280, 450), (1280, 630), (1395, 630)],
              label="NoShow [≥ 5 мин после вызова]|/ письмо «Запись аннулирована»", lx=1288, ly=560, anchor="start")
    s += poly([(330, 467), (330, 720), (615, 720)], label="Cancel [≥ 60 мин]|/ продвинуть следующего",
              lx=345, ly=690, anchor="start")
    s += poly([(400, 227), (540, 227), (540, 690), (615, 690)], label="Cancel", lx=548, ly=500, anchor="start")
    for x, y in [(1650, 230), (1650, 430), (1650, 630), (850, 720), (850, 190)]:
        s += final(x, y)
    for x1, x2, y in [(1605, 1638, 230), (1605, 1638, 430), (1605, 1638, 630), (825, 838, 720), (825, 838, 190)]:
        s += poly([(x1, y), (x2, y)], arrow=None)
    s += ('<rect x="40" y="800" width="1620" height="70" fill="#fff8c4" stroke="#d6c45a"/>'
          + txt(60, 820, "minutesToStart — минут до начала слота. Окно записи закрывается за 60 минут до начала, лист ожидания замораживается за 30 минут (REQ-FUNC-007, REQ-FUNC-010).", "sm", anchor="start")
          + txt(60, 840, "Rework не закрывает ЛР: студент записывается снова. Неявка (NoShow) возможна только через 5 минут после вызова (REQ-FUNC-020).", "sm", anchor="start")
          + txt(60, 860, "События SeatFreed, SlotStarted, Called, Passed, Rework, NoShow и Cancel порождают действия пользователей и планировщика.", "sm", anchor="start"))
    return page("stm Жизненный цикл записи (Booking)", W, H, s)


def state_slot():
    W, H = 1560, 780
    s = ('<rect x="30" y="50" width="1500" height="280" rx="22" fill="#fafafa" stroke="#555" stroke-width="1.6"/>'
         + txt(55, 75, "Planned", "nm", anchor="start")
         + txt(150, 75, "слот опубликован и ещё не начался", "sm", anchor="start"))
    s += '<circle cx="80" cy="185" r="10" fill="#333"/>'
    s += state(250, 210, "Scheduled", "создан, запись|ещё не открыта", w=230, fill="#f3f4f6", stroke="#6b7280")
    s += state(600, 210, "Open", "запись открыта", w=230, fill="#eff6ff", stroke="#2563eb")
    s += state(950, 210, "RegistrationClosed", "новых записей нет,|лист продвигается", w=250, fill="#fffbeb", stroke="#d97706")
    s += state(1310, 210, "Frozen", "лист ожидания|заморожен", w=230, fill="#f0f9ff", stroke="#0891b2")
    s += poly([(90, 190), (135, 190)])
    s += poly([(365, 210), (485, 210)], label="after 7 суток|до начала", lx=425, ly=180)
    s += poly([(715, 210), (825, 210)], label="after 1 ч|до начала", lx=770, ly=180)
    s += poly([(1075, 210), (1195, 210)], label="after 30 мин|до начала", lx=1135, ly=180)
    s += state(1310, 520, "InProgress", "идёт защита,|ведётся живая очередь", w=250, fill="#fff7ed", stroke="#ea580c")
    s += state(1310, 700, "Finished", "слот завершён", w=230, fill="#f0fdf4", stroke="#16a34a", h=64)
    s += state(520, 520, "Cancelled", "слот отменён, записи|отменены, письма|отправлены", w=260, h=96, fill="#f3f4f6", stroke="#6b7280")
    s += poly([(1310, 330), (1310, 483)], label="start [не раньше чем|за 15 мин до начала]", lx=1322, ly=410, anchor="start")
    s += poly([(1310, 557), (1310, 668)], label="finish", lx=1322, ly=620, anchor="start")
    s += poly([(520, 330), (520, 472)], label="cancel(reason)", lx=532, ly=410, anchor="start")
    s += final(1310, 760) + poly([(1310, 732), (1310, 748)], arrow=None)
    s += final(520, 620) + poly([(520, 568), (520, 608)], arrow=None)
    s += ('<rect x="40" y="665" width="880" height="86" fill="#fff8c4" stroke="#d6c45a"/>'
          + txt(55, 688, "Переходы внутри Planned выполняет планировщик по времени (UC-16);", "sm", anchor="start")
          + txt(55, 708, "отмена возможна из любого состояния Planned, поэтому показана один раз от составного состояния.", "sm", anchor="start")
          + txt(55, 728, "В состоянии Frozen продвижение листа ожидания не выполняется (UC-15, поток A2).", "sm", anchor="start"))
    return page("stm Состояния слота (Slot)", W, H, s)


# ------------------------------------------------------------------ последовательность
def M(f, to, text, ret=False, self_=False):
    return {"t": "msg", "f": f, "to": to, "text": text, "ret": ret, "self": self_}


def sequence(title, parts, events, W, notes=None, ystart=125, step=46):
    n = len(parts)
    gap = (W - 40) / n
    xs = [20 + gap * (i + 0.5) for i in range(n)]
    ys, y = [], ystart
    for e in events:
        y += step * (0.55 if e["t"] in ("alt", "else", "end") else (1.15 if e.get("self") else 1))
        ys.append(y)
    bottom = y + 45
    s = ""
    for (name, stereo), x in zip(parts, xs):
        bw = min(gap - 14, 175)
        s += (f'<rect x="{x - bw / 2}" y="20" width="{bw}" height="58" rx="3" fill="#fff" stroke="#333" stroke-width="1.5"/>'
              + txt(x, 40, f"«{stereo}»", "st") + txt(x, 63, name, "nm", style="font-size:15px"))
        s += f'<line x1="{x}" y1="78" x2="{x}" y2="{bottom}" stroke="#777" stroke-dasharray="6 5"/>'
    stacks = {i: [] for i in range(n)}
    bars = []
    for e, yy in zip(events, ys):
        if e["t"] != "msg":
            continue
        a, b = e["f"], e["to"]
        if e.get("ret"):
            if stacks[a]:
                y0, d = stacks[a].pop()
                bars.append((a, y0, yy, d))
        else:
            stacks[b].append((yy - 8, len(stacks[b])))
    for i, st in stacks.items():
        for y0, d in st:
            bars.append((i, y0, y0 + 22, d))
    for i, y0, y1, d in bars:
        s += (f'<rect x="{xs[i] - 6 + d * 7}" y="{y0}" width="12" height="{y1 - y0 + 8}" fill="#fff" '
              f'stroke="#333" stroke-width="1.4"/>')
    frames = []
    for e, yy in zip(events, ys):
        if e["t"] == "alt":
            frames.append({"y0": yy, "label": e["label"], "span": e["span"], "else": []})
        elif e["t"] == "else":
            frames[-1]["else"].append((yy, e["label"]))
        elif e["t"] == "end":
            frames[-1]["y1"] = yy
    for fr in frames:
        x0, x1 = xs[fr["span"][0]] - 70, xs[fr["span"][1]] + 70
        s += (f'<rect x="{x0}" y="{fr["y0"] - 12}" width="{x1 - x0}" height="{fr["y1"] - fr["y0"] + 18}" fill="none" '
              f'stroke="#555" stroke-width="1.4"/><rect x="{x0}" y="{fr["y0"] - 12}" width="46" height="22" fill="#e2e8f0" '
              f'stroke="#555"/>' + txt(x0 + 23, fr["y0"] - 1, "alt", "nm", style="font-size:14px")
              + txt(x0 + 54, fr["y0"] - 1, fr["label"], "lb", anchor="start", style="font-size:13.5px"))
        for ey, el in fr["else"]:
            s += (f'<line x1="{x0}" y1="{ey - 8}" x2="{x1}" y2="{ey - 8}" stroke="#555" stroke-dasharray="6 4"/>'
                  + txt(x0 + 8, ey + 4, el, "lb", anchor="start", style="font-size:13.5px"))
    for e, yy in zip(events, ys):
        if e["t"] != "msg":
            continue
        a, b = e["f"], e["to"]
        if e.get("self"):
            x = xs[a] + 6
            s += poly([(x, yy - 12), (x + 44, yy - 12), (x + 44, yy + 8), (x + 6, yy + 8)])
            s += txt(x + 52, yy - 2, e["text"], "lb", anchor="start", style="font-size:13.5px")
            continue
        sgn = 1 if xs[b] > xs[a] else -1
        x1, x2 = xs[a] + sgn * 7, xs[b] - sgn * 7
        s += poly([(x1, yy), (x2, yy)], "dashed" if e.get("ret") else "solid", "open" if e.get("ret") else "full")
        s += txt((x1 + x2) / 2, yy - 8, e["text"], "lb", style="font-size:13.5px")
    if notes:
        s += notes(xs, ys)
    return page(title, W, int(bottom) + 20, s), int(bottom) + 20


def red(tx, ty, text, x, y, anchor="start"):
    w = len(text) * 7.4
    sx = tx + w + 6 if x > tx + w else tx - 6
    return poly([(sx, ty - 5), (x, y)], arrow="red", color="#c00000") + txt(tx, ty - 7, text, "red", anchor)


SEQ_BOOKING = dict(
    parts=[("Студент", "actor"), ("BookingController", "web"), ("BookingService", "booking"),
           ("QueuePolicy", "queue"), ("PostgreSQL", "db")],
    events=[M(0, 1, "POST /slots/7/bookings (lab=3, ack=true)"), M(1, 2, "book(slot=7, student, lab=3, ack)"),
            M(2, 4, "BEGIN; SELECT slot WHERE id=7 FOR UPDATE"), M(4, 2, "слот и записи", True),
            M(2, 3, "validate(slot, student, lab, now)"), M(3, 3, "проверить окно, лимит, ЛР", self_=True),
            M(3, 2, "OK", True),
            {"t": "alt", "label": "[есть свободные места]", "span": (2, 4)},
            M(2, 3, "assignPosition(slot)"), M(3, 2, "position = 9", True),
            M(2, 4, "INSERT booking (BOOKED, position, ack_at)"),
            {"t": "else", "label": "[мест нет, лист ожидания < 3]"},
            M(2, 4, "INSERT booking (WAITLISTED)"),
            {"t": "end"},
            M(2, 4, "INSERT journal; INSERT outbox_email; COMMIT"), M(4, 2, "ok", True),
            M(2, 1, "BookingResult (status, position, eta)", True), M(1, 0, "303 → /my", True)])

SEQ_NOSHOW = dict(
    parts=[("Преподаватель", "actor"), ("LiveController", "web"), ("QueueService", "queue"),
           ("QueuePolicy", "queue"), ("PostgreSQL", "db"), ("Студент", "actor")],
    events=[M(0, 1, "POST /teacher/bookings/57/mark (NO_SHOW)"), M(1, 2, "mark(57, NO_SHOW, teacher)"),
            M(2, 4, "BEGIN; SELECT slot FOR UPDATE; SELECT booking 57"), M(4, 2, "booking (called_at = 10:21)", True),
            M(2, 3, "minutesSinceCall(called_at, now)"), M(3, 2, "n минут", True),
            {"t": "alt", "label": "[n ≥ 5]", "span": (2, 4)},
            M(2, 4, "UPDATE booking → NO_SHOW; INSERT journal; INSERT outbox"),
            M(2, 3, "callNext(slot)"), M(3, 2, "следующий, позиция 4", True),
            M(2, 4, "UPDATE booking SET called_at; COMMIT"),
            {"t": "else", "label": "[n < 5]"},
            M(2, 1, "409: ждать ещё (5 − n) мин", True),
            {"t": "end"},
            M(2, 1, "MarkResult", True), M(1, 0, "200: очередь обновлена", True),
            M(5, 1, "GET /api/slots/7/queue (If-None-Match)"), M(1, 5, "200 JSON: «вы следующий», позиция 4", True)])


def seq_booking():
    def notes(xs, ys):
        return (red(30, 112, "Участник", 100, 80)
                + red(150, 285, "Линия жизни", 127, 280)
                + red(140, 262, "Синхронное сообщение", 255, 176)
                + red(150, 345, "Полоса активации", 332, 345)
                + red(140, ys[7] + 30, "Фрагмент alt", 478, ys[7] - 2)
                + red(840, ys[5] + 38, "Сообщение самому себе", 812, ys[5] + 8)
                + red(130, ys[17] + 30, "Сообщение-возврат", 300, ys[17] + 3))
    html, h = sequence("sd Запись на защиту (UC-04, UC-05)", SEQ_BOOKING["parts"], SEQ_BOOKING["events"], 1100, notes)
    return html, h


def seq_noshow():
    html, h = sequence("sd Неявка студента (UC-09, UC-11, UC-07)", SEQ_NOSHOW["parts"], SEQ_NOSHOW["events"], 1250)
    return html, h

FIGS = {"uml-use-cases": (use_cases, 1380, 1050),
        "uml-activity-booking": (activity_booking, 1200, 1130),
        "uml-activity-cancel": (activity_cancel, 1220, 1130),
        "uml-state-booking": (state_booking, 1740, 950),
        "uml-state-slot": (state_slot, 1600, 830),
        "uml-seq-booking": (seq_booking, 1140, 0),
        "uml-seq-noshow": (seq_noshow, 1290, 0)}


def build(name):
    fn, w, h = FIGS[name]
    r = fn()
    if isinstance(r, tuple):
        return r[0], w, r[1] + 40
    return r, w, h


def main():
    OUT.mkdir(exist_ok=True)
    exe = find_browser()
    only = sys.argv[1:]
    with tempfile.TemporaryDirectory() as tmp:
        for name in FIGS:
            if only and name not in only:
                continue
            html, w, h = build(name)
            src = Path(tmp) / f"{name}.html"
            src.write_text(f"<!doctype html><meta charset='utf-8'><style>{CSS}</style><body>{html}</body>",
                           encoding="utf-8")
            out = OUT / f"{name}.png"
            subprocess.run([exe, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
                            f"--user-data-dir={Path(tmp) / 'profile'}", f"--force-device-scale-factor={SCALE}",
                            f"--window-size={w},{h}", f"--screenshot={out}", src.as_uri()],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print(f"images/{name}.png ({w * SCALE}, {h * SCALE})")


if __name__ == "__main__":
    main()
