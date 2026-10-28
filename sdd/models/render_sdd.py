"""Рендер графической части SDD LabQueue в PNG (sdd/images).

Диаграммы описаны как SVG; для рендеринга используется тот же headless-браузер,
что и в srs/models/render.py. Данные плана берутся из plan.json рядом со скриптом.

Запуск:  python sdd/models/render_sdd.py
"""
import json
import subprocess
import sys
import tempfile
from datetime import date, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "images"
sys.path.insert(0, str(HERE.parent.parent / "srs" / "models"))
from render import CSS, find_browser  # noqa: E402

SCALE = 2
FONT = "'Segoe UI',Arial,sans-serif"
STYLE = f"""
text{{font-family:{FONT};fill:#1f2937}}
.st{{font-size:15px;fill:#555}}.nm{{font-size:19px;font-weight:700}}.bd{{font-size:16px;fill:#333}}
.ln{{stroke:#333;stroke-width:1.6;fill:none}}.dl{{stroke:#333;stroke-width:1.5;fill:none;stroke-dasharray:7 5}}
.lb{{font-size:15px;fill:#333}}.sm{{font-size:14px;fill:#444}}
"""
DEFS = ('<defs><marker id="ar" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" '
        'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#333"/></marker>'
        '<marker id="oa" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="10" markerHeight="10" orient="auto">'
        '<path d="M0,0 L10,5 L0,10" fill="none" stroke="#333" stroke-width="1.4"/></marker></defs>')


def page(tab, w, h, inner):
    return (f'<div class="frame"><div class="tab" style="font-size:16px">{tab}</div>'
            f'<div class="canvas" style="padding:34px 10px 10px"><svg width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}"><style>{STYLE}</style>{DEFS}{inner}</svg></div></div>')


def box(x, y, w, h, stereo, name, lines=(), fill="#fff", stroke="#333", dashed=False):
    da = ' stroke-dasharray="7 5"' if dashed else ""
    s = (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="3" fill="{fill}" stroke="{stroke}" stroke-width="1.6"{da}/>'
         f'<text x="{x + w / 2}" y="{y + 20}" text-anchor="middle" class="st">«{stereo}»</text>'
         f'<text x="{x + w / 2}" y="{y + 42}" text-anchor="middle" class="nm">{name}</text>')
    if lines:
        s += f'<line x1="{x}" y1="{y + 52}" x2="{x + w}" y2="{y + 52}" stroke="{stroke}"/>'
        for i, t in enumerate(lines):
            s += f'<text x="{x + 10}" y="{y + 74 + i * 21}" class="bd">{t}</text>'
    return s


def ln(pts, cls="ln", arrow="full", label=None, lx=None, ly=None, anchor="middle"):
    d = " ".join(f"{x},{y}" for x, y in pts)
    m = {"full": ' marker-end="url(#ar)"', "open": ' marker-end="url(#oa)"', None: ""}[arrow]
    s = f'<polyline points="{d}" class="{cls}"{m}/>'
    if label:
        s += f'<text x="{lx}" y="{ly}" class="lb" text-anchor="{anchor}">{label}</text>'
    return s


