"""Рендер UML-диаграмм LabQueue: SVG (sdd/images/uml-*.svg) и PNG (sdd/images/uml-*.png).

Диаграммы описаны текстом PlantUML в файлах *.puml этого каталога; изображения строит
этот скрипт: геометрия каждой диаграммы задана ниже, а имена, подписи и переходы
совпадают с *.puml. SVG сохраняется как самостоятельный файл (с xmlns, встроенными
стилями и белым фоном), PNG снимает headless Chrome или Edge.

Графика строгая чёрно-белая (белая заливка, чёрные линии одной толщины); цветом выделены
только красные поясняющие выноски на диаграмме последовательности записи.

В диаграмме прецедентов каждая связь — один прямой отрезок; скрипт проверяет, что ни один
отрезок не пересекает чужой овал, чужого актёра и другой отрезок, и при нарушении
завершается с ошибкой. В диаграммах последовательности скрипт проверяет, что оба конца
каждого сообщения лежат на полосах активации.

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
from render import CSS, find_browser  # noqa: E402,F401

try:
    from PIL import Image, ImageFont
except ImportError:  # без Pillow: PNG не обрезается, ширина текста оценивается
    Image = ImageFont = None

SCALE = 2
FONT = "'Segoe UI',Arial,Helvetica,sans-serif"
STYLE = f"""
text{{font-family:{FONT};fill:#000;font-size:15px}}
.nm{{font-size:16px;font-weight:700}}.st{{font-size:14px}}.lb{{font-size:14.5px}}.sm{{font-size:14px}}
.red{{font-size:14.5px;fill:#c00000;font-weight:600}}
.ln{{stroke:#000;stroke-width:1.5;fill:none}}.dl{{stroke:#000;stroke-width:1.5;fill:none;stroke-dasharray:7 5}}
.mt{{stroke:#fff;stroke-width:5px;paint-order:stroke;stroke-linejoin:round}}
.sh{{fill:#fff;stroke:#000;stroke-width:1.5}}
"""
DEFS = ('<defs><marker id="ar" viewBox="0 0 10 10" refX="9.5" refY="5" markerWidth="10" markerHeight="10" '
        'orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#000"/></marker>'
        '<marker id="oa" viewBox="0 0 10 10" refX="9.5" refY="5" markerWidth="11" markerHeight="11" orient="auto">'
        '<path d="M0.5,0.5 L9.5,5 L0.5,9.5" fill="none" stroke="#000" stroke-width="1.5"/></marker>'
        '<marker id="ra" viewBox="0 0 10 10" refX="9.5" refY="5" markerWidth="9" markerHeight="9" orient="auto">'
        '<path d="M0,0 L10,5 L0,10 z" fill="#c00000"/></marker></defs>')

_FONTS = {}


def measure(text, size=14.5, bold=False):
    """Ширина строки в пикселях (по шрифту Segoe UI, если он есть)."""
    key = (size, bold)
    if key not in _FONTS and ImageFont is not None:
        try:
            _FONTS[key] = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeui.ttf",
                                             size * 4)
        except OSError:
            _FONTS[key] = None
    f = _FONTS.get(key)
    if f is None:
        return len(text) * size * 0.56
    return f.getlength(text) / 4


def page(tab, w, h, inner):
    """Полотно w x h внутри рамки диаграммы UML с ярлыком tab; возвращает (svg, ширина, высота)."""
    tw = 22 + measure(tab, 15, True)
    W, H = w + 20, h + 60
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
           f'<style>{STYLE}</style>{DEFS}<rect width="{W}" height="{H}" fill="#fff"/>'
           f'<rect x="10" y="10" width="{w}" height="{h + 40}" class="sh"/>'
           f'<polygon points="10,10 {10 + tw},10 {10 + tw},26 {10 + tw - 12},38 10,38" class="sh"/>'
           f'<text x="18" y="29" class="nm" style="font-size:15px">{esc(tab)}</text>'
           f'<g transform="translate(10,50)">{inner}</g></svg>')
    return svg, W, H


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def txt(x, y, text, cls="", anchor="middle", dy=17, style=""):
    """Текст; '|' разделяет строки, блок центрируется по вертикали относительно y."""
    parts = text.split("|")
    y0 = y - (len(parts) - 1) * dy / 2
    out = ""
    for i, p in enumerate(parts):
        out += (f'<text x="{x}" y="{y0 + i * dy + 5}" text-anchor="{anchor}" class="{cls}" '
                f'style="{style}">{esc(p)}</text>')
    return out


def poly(pts, kind="solid", arrow="full", label=None, lx=None, ly=None, anchor="middle", cls="lb"):
    d = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
    m = {"full": ' marker-end="url(#ar)"', "open": ' marker-end="url(#oa)"',
         "red": ' marker-end="url(#ra)"', None: ""}[arrow]
    c = "dl" if kind == "dashed" else "ln"
    extra = ' style="stroke:#c00000"' if arrow == "red" else ""
    s = f'<polyline points="{d}" class="{c}"{m}{extra}/>'
    if label:
        s += txt(lx, ly, label, cls, anchor)
    return s


def init(cx, cy):
    return f'<circle cx="{cx}" cy="{cy}" r="10" fill="#000"/>'


def final(cx, cy):
    return (f'<circle cx="{cx}" cy="{cy}" r="12" class="sh"/>'
            f'<circle cx="{cx}" cy="{cy}" r="7" fill="#000"/>')


def note(x, y, w, h, lines, cls="sm", dy=18):
    """Примечание UML: белый прямоугольник с загнутым углом."""
    pts = f"{x},{y} {x + w - 14},{y} {x + w},{y + 14} {x + w},{y + h} {x},{y + h}"
    return (f'<polygon points="{pts}" class="sh"/>'
            f'<polyline points="{x + w - 14},{y} {x + w - 14},{y + 14} {x + w},{y + 14}" class="ln"/>'
            + "".join(txt(x + 10, y + 20 + i * dy - 5, t, cls, anchor="start") for i, t in enumerate(lines)))


# ------------------------------------------------------------------ актёры
def stick(x, y, lines, bold=True):
    """Человечек с центром тела (x, y); подпись под ним (строки через '|')."""
    s = (f'<circle cx="{x}" cy="{y - 38}" r="11" class="sh"/>'
         f'<line x1="{x}" y1="{y - 27}" x2="{x}" y2="{y + 6}" class="ln"/>'
         f'<line x1="{x - 19}" y1="{y - 16}" x2="{x + 19}" y2="{y - 16}" class="ln"/>'
         f'<line x1="{x}" y1="{y + 6}" x2="{x - 14}" y2="{y + 30}" class="ln"/>'
         f'<line x1="{x}" y1="{y + 6}" x2="{x + 14}" y2="{y + 30}" class="ln"/>')
    ls = lines.split("|")
    for i, t in enumerate(ls):
        st = "font-size:14px;font-weight:400" if t.startswith(("«", "(")) else "font-size:15px"
        s += f'<text x="{x}" y="{y + 50 + i * 17}" text-anchor="middle" class="nm" style="{st}">{esc(t)}</text>'
    return s


# ------------------------------------------------------------------ прецеденты
RX, RY = 105, 42


def ell(cx, cy, text):
    return (f'<ellipse cx="{cx}" cy="{cy}" rx="{RX}" ry="{RY}" class="sh"/>'
            + txt(cx, cy, text, "lb", style="font-size:15px", dy=17))


def edge_pt(cx, cy, tx, ty):
    """Точка на границе овала (cx, cy) в направлении точки (tx, ty)."""
    dx, dy = tx - cx, ty - cy
    k = 1 / math.sqrt((dx / RX) ** 2 + (dy / RY) ** 2)
    return cx + dx * k, cy + dy * k


def seg_cross(p1, p2, p3, p4):
    """Пересекаются ли отрезки p1p2 и p3p4 строго внутри (общие концы не считаются)."""
    def orient(a, b, c):
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    d1, d2 = orient(p3, p4, p1), orient(p3, p4, p2)
    d3, d4 = orient(p1, p2, p3), orient(p1, p2, p4)
    eps = 1e-6
    return d1 * d2 < -eps and d3 * d4 < -eps


def use_cases():
    W, H = 1430, 860
    DY = -100
    U = {  # id: (cx, cy, текст)
        "01": (710, 495, "UC-01 Войти|в систему"),
        "07": (270, 205, "UC-07 Следить за|позицией в очереди"),
        "02": (340, 300, "UC-02|Зарегистрироваться"),
        "03": (362, 395, "UC-03 Просмотреть|слоты"),
        "04": (362, 590, "UC-04 Записаться|на защиту"),
        "06": (322, 685, "UC-06 Отменить или|перенести запись"),
        "13": (710, 190, "UC-13 Выгрузить|отчёт в Excel"),
        "10": (710, 285, "UC-10 Вызвать|вне очереди"),
        "11": (710, 380, "UC-11 Отметить|неявку"),
        "05": (710, 590, "UC-05 Встать в|лист ожидания"),
        "15": (710, 685, "UC-15 Продвинуть|из листа ожидания"),
        "12": (1140, 190, "UC-12 Просмотреть|ведомость и отчёт|о записи"),
        "08": (1090, 285, "UC-08 Управлять|слотами и сериями"),
        "09": (1090, 380, "UC-09 Вести|живую очередь"),
        "14": (1090, 610, "UC-14 Управлять|преподавателями|и группами"),
        "17": (1090, 710, "UC-17 Отправить|уведомления"),
        "16": (1090, 810, "UC-16 Заморозить|лист ожидания"),
    }
    # актёры: (x, y центра тела, подпись, число строк подписи)
    A = {
        "S": (70, 500, "Студент"),
        "T": (1330, 430, "Преподаватель"),
        "AD": (1330, 570, "Администратор"),
        "MS": (1330, 735, "«system»|Почтовый сервер"),
        "TM": (1330, 880, "Время|(Планировщик)"),
    }
    U = {k: (x, y + DY, t) for k, (x, y, t) in U.items()}
    A = {k: (x, y + DY, t) for k, (x, y, t) in A.items()}
    s = ('<rect x="130" y="10" width="1110" height="800" class="sh"/>'
         + txt(145, 32, "«subject» LabQueue", "nm", anchor="start"))
    for k, (cx, cy, t) in U.items():
        s += ell(cx, cy, t)
    boxes = {}
    for k, (x, y, name) in A.items():
        s += stick(x, y, name)
        n = name.count("|") + 1
        wmax = max(measure(p, 15, True) for p in name.split("|"))
        boxes[k] = (x - max(wmax / 2, 22), y - 52, x + max(wmax / 2, 22), y + 52 + 17 * n)

    # связи: (откуда, куда, вид, стрелка, подпись, x, y подписи, якорь)
    links = []
    for u in ("02", "03", "04", "06", "07", "01"):
        links.append(("S", u, "solid", "open", None))
    for u in ("08", "09", "12", "01"):
        links.append(("T", u, "solid", "open", None))
    for u in ("14", "01"):
        links.append(("AD", u, "solid", "open", None))
    links.append(("TM", "16", "solid", "open", None))
    links.append(("TM", "17", "solid", "open", None))
    links.append(("17", "MS", "solid", "open", None))
    links += [("05", "04", "dashed", "open", "«extend»|[мест нет]"),
              ("10", "09", "dashed", "open", "«extend»|[причина указана]"),
              ("11", "09", "dashed", "open", "«extend»|[прошло ≥ 5 мин]"),
              ("13", "12", "dashed", "open", "«extend»"),
              ("06", "15", "dashed", "open", "«include»"),
              ("15", "17", "dashed", "open", "«include»")]

    def center(k):
        return U[k][:2] if k in U else None

    def anchor(k, other):
        x, y, _ = A[k]
        ox = (U[other][0] if other in U else A[other][0])
        return (x + 19, y - 16) if ox > x else (x - 19, y - 16)

    segs = []
    for a, b, kind, arrow, label in links:
        if a in A:
            p1 = anchor(a, b)
            p2 = edge_pt(*U[b][:2], *p1)
        elif b in A:
            p2 = anchor(b, a)
            p1 = edge_pt(*U[a][:2], *p2)
        else:
            p1 = edge_pt(*U[a][:2], *U[b][:2])
            p2 = edge_pt(*U[b][:2], *U[a][:2])
        segs.append((a, b, p1, p2, kind, arrow, label))

    # ---- проверка: ни одной пересечённой фигуры и ни одного пересечения связей
    bad = []
    for a, b, p1, p2, *_ in segs:
        n = 400
        for i in range(n + 1):
            x = p1[0] + (p2[0] - p1[0]) * i / n
            y = p1[1] + (p2[1] - p1[1]) * i / n
            for k, (cx, cy, _) in U.items():
                if k in (a, b):
                    continue
                if ((x - cx) / (RX + 4)) ** 2 + ((y - cy) / (RY + 4)) ** 2 < 1:
                    bad.append(f"связь {a}-{b} пересекает овал UC-{k}")
                    break
            for k, (x0, y0, x1, y1) in boxes.items():
                if k in (a, b):
                    continue
                if x0 - 4 < x < x1 + 4 and y0 - 4 < y < y1 + 4:
                    bad.append(f"связь {a}-{b} пересекает актёра {k}")
                    break
    for i in range(len(segs)):
        for j in range(i + 1, len(segs)):
            if seg_cross(segs[i][2], segs[i][3], segs[j][2], segs[j][3]):
                bad.append(f"связи {segs[i][0]}-{segs[i][1]} и {segs[j][0]}-{segs[j][1]} пересекаются")
    if bad:
        sys.exit("Диаграмма прецедентов: " + "; ".join(sorted(set(bad))))
    print(f"  проверка прецедентов: {len(segs)} связей, пересечений нет")

    LBL = {("05", "04"): (0, -22), ("10", "09"): (-20, -42), ("11", "09"): (0, -22), ("13", "12"): (0, -12),
           ("06", "15"): (0, -12), ("15", "17"): (0, -12)}
    for a, b, p1, p2, kind, arrow, label in segs:
        s += poly([p1, p2], kind, arrow)
        if label:
            dx, dy = LBL[(a, b)]
            s += txt((p1[0] + p2[0]) / 2 + dx, (p1[1] + p2[1]) / 2 + dy, label, "lb", style="font-size:14px", dy=16)
    s += note(560, 740, 290, 56, ["UC-02, UC-11 и UC-14 ставят письма", "в очередь отправки (UC-17)"])
    return page("uc LabQueue", W, H, s)


# ------------------------------------------------------------------ активность
def act_box(cx, cy, text, w=250, h=54):
    return (f'<rect x="{cx - w / 2}" y="{cy - h / 2}" width="{w}" height="{h}" rx="14" class="sh"/>'
            + txt(cx, cy, text, "lb", style="font-size:14.5px", dy=17))


def dec(cx, cy, text, w=230, h=84):
    return (f'<polygon points="{cx},{cy - h / 2} {cx + w / 2},{cy} {cx},{cy + h / 2} {cx - w / 2},{cy}" class="sh"/>'
            + txt(cx, cy, text, "lb", style="font-size:14.5px", dy=17))


def bar(x1, x2, y):
    return f'<rect x="{x1}" y="{y - 4}" width="{x2 - x1}" height="8" fill="#000"/>'


def lanes(names, xs, h):
    s = ""
    for n, (a, b) in zip(names, xs):
        s += (f'<rect x="{a}" y="10" width="{b - a}" height="{h}" fill="none" class="ln"/>'
              f'<rect x="{a}" y="10" width="{b - a}" height="36" class="sh"/>'
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
    s += dec(600, 615, "Есть свободные|места?")
    s += act_box(600, 725, "Создать запись BOOKED,|присвоить позицию", 260)
    s += act_box(930, 725, "Создать запись WAITLISTED|(лист ожидания)", 250)
    s += act_box(600, 860, "Записать в журнал,|поставить письмо в outbox", 270)
    s += act_box(220, 945, "Увидеть позицию|и расчётное время", 240)
    s += final(220, 1030)
    s += poly([(220, 100), (220, 123)])
    s += poly([(220, 177), (220, 240), (470, 240)])
    s += poly([(600, 267), (600, 293)])
    s += poly([(715, 335), (825, 335)], label="нет", lx=770, ly=323)
    s += poly([(930, 362), (930, 413)])
    s += poly([(600, 377), (600, 425), (365, 425)], label="да", lx=612, ly=403, anchor="start")
    s += poly([(220, 452), (220, 515), (470, 515)])
    s += poly([(600, 542), (600, 573)])
    s += poly([(600, 657), (600, 698)], label="да", lx=612, ly=681, anchor="start")
    s += poly([(715, 615), (930, 615), (930, 698)], label="нет [лист ожидания < 3]", lx=822, ly=601)
    s += poly([(600, 752), (600, 833)])
    s += poly([(930, 752), (930, 860), (735, 860)])
    s += poly([(465, 860), (220, 860), (220, 918)])
    s += poly([(220, 972), (220, 1018)])
    return page("act Запись на защиту (UC-04, UC-05)", W, H, s)


def activity_cancel():
    W, H = 1180, 1090
    s = lanes(["Студент", "Система", "Планировщик"], [(20, 370), (370, 900), (900, 1160)], H - 20)
    s += init(195, 90)
    s += act_box(195, 150, "Нажать «Отменить»|и подтвердить", 230)
    s += dec(600, 245, "До начала|≥ 60 мин?", w=200)
    s += act_box(815, 245, "Отказать:|«Сообщите|преподавателю»", 150, 66)
    s += final(815, 345)
    s += act_box(600, 345, "Заблокировать слот, запись|→ CANCELLED", 250)
    s += act_box(600, 435, "Записать в журнал", 220)
    s += dec(600, 535, "До начала > 30 мин|и лист не пуст?", 250)
    s += act_box(600, 635, "Перевести первого из листа|в BOOKED", 250)
    s += bar(490, 1025, 695)
    s += act_box(490, 765, "Пересчитать позиции|и расчётное время", 200)
    s += act_box(1025, 765, "Отправить письмо|«Вы записаны» (SMTP)", 210)
    s += bar(490, 1025, 840)
    s += act_box(195, 965, "Получить уведомление,|увидеть новый статус", 250)
    s += final(195, 1045)
    s += poly([(195, 100), (195, 123)])
    s += poly([(195, 177), (195, 245), (500, 245)])
    s += poly([(700, 245), (740, 245)], label="нет", lx=720, ly=232)
    s += poly([(815, 278), (815, 333)])
    s += poly([(600, 287), (600, 318)], label="да", lx=612, ly=306, anchor="start")
    s += poly([(600, 372), (600, 408)])
    s += poly([(600, 462), (600, 493)])
    s += poly([(600, 577), (600, 608)], label="да", lx=612, ly=596, anchor="start")
    s += poly([(600, 662), (600, 691)])
    s += poly([(490, 699), (490, 738)])
    s += poly([(1025, 699), (1025, 738)])
    s += poly([(490, 792), (490, 836)])
    s += poly([(1025, 792), (1025, 836)])
    s += poly([(600, 844), (600, 900), (195, 900), (195, 938)])
    s += poly([(725, 535), (1148, 535), (1148, 965), (320, 965)], label="нет", lx=745, ly=522)
    s += poly([(195, 992), (195, 1033)])
    return page("act Отмена записи и продвижение (UC-06, UC-15)", W, H, s)


# ------------------------------------------------------------------ состояния
def state(cx, cy, name, lines="", w=200, h=78):
    s = (f'<rect x="{cx - w / 2}" y="{cy - h / 2}" width="{w}" height="{h}" rx="16" class="sh"/>'
         + txt(cx, cy - h / 2 + 20, name, "nm", style="font-size:16px"))
    if lines:
        s += f'<line x1="{cx - w / 2}" y1="{cy - h / 2 + 33}" x2="{cx + w / 2}" y2="{cy - h / 2 + 33}" class="ln"/>'
        s += txt(cx, cy + 15, lines, "sm", dy=16)
    return s


def state_booking():
    W, H = 1700, 820
    s = ""
    s += state(470, 140, "Waitlisted", "в листе ожидания|(FIFO)")
    s += state(470, 430, "Booked", "место закреплено,|позиция и ETA")
    s += state(810, 140, "Expired", "лист не дошёл|до студента")
    s += state(810, 285, "Cancelled", "запись отменена,|место освобождено")
    s += state(810, 430, "InQueue", "слот начат,|ждёт вызова")
    s += state(1150, 430, "Defending", "вызван,|защищается")
    s += state(1490, 260, "Passed", "ЛР защищена")
    s += state(1490, 430, "Rework", "на доработку,|нужна новая запись")
    s += state(1490, 600, "NoShow", "запись аннулирована")
    # начальный узел и выбор с вопросом внутри ромба
    s += init(50, 285)
    s += ('<polygon points="190,230 290,285 190,340 90,285" class="sh"/>'
          + txt(190, 285, "Есть свободные|места?", "lb", dy=17))
    s += poly([(60, 285), (90, 285)])
    s += poly([(190, 230), (190, 140), (370, 140)], label="[нет]", lx=278, ly=126)
    s += poly([(190, 340), (190, 430), (370, 430)], label="[да]", lx=278, ly=416)
    s += poly([(470, 179), (470, 391)], label="SeatFreed [> 30 мин до|начала, первый в листе]|/ письмо «Вы записаны»",
              lx=458, ly=285, anchor="end")
    s += poly([(570, 140), (710, 140)], label="SlotStarted", lx=640, ly=126)
    s += poly([(570, 430), (710, 430)], label="SlotStarted", lx=640, ly=416)
    s += poly([(540, 179), (540, 265), (710, 265)], label="Cancel", lx=550, ly=225, anchor="start")
    s += poly([(540, 391), (540, 305), (710, 305)], label="Cancel [≥ 60 мин]|/ продвинуть|следующего",
              lx=550, ly=355, anchor="start")
    s += poly([(910, 430), (1050, 430)], label="Called", lx=980, ly=416)
    s += poly([(1150, 391), (1150, 260), (1390, 260)], label="Passed", lx=1270, ly=246)
    s += poly([(1250, 430), (1390, 430)], label="Rework", lx=1320, ly=416)
    s += poly([(1150, 469), (1150, 600), (1390, 600)],
              label="NoShow [≥ 5 мин|после вызова]|/ письмо «Запись аннулирована»", lx=1160, ly=558, anchor="start")
    for x, y, x0 in [(1650, 260, 1590), (1650, 430, 1590), (1650, 600, 1590), (960, 285, 910), (960, 140, 910)]:
        s += final(x if x0 > 1000 else x, y) + poly([(x0, y), (x - 12, y)], arrow=None)
    s += note(40, 700, 1620, 90, [
        "minutesToStart — минут до начала слота. Окно записи закрывается за 60 минут до начала, лист ожидания замораживается за 30 минут (REQ-FUNC-007, REQ-FUNC-010).",
        "Rework не закрывает ЛР: студент записывается снова. Неявка (NoShow) возможна только через 5 минут после вызова (REQ-FUNC-020).",
        "События SeatFreed, SlotStarted, Called, Passed, Rework, NoShow и Cancel порождают действия пользователей и планировщика."])
    return page("stm Жизненный цикл записи (Booking)", W, H, s)


def state_slot():
    W, H = 1560, 780
    s = ('<rect x="30" y="50" width="1500" height="280" rx="22" class="sh"/>'
         + txt(55, 75, "Planned", "nm", anchor="start")
         + txt(150, 75, "слот опубликован и ещё не начался", "sm", anchor="start"))
    s += init(80, 185)
    s += state(250, 210, "Scheduled", "создан, запись|ещё не открыта", w=230)
    s += state(600, 210, "Open", "запись открыта", w=230)
    s += state(950, 210, "RegistrationClosed", "новых записей нет,|лист продвигается", w=250)
    s += state(1310, 210, "Frozen", "лист ожидания|заморожен", w=230)
    s += poly([(90, 190), (135, 190)])
    s += poly([(365, 210), (485, 210)], label="after 7 суток|до начала", lx=425, ly=180)
    s += poly([(715, 210), (825, 210)], label="after 1 ч|до начала", lx=770, ly=180)
    s += poly([(1075, 210), (1195, 210)], label="after 30 мин|до начала", lx=1135, ly=180)
    s += state(1310, 520, "InProgress", "идёт защита,|ведётся живая очередь", w=250)
    s += state(1310, 700, "Finished", "слот завершён", w=230, h=64)
    s += state(520, 520, "Cancelled", "слот отменён, записи|отменены, письма|отправлены", w=260, h=96)
    s += poly([(1310, 330), (1310, 481)], label="start [не раньше чем|за 15 мин до начала]", lx=1322, ly=410, anchor="start")
    s += poly([(1310, 559), (1310, 668)], label="finish", lx=1322, ly=620, anchor="start")
    s += poly([(520, 330), (520, 472)], label="cancel(reason)", lx=532, ly=410, anchor="start")
    s += final(1310, 760) + poly([(1310, 732), (1310, 748)], arrow=None)
    s += final(520, 620) + poly([(520, 568), (520, 608)], arrow=None)
    s += note(40, 665, 880, 86, [
        "Переходы внутри Planned выполняет планировщик по времени (UC-16);",
        "отмена возможна из любого состояния Planned, поэтому показана один раз от составного состояния.",
        "В состоянии Frozen продвижение листа ожидания не выполняется (UC-15, поток A2)."])
    return page("stm Состояния слота (Slot)", W, H, s)


# ------------------------------------------------------------------ последовательность
def M(f, to, text, ret=False, self_=False, keep=False, noret=False):
    return {"t": "msg", "f": f, "to": to, "text": text, "ret": ret, "self": self_, "keep": keep, "noret": noret}


def ALT(label, span, dx0=120):
    return {"t": "alt", "label": label, "span": span, "dx0": dx0}


def ELSE(label):
    return {"t": "else", "label": label}


END = {"t": "end"}
BW = 14  # ширина полосы активации
LH = 17  # шаг строки подписи


def sequence(tab, parts, events, W, notes=None, extra_bottom=0):
    n = len(parts)
    gap = (W - 40) / n
    xs = [20 + gap * (i + 0.5) for i in range(n)]
    is_actor = [p[1] == "actor" for p in parts]
    # --- вертикальная раскладка
    last = 150
    ys, frames = [], []
    for e in events:
        if e["t"] == "msg":
            lines = e["text"].count("|") + 1
            y = last + LH * lines + 26
            ys.append(y)
            last = y + (12 if e["self"] else 0)
        elif e["t"] == "alt":
            y0 = last + 22
            gl = e["label"].count("|") + 1
            ys.append(y0)
            frames.append({"y0": y0, "span": e["span"], "dx0": e["dx0"], "label": e["label"], "else": []})
            last = y0 + 24 + LH * gl + 6 - 26
        elif e["t"] == "else":
            ysep = last + 22
            gl = e["label"].count("|") + 1
            ys.append(ysep)
            frames[-1]["else"].append((ysep, e["label"]))
            last = ysep + 6 + LH * gl + 6 - 26
        else:
            y1 = last + 20
            ys.append(y1)
            frames[-1]["y1"] = y1
            last = y1 - 8
    # --- полосы активации
    stacks = [[] for _ in range(n)]
    bars, msgs = [], []

    def open_bar(p, y, auto=False):
        b = {"p": p, "d": len(stacks[p]), "y0": y - 14, "y1": None, "auto": auto, "pend": 0}
        stacks[p].append(b)
        bars.append(b)
        return b

    for e, yy in zip(events, ys):
        if e["t"] != "msg":
            continue
        a, b = e["f"], e["to"]
        if e["self"]:
            sb = stacks[a][-1]
            nb = open_bar(a, yy + 12)
            nb["y0"], nb["y1"] = yy + 2, yy + 26
            stacks[a].pop()
            msgs.append({"e": e, "y": yy, "sb": sb, "rb": nb, "self": True})
        elif not e["ret"]:
            if not stacks[a]:
                open_bar(a, yy, auto=True)
            sb = stacks[a][-1]
            rb = open_bar(b, yy)
            if e["noret"]:
                rb["y1"] = yy + 14
                stacks[b].pop()
            else:
                sb["pend"] += 1
            msgs.append({"e": e, "y": yy, "sb": sb, "rb": rb, "self": False})
        else:
            sb = stacks[a][-1]
            rb = stacks[b][-1]
            msgs.append({"e": e, "y": yy, "sb": sb, "rb": rb, "self": False})
            if not e["keep"]:
                sb["y1"] = yy + 10
                stacks[a].pop()
                rb["pend"] -= 1
                if rb["auto"] and rb["pend"] == 0:
                    rb["y1"] = yy + 10
                    stacks[b].pop()
    if any(stacks):
        sys.exit("Диаграмма последовательности: не закрыта полоса активации")
    bottom = max(b["y1"] for b in bars) + 36 + extra_bottom

    def bx(b):
        return xs[b["p"]] + 7 * b["d"]

    s = ""
    # --- участники и линии жизни
    for i, ((name, stereo), x) in enumerate(zip(parts, xs)):
        if is_actor[i]:
            s += stick(x, 66, name)
            top = 124
        else:
            bw = min(gap - 24, 200)
            s += (f'<rect x="{x - bw / 2}" y="44" width="{bw}" height="58" class="sh"/>'
                  + txt(x, 62, f"«{stereo}»", "st") + txt(x, 85, name, "nm", style="font-size:15.5px"))
            top = 102
        s += f'<line x1="{x}" y1="{top}" x2="{x}" y2="{bottom}" stroke="#000" stroke-width="1.2" stroke-dasharray="6 5"/>'
    # --- фрагменты alt
    for fr in frames:
        x0, x1 = xs[fr["span"][0]] - fr["dx0"], xs[fr["span"][1]] + 70
        y0, y1 = fr["y0"], fr["y1"]
        s += f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="none" class="ln"/>'
        s += f'<polygon points="{x0},{y0} {x0 + 46},{y0} {x0 + 46},{y0 + 12} {x0 + 36},{y0 + 22} {x0},{y0 + 22}" class="sh"/>'
        s += txt(x0 + 21, y0 + 11, "alt", "nm", style="font-size:14px")
        s += txt(x0 + 8, y0 + 22 + 6 + LH / 2 + (fr["label"].count("|")) * LH / 2, fr["label"], "lb", anchor="start", style="font-size:14px")
        for ey, el in fr["else"]:
            s += f'<line x1="{x0}" y1="{ey}" x2="{x1}" y2="{ey}" stroke="#000" stroke-width="1.5" stroke-dasharray="6 4"/>'
            s += txt(x0 + 8, ey + 6 + LH / 2 + el.count("|") * LH / 2, el, "lb", anchor="start", style="font-size:14px")
    # --- полосы
    for b in bars:
        s += (f'<rect x="{bx(b) - BW / 2}" y="{b["y0"]}" width="{BW}" height="{b["y1"] - b["y0"]}" class="sh"/>')
    # --- сообщения
    warn = []
    for m in msgs:
        e, yy, sb, rb = m["e"], m["y"], m["sb"], m["rb"]
        lines = e["text"].split("|")
        if m["self"]:
            xa = bx(sb) + BW / 2
            xb = bx(rb) + BW / 2
            s += poly([(xa, yy - 8), (xa + 38, yy - 8), (xa + 38, yy + 16), (xb, yy + 16)])
            s += txt(xa + 46, yy + 4, e["text"], "lb", anchor="start")
            chk = [(xa, yy - 8, sb), (xb, yy + 16, rb)]
        else:
            sgn = 1 if xs[rb["p"]] > xs[sb["p"]] else -1
            x1 = bx(sb) + sgn * BW / 2
            x2 = bx(rb) - sgn * BW / 2
            s += poly([(x1, yy), (x2, yy)], "dashed" if e["ret"] else "solid", "open" if e["ret"] else "full")
            for i, ln in enumerate(lines):
                ty = yy - 13 - (len(lines) - 1 - i) * LH
                tw = measure(ln) + 8  # белая плашка: линии жизни не проходят сквозь подпись
                s += (f'<rect x="{(x1 + x2) / 2 - tw / 2:.1f}" y="{ty - 10:.1f}" width="{tw:.1f}" '
                      f'height="{LH - 1:.1f}" fill="#fff" stroke="none"/>')
                s += txt((x1 + x2) / 2, ty, ln, "lb", dy=0)
                if measure(ln) > abs(x2 - x1) - 6:
                    warn.append(f"подпись «{ln}» шире стрелки")
            chk = [(x1, yy, sb), (x2, yy, rb)]
        for x, y, b in chk:  # оба конца сообщения лежат на полосах
            if not (bx(b) - BW / 2 - 0.6 <= x <= bx(b) + BW / 2 + 0.6 and b["y0"] <= y <= b["y1"]):
                sys.exit(f"Сообщение «{e['text']}» не опирается на полосу активации")
    if warn:
        print("  предупреждение:", "; ".join(warn))
    geo = {"xs": xs, "ys": ys, "bars": bars, "msgs": msgs, "frames": frames, "bottom": bottom, "gap": gap}
    if notes:
        s += notes(geo)
    print(f"  проверка последовательности: {len(msgs)} сообщений, все концы лежат на полосах")
    return page(tab, W, int(bottom) + 20, s)


def red(tx, ty, text, x, y, anchor="start"):
    """Красная выноска: текст в (tx, ty) и стрелка к точке (x, y)."""
    w = measure(text, 14.5, True)
    cx = tx + w / 2 if anchor == "start" else tx
    x0, x1 = (tx, tx + w) if anchor == "start" else (tx - w / 2, tx + w / 2)
    # начало стрелки: у ближайшего края текста
    if x > x1:
        sx, sy = x1 + 4, ty - 5
    elif x < x0:
        sx, sy = x0 - 4, ty - 5
    else:
        sx, sy = cx, (ty + 5 if y > ty else ty - 20)
    return poly([(sx, sy), (x, y)], arrow="red") + txt(tx, ty, text, "red", anchor, dy=0)


SEQ_BOOKING = dict(
    parts=[("Студент", "actor"), ("BookingController", "web"), ("BookingService", "booking"),
           ("QueuePolicy", "queue"), ("PostgreSQL", "db")],
    events=[M(0, 1, "POST /slots/7/bookings|(lab=3, ack=true)"), M(1, 2, "book(slot=7, student,|lab=3, ack)"),
            M(2, 4, "BEGIN; SELECT slot WHERE id=7 FOR UPDATE"), M(4, 2, "слот и записи", True),
            M(2, 3, "validate(slot, student,|lab, now)"), M(3, 3, "проверить окно, лимит, ЛР", self_=True),
            M(3, 2, "OK", True),
            ALT("[есть свободные|места]", (2, 4), 190),
            M(2, 3, "assignPosition(slot)"), M(3, 2, "position = 9", True),
            M(2, 4, "INSERT booking (BOOKED, position, ack_at)", noret=True),
            ELSE("[мест нет,|лист ожидания < 3]"),
            M(2, 4, "INSERT booking (WAITLISTED)", noret=True),
            END,
            M(2, 4, "INSERT journal; INSERT outbox_email; COMMIT"), M(4, 2, "ok", True),
            M(2, 1, "BookingResult|(status, position, eta)", True), M(1, 0, "303 → /my", True)])

SEQ_NOSHOW = dict(
    parts=[("Преподаватель", "actor"), ("LiveController", "web"), ("QueueService", "queue"),
           ("QueuePolicy", "queue"), ("PostgreSQL", "db"), ("Студент", "actor")],
    events=[M(0, 1, "POST /teacher/bookings/|57/mark (NO_SHOW)"), M(1, 2, "mark(57, NO_SHOW, teacher)"),
            M(2, 4, "BEGIN; SELECT slot FOR UPDATE; SELECT booking 57"), M(4, 2, "booking (called_at = 10:21)", True),
            M(2, 3, "minutesSinceCall|(called_at, now)"), M(3, 2, "n минут", True),
            ALT("[n ≥ 5]", (1, 4), 100),
            M(2, 4, "UPDATE booking → NO_SHOW; INSERT journal; INSERT outbox", noret=True),
            M(2, 3, "callNext(slot)"), M(3, 2, "следующий, позиция 4", True),
            M(2, 4, "UPDATE booking SET called_at; COMMIT", noret=True),
            ELSE("[n < 5]"),
            M(2, 1, "409: ждать ещё|(5 − n) мин", True, keep=True),
            END,
            M(2, 1, "MarkResult", True), M(1, 0, "200: очередь обновлена", True),
            M(5, 1, "GET /api/slots/7/queue (If-None-Match)"), M(1, 5, "200 JSON: «вы следующий», позиция 4", True)])


def seq_booking():
    def notes(g):
        xs, ys, bars = g["xs"], g["ys"], g["bars"]
        bs = [b for b in bars if b["p"] == 0][0]
        fr = g["frames"][0]
        fx0 = xs[2] - fr["dx0"]
        mid = (xs[0] + xs[1]) / 2
        s = ""
        s += red(xs[1] + 30, 128, "Участник", xs[1] + 50, 103)
        
        s += txt(14, 70, "Действующее|лицо (актёр)", "red", anchor="start", dy=17)
        s += poly([(xs[0] - 28, 82), (xs[0] - 18, 56)], arrow="red")
        s += red(xs[0] + 24, 160, "Линия жизни", xs[0] + 1, 150)
        s += red(mid - 70, ys[0] + 40, "Синхронное сообщение", mid + 10, ys[0] + 3)
        s += red(xs[0] + 24, ys[2], "Полоса активации", xs[0] + 8, ys[2] - 16)
        s += red(fx0 + 70, fr["y0"] - 8, "Фрагмент alt", fx0 + 30, fr["y0"] + 1)
        s += red(xs[3] + 60, ys[5] + 42, "Сообщение самому себе", xs[3] + 40, ys[5] + 16)
        s += red(mid - 70, ys[-1] + 44, "Сообщение-возврат", mid + 10, ys[-1] + 3)
        return s
    return sequence("sd Запись на защиту (UC-04, UC-05)", SEQ_BOOKING["parts"], SEQ_BOOKING["events"], 1390, notes,
                    extra_bottom=30)


def seq_noshow():
    return sequence("sd Неявка студента (UC-09, UC-11, UC-07)", SEQ_NOSHOW["parts"], SEQ_NOSHOW["events"], 1400)


FIGS = {"uml-use-cases": use_cases,
        "uml-activity-booking": activity_booking,
        "uml-activity-cancel": activity_cancel,
        "uml-state-booking": state_booking,
        "uml-state-slot": state_slot,
        "uml-seq-booking": seq_booking,
        "uml-seq-noshow": seq_noshow}


def main():
    OUT.mkdir(exist_ok=True)
    exe = find_browser()
    only = sys.argv[1:]
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in FIGS.items():
            if only and name not in only:
                continue
            print(name)
            svg, w, h = fn()
            (OUT / f"{name}.svg").write_text('<?xml version="1.0" encoding="UTF-8"?>\n' + svg, encoding="utf-8")
            src = Path(tmp) / f"{name}.html"
            src.write_text(f"<!doctype html><meta charset='utf-8'><style>html,body{{margin:0;background:#fff}}"
                           f"svg{{display:block}}</style><body>{svg}</body>", encoding="utf-8")
            shot = Path(tmp) / f"{name}.png"
            subprocess.run([exe, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
                            f"--user-data-dir={Path(tmp) / 'profile'}", f"--force-device-scale-factor={SCALE}",
                            f"--window-size={w + 60},{h + 200}", f"--screenshot={shot}", src.as_uri()],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            out = OUT / f"{name}.png"
            if Image is not None:
                im = Image.open(shot)
                if im.width < w * SCALE or im.height < h * SCALE:
                    sys.exit(f"{name}: снимок {im.size} меньше полотна {(w * SCALE, h * SCALE)}")
                im.crop((0, 0, w * SCALE, h * SCALE)).convert("RGB").save(out)
            else:
                out.write_bytes(shot.read_bytes())
            print(f"  images/{name}.svg, images/{name}.png ({w * SCALE}x{h * SCALE})")


if __name__ == "__main__":
    main()
