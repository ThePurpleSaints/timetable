# Timetable Admin Editor (prototype)

A proposal-grade desktop tool for managing timetable data through the Spring
Boot admin API. Pure Python + tkinter (no third-party dependencies).

It auto-connects to `http://localhost:8003` on start (the default token is the
server's dev default `insecure-development-token`). Override with flags:

```
python editor.py --url http://localhost:8003 --token <admin-token>
```

You can:

- browse sections and edit every timetable cell (double-click a cell),
- add / delete cells on any day, add new sections, rename sections,
- edit the dataset headers (timetableId, academic year, department, semester, year),
- manage academic calendar events per schedule: add, edit, delete, create or clear a whole schedule,
- add, edit or cancel on-date overrides.

## The idea it demonstrates: one ID, infinitely scalable

Every dataset is identified by a single code that encodes its whole story, so
adding a new department/year/semester is just adding a new dataset:

```
Timetable : TT-ODD-26-27-Y2-CSE
Calendar  : CAL-ODD-26-27-Y2

TT | CAL        fixed prefix
ODD | EVE       term — ODD starts late in the year (Aug–Jan), EVE early (Jan–May)
26-27          academic year
Y2             year of study (Y1, Y2, ...)
CSE            department (3–4 chars)
```

Current live dataset:

| Kind       | ID                 | Meaning                          |
| ---------- | ------------------ | -------------------------------- |
| Timetable  | `TT-ODD-26-27-Y2-CSE` | 2nd-year CSE, odd sem, 2026–27, sections A–I |
| Calendar   | `CAL-ODD-26-27-Y2` | the same term's academic calendar |

The Android app stays untouched — it only speaks to the public API, which
keeps returning the same shapes.

## Note

This is a dummy/prototype ("propose the idea") — it is intentionally simple,
not hardened. The API contract it uses lives in the server
(`server-spring/src/main/java/.../web/AdminApiController.java`).