# ------------------------------------------------------------------ компоненты
def components():
    s = box(20, 20, 1060, 70, "actor", "Браузер: студент, преподаватель, администратор", fill="#f8fafc")
    s += (f'<rect x="20" y="130" width="1060" height="560" fill="#f4f7fc" stroke="#333" stroke-width="1.8"/>'
          f'<text x="40" y="156" class="nm">«system» LabQueue (Spring Boot 3, Java 21)</text>')
    s += box(50, 175, 1000, 80, "layer", "web — контроллеры MVC, шаблоны Thymeleaf, poll.js", fill="#eff6ff", stroke="#2563eb")
    mods = [("security", "роли, вход"), ("catalog", "дисциплины"), ("slot", "слоты, серии"), ("booking", "записи, лист"),
            ("live", "очередь"), ("report", "ведомость"), ("notification", "outbox")]
    w, gap, x0 = 128, 17, 50
    for i, (n, d) in enumerate(mods):
        x = x0 + i * (w + gap)
        s += box(x, 300, w, 100, "module", n, [d], fill="#fff")
        s += ln([(x + w / 2, 255), (x + w / 2, 300)])
    s += box(330, 450, 440, 85, "module, без Spring", "queue — ядро очереди", fill="#fef9c3", stroke="#ca8a04")
    s += '<text x="550" y="552" text-anchor="middle" class="sm">QueuePolicy, EtaCalculator, Capacity</text>'
    for i in (3, 4):
        x = x0 + i * (w + gap) + w / 2
        s += ln([(x, 400), (x if i == 3 else x, 450)])
    s += box(50, 590, 1000, 80, "layer", "repositories — Spring Data JPA, миграции Flyway", fill="#fff")
    for i in range(7):
        x = x0 + i * (w + gap) + 30
        if i not in (3, 4):
            s += ln([(x, 400), (x, 590)], cls="dl", arrow="open")
    s += box(20, 730, 330, 100, "database", "PostgreSQL 16", ["таблицы, индексы, блокировки"], fill="#f0fdf4", stroke="#16a34a")
    s += box(400, 730, 320, 100, "component", "scheduler", ["заморозка, отправка писем"], fill="#fff")
    s += box(770, 730, 310, 100, "external", "SMTP-сервер", ["Gmail / Яндекс 360"], dashed=True)
    s += ln([(185, 670), (185, 730)], label="JDBC", lx=195, ly=705, anchor="start")
    s += ln([(560, 730), (560, 670)])
    s += ln([(1048, 350), (1068, 350), (1068, 730)], label="SMTP / TLS", lx=1060, ly=712, anchor="end")
    s += ln([(550, 90), (550, 175)], label="HTTPS: страницы, формы, опрос JSON раз в 10 с", lx=560, ly=120, anchor="start")
    return page("component view &nbsp;LabQueue", 1100, 840, s)


# ------------------------------------------------------------------ модель данных
def entity(x, y, w, name, cols, fill="#fff"):
    h = 40 + 22 * len(cols) + 8
    s = (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="3" fill="{fill}" stroke="#333" stroke-width="1.6"/>'
         f'<rect x="{x}" y="{y}" width="{w}" height="36" rx="3" fill="#e2e8f0" stroke="#333" stroke-width="1.6"/>'
         f'<text x="{x + w / 2}" y="{y + 25}" text-anchor="middle" class="nm" style="font-size:18px">{name}</text>')
    for i, c in enumerate(cols):
        bold = "font-weight:700;" if c.startswith("PK") else ""
        s += f'<text x="{x + 10}" y="{y + 60 + i * 22}" class="bd" style="{bold}font-size:15px">{c}</text>'
    return s, h


def data_model():
    s = ""
    E = {}
    def add(key, *args):
        nonlocal s
        part, h = entity(*args)
        s += part
        E[key] = (args[0], args[1], args[2], h)
    add("group", 20, 20, 230, "study_group", ["PK id", "code (уник.)"])
    add("user", 20, 170, 270, "app_user", ["PK id", "email (уник.)", "password_hash", "full_name", "role", "FK group_id",
                                            "email_confirmed", "locked_until"])
    add("disc", 420, 20, 260, "discipline", ["PK id", "title", "lab_count", "FK teacher_id"])
    add("dg", 790, 20, 290, "discipline_group", ["PK,FK discipline_id", "PK,FK group_id"])
    add("slot", 420, 220, 290, "slot", ["PK id", "FK discipline_id", "FK teacher_id", "starts_at, ends_at", "place",
                                         "defense_minutes", "capacity", "status", "series_id", "version"])
    add("book", 800, 260, 280, "booking", ["PK id", "FK slot_id", "FK student_id", "lab_no", "status", "position",
                                            "created_at", "called_at"])
    add("jour", 420, 560, 290, "journal_entry", ["PK id", "FK slot_id", "FK booking_id", "FK actor_id", "action",
                                                  "reason", "at"])
    add("out", 20, 470, 270, "outbox_email", ["PK id", "to_email", "template", "payload (jsonb)", "attempts",
                                               "next_attempt_at", "sent_at"])
    def rel(p1, p2, l1, l2, lx1, ly1, lx2, ly2):
        return (ln([p1, p2], arrow=None) + f'<text x="{lx1}" y="{ly1}" class="lb">{l1}</text>'
                f'<text x="{lx2}" y="{ly2}" class="lb">{l2}</text>')
    s += rel((135, 112), (135, 170), "1", "*", 142, 130, 142, 164)                       # group — user
    s += rel((290, 260), (420, 300), "1", "*", 296, 254, 398, 296)                       # user(teacher) — slot
    s += rel((550, 156), (550, 220), "1", "*", 557, 174, 557, 214)                       # disc — slot
    s += rel((680, 60), (790, 60), "1", "*", 688, 52, 772, 52)                           # disc — dg
    s += ln([(200, 20), (200, 4), (935, 4), (935, 20)], arrow=None) + '<text x="207" y="22" class="lb">1</text><text x="942" y="22" class="lb">*</text>'
    s += rel((710, 330), (800, 330), "1", "*", 718, 322, 782, 322)                       # slot — booking
    s += ln([(290, 190), (760, 190), (760, 300), (800, 300)], arrow=None) + '<text x="296" y="184" class="lb">1</text><text x="782" y="294" class="lb">*</text>'
    s += rel((565, 488), (565, 560), "1", "*", 572, 506, 572, 552)                       # slot — journal
    s += ln([(1000, 484), (1000, 640), (710, 640)], arrow=None) + '<text x="1007" y="502" class="lb">1</text><text x="718" y="634" class="lb">*</text>'
    s += ('<text x="740" y="690" class="sm">booking: частичный уникальный индекс</text>'
          '<text x="740" y="710" class="sm">(slot_id, student_id) для активных статусов</text>')
    return page("entity-relationship view &nbsp;LabQueue", 1100, 790, s)


# ------------------------------------------------------------------ последовательность
def sequence():
    lanes = [("Студент", "браузер"), ("BookingController", "web"), ("BookingService", "booking"),
             ("QueuePolicy", "queue"), ("PostgreSQL", "db"), ("OutboxSender", "scheduler"), ("SMTP", "внешний")]
    xs = [80 + i * 158 for i in range(len(lanes))]
    s = ""
    for (n, t), x in zip(lanes, xs):
        s += (f'<rect x="{x - 76}" y="10" width="152" height="54" rx="3" fill="#fff" stroke="#333" stroke-width="1.5"/>'
              f'<text x="{x}" y="33" text-anchor="middle" class="nm" style="font-size:15px">{n}</text>'
              f'<text x="{x}" y="54" text-anchor="middle" class="st">{t}</text>'
              f'<line x1="{x}" y1="64" x2="{x}" y2="820" stroke="#777" stroke-dasharray="6 5"/>')
    msgs = [  # (из, в, y, текст, пунктир)
        (0, 1, 100, "POST /bookings/42/cancel", False),
        (1, 2, 140, "cancel(42, student)", False),
        (2, 4, 180, "BEGIN; SELECT slot FOR UPDATE", False),
        (2, 4, 220, "UPDATE booking SET CANCELLED", False),
        (2, 3, 265, "promote(slot, now)", False),
        (3, 2, 305, "первый из листа → BOOKED", True),
        (2, 4, 345, "UPDATE booking; INSERT journal", False),
        (2, 4, 385, "INSERT outbox_email; COMMIT", False),
        (2, 1, 425, "результат", True),
        (1, 0, 465, "303 → /my", True),
        (0, 1, 530, "GET /api/slots/7/queue (опрос ≤ 10 с)", False),
        (1, 0, 570, "200 JSON: позиции, ETA", True),
        (5, 4, 640, "SELECT outbox WHERE sent_at IS NULL", False),
        (5, 6, 690, "отправить письмо «Вы записаны»", False),
        (6, 5, 730, "250 OK  (при ошибке — повтор 1, 5, 15 мин)", True),
        (5, 4, 775, "UPDATE outbox SET sent_at", False),
    ]
    for a, b, y, t, dash in msgs:
        x1, x2 = xs[a], xs[b]
        x2e = x2 - 8 if x2 > x1 else x2 + 8
        s += ln([(x1, y), (x2e, y)], cls="dl" if dash else "ln", arrow="open" if dash else "full")
        tx = (x1 + x2) / 2
        s += f'<text x="{tx}" y="{y - 8}" text-anchor="middle" class="lb">{t}</text>'
    s += ('<rect x="300" y="160" width="520" height="245" fill="none" stroke="#ca8a04" stroke-width="1.5"/>'
          '<text x="812" y="398" text-anchor="end" class="sm" style="fill:#92400e">одна транзакция, слот заблокирован</text>'
          '<rect x="10" y="610" width="1110" height="185" fill="none" stroke="#999" stroke-dasharray="4 4"/>'
          '<text x="18" y="628" class="sm">фоновая задача, каждые 30 с</text>')
    return page("sequence view &nbsp;Отмена записи и продвижение из листа ожидания", 1130, 830, s)


# ------------------------------------------------------------------ развёртывание
def deployment():
    s = box(20, 30, 230, 100, "device", "Браузер", ["ПК / смартфон"], fill="#f8fafc")
    s += (f'<rect x="300" y="10" width="560" height="560" rx="4" fill="#f4f7fc" stroke="#333" stroke-width="1.8"/>'
          f'<text x="320" y="38" class="nm">«node» VPS: 2 vCPU, 2 ГБ, Ubuntu 24.04</text>'
          f'<text x="320" y="62" class="st">Docker Compose</text>')
    s += box(330, 80, 500, 85, "container", "proxy — Caddy 2", ["HTTPS 443, сертификат Let's Encrypt"], fill="#fff")
    s += box(330, 200, 500, 105, "container", "app — eclipse-temurin:21-jre", ["labqueue.jar, -Xmx768m, порт 8080",
                                                                              "переменные окружения из .env"], fill="#eff6ff", stroke="#2563eb")
    s += box(330, 340, 240, 105, "container", "db — PostgreSQL 16", ["том pgdata"], fill="#f0fdf4", stroke="#16a34a")
    s += box(590, 340, 240, 105, "container", "backup", ["pg_dump ежедневно,", "хранение 14 дней"], fill="#fff")
    s += box(330, 470, 500, 80, "volume", "pgdata, backups", fill="#fff", dashed=True)
    s += box(900, 200, 190, 105, "external", "SMTP", ["порт 587, TLS"], dashed=True)
    s += ln([(250, 80), (330, 120)], label="HTTPS", lx=258, ly=86, anchor="start")
    s += ln([(580, 165), (580, 200)], label="HTTP 8080", lx=590, ly=188, anchor="start")
    s += ln([(450, 305), (450, 340)], label="JDBC 5432", lx=460, ly=328, anchor="start")
    s += ln([(590, 392), (570, 392)])
    s += ln([(830, 250), (900, 250)], label="SMTP", lx=865, ly=242)
    return page("deployment view &nbsp;LabQueue", 1100, 580, s)


# ------------------------------------------------------------------ диаграммы Ганта
D0 = date(2026, 11, 9)


def d(s):
    dd, mm = map(int, s.split(".")[:2])
    return date(2026 if mm >= 9 else 2027, mm, dd)


def gantt(plan, title, deadline="27.12", end="03.01", lanes_note=None):
    """plan: список (метка_строки, итерация_начало, итерация_конец, смещение_ч, часы, цвет, ёмкость_ч_итерации)."""
    days = (d(end) - D0).days + 1
    left, top, rowh, W = 300, 70, 34, 1100
    sx = (W - left - 20) / days
    s = ""
    t = D0
    while t <= d(end):   # недели
        x = left + (t - D0).days * sx
        s += f'<line x1="{x}" y1="{top - 10}" x2="{x}" y2="{top + rowh * len(plan) + 10}" stroke="#e5e7eb"/>'
        s += f'<text x="{x + 3}" y="{top - 16}" class="sm">{t.strftime("%d.%m")}</text>'
        t += timedelta(days=7)
    for i, (lab, s_it, e_it, off, h, color, cap) in enumerate(plan):
        y = top + i * rowh
        span = (d(e_it) - d(s_it)).days + 1
        x = left + (d(s_it) - D0).days * sx + off / cap * span * sx
        w = max(h / cap * span * sx, 4)
        s += f'<text x="{left - 10}" y="{y + 22}" text-anchor="end" class="bd" style="font-size:15px">{lab}</text>'
        s += (f'<rect x="{x}" y="{y + 6}" width="{w}" height="22" rx="3" fill="{color}" stroke="#333" stroke-width="0.8"/>'
              f'<text x="{x + w + 5}" y="{y + 22}" class="sm">{h} ч</text>')
    x = left + (d(deadline) - D0).days * sx + sx
    hh = top + rowh * len(plan) + 10
    s += (f'<line x1="{x}" y1="{top - 30}" x2="{x}" y2="{hh}" stroke="#dc2626" stroke-width="2.2" stroke-dasharray="8 5"/>'
          f'<text x="{x - 6}" y="{top - 34}" text-anchor="end" class="lb" style="fill:#dc2626;font-weight:700">'
          f'дедлайн {deadline}</text>')
    if lanes_note:
        s += f'<text x="{left}" y="{hh + 28}" class="sm">{lanes_note}</text>'
    return page(title, W, hh + 40, s)


def plan_rows(plan, version):
    colors = {"I1": "#93c5fd", "I2": "#86efac", "I3": "#fde68a", "I4": "#fca5a5", "I4+": "#fca5a5"}
    rows = []
    for it in plan["order"]:
        s_it, e_it, cap = plan["iters"][it]
        off = {"dev": 0, "jun": 0}
        for who, label, h in plan["items"][it]:
            c = colors[it] if who == "dev" else "#c4b5fd"
            name = label if who == "dev" else f"{label} (junior)"
            rows.append((name, s_it, e_it, off[who], h, c, cap if who == "dev" else plan["jun_caps"][it]))
            off[who] += h
    return rows


def main():
    OUT.mkdir(exist_ok=True)
    P = json.loads((HERE / "plan.json").read_text(encoding="utf-8"))
    figs = {"components": (components(), 1140, 900), "data-model": (data_model(), 1140, 850),
            "seq-cancel-promote": (sequence(), 1170, 890), "deployment": (deployment(), 1140, 640)}
    for v, title, note in (("v1", "План итераций, версия 1 (29.10.2026): 149 ч", "не укладывается: последняя итерация до 03.01"),
                           ("v2", "План итераций, версия 2 (30.10.2026): 131 ч", "запас до дедлайна — 9 ч"),
                           ("v3", "План итераций, версия 3 (04.11.2026): разработчик 128 ч + junior 29 ч",
                            "фиолетовым — задачи junior-разработчика")):
        rows = plan_rows(P[v], v)
        html = gantt(rows, title, lanes_note=note)
        figs[f"gantt-{v}"] = (html, 1140, 70 + 34 * len(rows) + 140)
    exe = find_browser()
    only = sys.argv[1:]
    with tempfile.TemporaryDirectory() as tmp:
        for name, (html, w, h) in figs.items():
            if only and name not in only:
                continue
            src = Path(tmp) / f"{name}.html"
            src.write_text(f"<!doctype html><meta charset='utf-8'><style>{CSS}</style><body>{html}</body>", encoding="utf-8")
            out = OUT / f"{name}.png"
            subprocess.run([exe, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
                            f"--user-data-dir={Path(tmp) / 'profile'}", f"--force-device-scale-factor={SCALE}",
                            f"--window-size={w},{h}", f"--screenshot={out}", src.as_uri()],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print(f"images/{name}.png ({w * SCALE}, {h * SCALE})")


if __name__ == "__main__":
    main()
