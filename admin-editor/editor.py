#!/usr/bin/env python3
"""
Timetable Admin Editor (proposal / dummy prototype)

A small tkinter front-end for the Spring Boot admin API:
  GET/POST/DELETE cells, overrides, and calendar events.

Pure stdlib: python -m editor.py   (or:  python editor.py)

The point of this prototype is to demonstrate the data model idea:

    Timetables : TT-ODD-26-27-Y2-CSE      TT-{ODD|EVE}-{YY-YY}-Y{n}-{DEPT}
    Calendar   : CAL-ODD-26-27-Y2         CAL-{ODD|EVE}-{YY-YY}-Y{n}

so more departments / years / semesters can be added without touching the app.
"""

import json
import sys
import tkinter as tk
import urllib.error
import urllib.request
from tkinter import messagebox, ttk

DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]


class Api:
    def __init__(self):
        self.base = "http://localhost:8003"
        self.token = ""

    def call(self, method, path, body=None, on_error=None):
        url = self.base.rstrip("/") + path
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Authorization", "Bearer " + self.token)
        req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as err:
            try:
                detail = err.read().decode("utf-8")
            except Exception:
                detail = err.reason or ""
            if on_error:
                on_error(f"HTTP {err.code}: {detail}")
            return None
        except Exception as exc:  # noqa: BLE001 - prototype
            if on_error:
                on_error(str(exc))
            return None


class EditorApp:
    def __init__(self, root):
        self.api = Api()
        self.root = root
        self.snapshot = None
        self.meta = None
        self.section = None  # currently selected section dict

        root.title("Timetable Admin Editor (prototype)")
        root.geometry("980x640")

        self._build_connection_bar()
        self._build_notebook()
        self._build_log()

    # ---------- UI scaffolding -------------------------------------------
    def _build_connection_bar(self):
        bar = ttk.Frame(self.root, padding=6)
        bar.pack(fill="x")
        ttk.Label(bar, text="Server:").pack(side="left")
        self.server_var = tk.StringVar(value=self.api.base)
        ttk.Entry(bar, textvariable=self.server_var, width=28).pack(side="left", padx=4)
        ttk.Label(bar, text="Token:").pack(side="left")
        self.token_var = tk.StringVar()
        ttk.Entry(bar, textvariable=self.token_var, width=20, show="*").pack(side="left", padx=4)
        self.status_var = tk.StringVar(value="Not connected")
        ttk.Button(bar, text="Connect", command=self.connect).pack(side="left", padx=4)
        ttk.Label(bar, textvariable=self.status_var, foreground="#2b6cb0").pack(side="left", padx=8)

    def _build_notebook(self):
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill="both", expand=True, padx=6, pady=4)

        self.tab_timetable = ttk.Frame(self.notebook)
        self.tab_calendar = ttk.Frame(self.notebook)
        self.tab_overrides = ttk.Frame(self.notebook)
        self.notebook.add(self.tab_timetable, text="Timetable")
        self.notebook.add(self.tab_calendar, text="Calendar")
        self.notebook.add(self.tab_overrides, text="Overrides")

        self._build_timetable_tab()
        self._build_calendar_tab()
        self._build_overrides_tab()

    def _build_log(self):
        self.log = tk.Text(self.root, height=6, state="disabled", bg="#111", fg="#7CFC00")
        self.log.pack(fill="x", padx=6, pady=4)
        self.log_tag = 0

    def log_line(self, text):
        self.log.config(state="normal")
        self.log.insert("end", text + "\n")
        self.log.see("end")
        self.log.config(state="disabled")

    # ---------- Timetable tab --------------------------------------------
    def _build_timetable_tab(self):
        pane = ttk.PanedWindow(self.tab_timetable, orient="horizontal")
        pane.pack(fill="both", expand=True)

        left = ttk.Frame(pane)
        ttk.Label(left, text="Sections (classes)").pack(anchor="w", padx=4)
        self.section_list = tk.Listbox(left, width=30)
        self.section_list.pack(fill="both", expand=True, padx=4, pady=2)
        self.section_list.bind("<<ListboxSelect>>", self._on_section_select)
        self.btn_reload = ttk.Button(left, text="Reload", command=self.connect)
        self.btn_reload.pack(fill="x", padx=4, pady=2)
        pane.add(left)

        right = ttk.Frame(pane)
        ttk.Label(right, text="Schedule grid — double-click a cell to edit").pack(anchor="w", padx=4)
        self.grid = ttk.Treeview(right, columns=(), show="headings")
        self.grid.pack(fill="both", expand=True, padx=4, pady=2)
        self.grid.bind("<Double-1>", self._on_cell_double)
        ttk.Button(right, text="Add cell to selected day", command=self.add_cell).pack(
            fill="x", padx=4, pady=2
        )
        ttk.Button(right, text="Delete selected cell", command=self.delete_cell).pack(
            fill="x", padx=4, pady=2
        )
        pane.add(right, weight=3)

    def _on_section_select(self, _event):
        selection = self.section_list.curselection()
        if not selection or not self.snapshot:
            return
        self.section = self.snapshot["sections"][selection[0]]
        self._render_grid(self.section)

    def _render_grid(self, section):
        weekly = section.get("weeklyTimetable", {})
        self.grid.delete(*self.grid.get_children())
        self.grid["columns"] = [f"c{i}" for i in range(9)]
        self.grid["show"] = "headings"
        headers = ["#", "Time"] + DAYS[:7]
        for i, header in enumerate(headers[:9]):
            self.grid.heading(f"c{i}", text=header)
            self.grid.column(f"c{i}", width=120 if header in DAYS else 55, anchor="w")
        max_cells = max((len(weekly.get(d, [])) for d in DAYS), default=0)
        for row in range(max_cells):
            values = [str(row)] + [
                weekly.get(DAYS[0], [])[row].get("time", "") if row < len(weekly.get(DAYS[0], [])) else ""
            ]
            for day_index in range(7):
                day = DAYS[day_index]
                if row < len(weekly.get(day, [])):
                    cell = weekly[day][row]
                    values.append(_cell_label(cell))
                else:
                    values.append("")
            self.grid.insert("", "end", values=values[:9])

    def _cell_at(self, day, row):
        weekly = self.section.get("weeklyTimetable", {})
        cells = weekly.get(day, [])
        return cells[row] if row < len(cells) else None

    def _on_cell_double(self, _event):
        if not self.section:
            return
        region = self.grid.identify("region", *_grid_coords(self.grid, _event))
        if region != "cell":
            return
        col = int(self.grid.identify_column(_event.x).lstrip("#")) - 1
        row = int(self.grid.identify_row(_event.y))
        if col == 0 or col > 7:  # ignore # / Time columns
            return
        day = DAYS[col - 1]
        cell = self._cell_at(day, row)
        if not cell:
            return
        edited = _edit_cell_dialog(self.root, section=self.section["sectionId"],
                                   day=day, ordinal=row, cell=cell, default_ordinal=row)
        if edited is not None:
            body = {
                "sectionId": self.section["sectionId"],
                "day": day,
                "ordinal": row,
                "cell": edited,
            }
            response = self.api.call("PUT", "/api/v1/admin/cell", body, self._on_api_error)
            if response and response.get("ok"):
                self.log_line(f"Updated {day} #{row}: {edited.get('courseName', '')}")
                self.connect(reload_only=True)

    # ---------- Calendar tab ---------------------------------------------
    def _build_calendar_tab(self):
        frame = ttk.Frame(self.tab_calendar, padding=6)
        frame.pack(fill="both", expand=True)
        ttk.Label(frame, text="Schedule:").grid(row=0, column=0, sticky="w")
        self.schedule_combo = ttk.Combobox(frame, state="readonly", width=22)
        self.schedule_combo.grid(row=0, column=1, sticky="w", padx=4)
        self.schedule_combo.bind("<<ComboboxSelected>>", lambda _e: self._render_calendar())

        ttk.Label(frame, text="(Timetable IDs TT-Odd-Eve-YY-YY-Yn-Dept belong here too)").grid(
            row=0, column=2, sticky="w", padx=12)

        self.event_list = tk.Listbox(frame, height=12)
        self.event_list.grid(row=1, column=0, columnspan=3, sticky="nsew", pady=6)
        self._event_id_map = {}

        form = ttk.LabelFrame(frame, text="Add event", padding=6)
        form.grid(row=2, column=0, columnspan=3, sticky="ew", pady=4)
        ttk.Label(form, text="Date (yyyy-mm-dd)").grid(row=0, column=0, sticky="w")
        self.ev_date = ttk.Entry(form, width=12)
        self.ev_date.grid(row=0, column=1, padx=4)
        ttk.Label(form, text="Day").grid(row=0, column=2, sticky="w")
        self.ev_day = ttk.Entry(form, width=10)
        self.ev_day.grid(row=0, column=3, padx=4)
        ttk.Label(form, text="Details").grid(row=0, column=4, sticky="w")
        self.ev_details = ttk.Entry(form, width=46)
        self.ev_details.grid(row=0, column=5, padx=4)
        self.ev_holiday = tk.BooleanVar()
        ttk.Checkbutton(form, text="Holiday", variable=self.ev_holiday).grid(row=0, column=6, padx=4)
        ttk.Button(form, text="Add", command=self.add_event).grid(row=0, column=7)

        ttk.Button(frame, text="Delete selected event", command=self.delete_event).pack(anchor="w")

    def _render_calendar(self):
        if not hasattr(self, "_calendar_events"):
            return
        schedule = self.schedule_combo.get()
        self.event_list.delete(0, "end")
        self._event_id_map.clear()
        for event in self._calendar_events:
            if event.get("scheduleId") != schedule:
                continue
            marker = "* H " if event.get("holiday") else "    "
            label = f"{marker}{event.get('date')}  {event.get('day', ''):<10} {event.get('details', '')}"
            self.event_list.insert("end", label)
            self._event_id_map[event.get("id")] = event

    # ---------- Overrides tab --------------------------------------------
    def _build_overrides_tab(self):
        frame = ttk.Frame(self.tab_overrides, padding=6)
        frame.pack(fill="both", expand=True)
        self.override_list = tk.Listbox(frame)
        self.override_list.pack(fill="both", expand=True, pady=4)
        form = ttk.LabelFrame(frame, text="Add / edit override", padding=6)
        form.pack(fill="x")
        ttk.Label(form, text="Date").grid(row=0, column=0)
        self.ov_date = ttk.Entry(form, width=12)
        self.ov_date.grid(row=0, column=1, padx=4)
        ttk.Label(form, text="Section").grid(row=0, column=2)
        self.ov_section = ttk.Entry(form, width=10)
        self.ov_section.grid(row=0, column=3, padx=4)
        ttk.Label(form, text="Period (blank=all)").grid(row=0, column=4)
        self.ov_period = ttk.Entry(form, width=8)
        self.ov_period.grid(row=0, column=5, padx=4)
        ttk.Label(form, text="Course code").grid(row=0, column=6)
        self.ov_code = ttk.Entry(form, width=12)
        self.ov_code.grid(row=0, column=7, padx=4)
        self.ov_cancelled = tk.BooleanVar()
        ttk.Checkbutton(form, text="Cancelled", variable=self.ov_cancelled).grid(row=0, column=8, padx=4)
        ttk.Button(form, text="Save", command=self.save_override).grid(row=0, column=9)
        ttk.Button(frame, text="Delete selected override", command=self.delete_override).pack(anchor="w")

    # ---------- actions ---------------------------------------------------
    def _on_api_error(self, message):
        self.log_line(f"[error] {message}")
        messagebox.showerror("API error", message)

    def connect(self, reload_only=False):
        self.api.base = self.server_var.get().strip()
        self.api.token = self.token_var.get().strip()
        if not reload_only:
            self.log_line(f"Connecting to {self.api.base} ...")
        probe = self.api.call("GET", "/api/v1/admin/meta", on_error=self._on_api_error)
        if probe is None:
            self.status_var.set("Connection failed")
            return
        self.status_var.set("Authenticated: %s" % probe.get("authenticated"))
        self.snapshot = self.api.call("GET", "/api/v1/admin/editor", on_error=self._on_api_error)
        if not self.snapshot:
            return
        self.meta = self.snapshot.get("meta", {})
        self._calendar_events = self.snapshot.get("calendar", [])
        ids = sorted({e.get("scheduleId") for e in self._calendar_events})
        self.schedule_combo["values"] = ids
        if ids and not self.schedule_combo.get():
            self.schedule_combo.set(ids[0])
        self._refresh_section_list()
        self._render_calendar()
        self._render_overrides()
        timetable_id = self.meta.get("timetableId", "?")
        self.log_line(f"Loaded: {len(self.snapshot['sections'])} sections, "
                      f"timetableId={timetable_id}, events={len(self._calendar_events)}")

    def _refresh_section_list(self):
        self.section_list.delete(0, "end")
        for section in self.snapshot["sections"]:
            self.section_list.insert("end", f"{section['sectionId']}  —  {section['sectionName']}")
        if self.snapshot["sections"]:
            self.section = self.snapshot["sections"][0]
            self._render_grid(self.section)

    def _render_overrides(self):
        self.override_list.delete(0, "end")
        for ov in self.snapshot.get("overrides", []):
            tag = "CANCELLED " if ov.get("is_cancelled") else ""
            self.override_list.insert(
                "end",
                f"{ov.get('id')}: {tag}{ov.get('override_date')} {ov.get('section_id')} "
                f"P{ov.get('period', '')} {ov.get('course_code', '')} {ov.get('activity', '')}",
            )

    # --- cell editing ---
    def add_cell(self):
        if not self.section:
            return
        day = text_choice(self.root, "Day", "Which day?", DAYS)
        if day is None:
            return
        ordering = list((self.section.get("weeklyTimetable", {}) or {}).keys())
        if not ordering:
            return
        new_cell = {
            "period": 0,
            "time": "",
            "cellType": "theory",
            "courseCode": "",
            "courseName": "New slot",
            "room": "",
            "inCharge": "",
        }
        response = self.api.call(
            "POST",
            "/api/v1/admin/cell",
            {"sectionId": self.section["sectionId"], "day": day, "cell": new_cell},
            self._on_api_error,
        )
        if response and response.get("ok"):
            self.log_line(f"Added cell to {day} (ordinal {response.get('ordinal')})")
            self.connect(reload_only=True)

    def delete_cell(self):
        if not self.section:
            return
        selection = self.grid.selection()
        if not selection:
            messagebox.showinfo("Nothing selected", "Select a cell row first.")
            return
        row = int(self.grid.index(selection[0]))
        day = text_choice(self.root, "Day", "Delete from which day?", DAYS)
        if day is None:
            return
        response = self.api.call(
            "DELETE",
            f"/api/v1/admin/cell?sectionId={self.section['sectionId']}&day={day}&ordinal={row}",
            on_error=self._on_api_error,
        )
        if response and response.get("ok"):
            self.log_line(f"Deleted {day} #{row}")
            self.connect(reload_only=True)

    # --- calendar actions ---
    def add_event(self):
        schedule = self.schedule_combo.get()
        date = self.ev_date.get().strip()
        if not date and not messagebox.askyesno("Empty date", "Date is empty — use a record's existing date?"):
            return
        new_event = {
            "scheduleId": schedule,
            "date": date or "2030-01-01",
            "day": self.ev_day.get().strip(),
            "details": self.ev_details.get().strip(),
            "holiday": bool(self.ev_holiday.get()),
        }
        events = [e for e in self._calendar_events if e.get("scheduleId") == schedule]
        events.append(new_event)
        response = self.api.call(
            "POST",
            "/api/v1/admin/calendar",
            {"scheduleId": schedule, "events": events},
            self._on_api_error,
        )
        if response and response.get("ok"):
            self.log_line(f"Saved {len(events)} events for {schedule}")
            self.connect(reload_only=True)

    def delete_event(self):
        selection = self.event_list.curselection()
        if not selection:
            messagebox.showinfo("Nothing selected", "Select an event first.")
            return
        event = self._event_id_map.get(selection[0])
        if not messagebox.askyesno("Delete?", f"Delete {event.get('details')}?"):
            return
        response = self.api.call(
            "DELETE", f"/api/v1/admin/calendar/{event['id']}", on_error=self._on_api_error
        )
        if response and response.get("ok"):
            self.log_line(f"Deleted event {event['id']}")
            self.connect(reload_only=True)

    # --- override actions ---
    def save_override(self):
        override = {
            "date": self.ov_date.get().strip(),
            "sectionId": self.ov_section.get().strip() or "A",
            "period": int(self.ov_period.get()) if self.ov_period.get().strip().isdigit() else None,
            "courseCode": self.ov_code.get().strip(),
            "cancelled": bool(self.ov_cancelled.get()),
            "reason": "",
        }
        response = self.api.call("POST", "/api/v1/admin/override", {"override": override},
                                 self._on_api_error)
        if response and response.get("ok"):
            self.log_line(f"Saved override for {override['sectionId']}")
            self.connect(reload_only=True)

    def delete_override(self):
        selection = self.override_list.curselection()
        if not selection:
            return
        text = self.override_list.get(selection[0])
        override_id = text.split(":")[0]
        if not messagebox.askyesno("Delete?", f"Delete override #{override_id}?"):
            return
        response = self.api.call(
            "DELETE", f"/api/v1/admin/override/{override_id}", on_error=self._on_api_error
        )
        if response and response.get("ok"):
            self.log_line(f"Deleted override #{override_id}")
            self.connect(reload_only=True)


# ---------- small helpers ------------------------------------------------
def _grid_coords(widget, event):
    return event.x, event.y


def _cell_label(cell):
    if cell.get("cellType") == "break":
        return f"[break] {cell.get('courseName', '')}"
    if cell.get("cellType") == "activity":
        return f"[{cell.get('activity', '')}] {cell.get('room', '')}"
    text = cell.get("courseName") or cell.get("courseCode") or ""
    room = cell.get("room") or ""
    return f"{text}  ({room})"


def text_choice(parent, title, prompt, choices):
    dialog = tk.Toplevel(parent)
    dialog.title(title)
    ttk.Label(dialog, text=prompt).pack(padx=10, pady=(10, 4))
    combo = ttk.Combobox(dialog, values=choices, state="readonly")
    combo.pack(padx=10, pady=4)
    combo.set(choices[0])
    result = []

    def pick():
        result.append(combo.get())
        dialog.destroy()

    ttk.Button(dialog, text="OK", command=pick).pack(pady=6)
    dialog.grab_set()
    parent.wait_window(dialog)
    return result[0] if result else None


def _edit_cell_dialog(parent, section, day, ordinal, cell, default_ordinal):
    dialog = tk.Toplevel(parent)
    dialog.title(f"Edit {day} #{ordinal}")
    fields = {
        "cellType": "type (theory|lab|activity|break)",
        "courseName": "course name",
        "courseCode": "course code",
        "room": "room",
        "inCharge": "in charge",
        "activity": "activity (if activity)",
        "time": "time",
        "period": "period",
    }
    entries = {}
    for row, (key, label) in enumerate(fields.items()):
        ttk.Label(dialog, text=label).grid(row=row, column=0, sticky="e", padx=6, pady=3)
        entry = ttk.Entry(dialog, width=42)
        entry.insert(0, "" if cell.get(key) is None else str(cell.get(key)))
        entry.grid(row=row, column=1, padx=6, pady=3)
        entries[key] = entry
    result = []

    def save():
        payload = {key: entries[key].get().strip() for key in fields}
        if payload["cellType"] not in ("theory", "laboratory", "activity", "break"):
            messagebox.showerror("Bad type", "cellType must be theory|laboratory|activity|break")
            return
        try:
            payload["period"] = int(payload["period"]) if payload["period"] else 0
        except ValueError:
            messagebox.showerror("Bad period", "period must be a number")
            return
        result.append(payload)
        dialog.destroy()

    ttk.Button(dialog, text="Save", command=save).grid(row=len(fields), column=0, columnspan=2, pady=8)
    dialog.grab_set()
    parent.wait_window(dialog)
    return result[0] if result else None


def main():
    if sys.platform == "win32":
        try:
            import ctypes  # keep the DPI crisp on Windows
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except Exception:
            pass
    root = tk.Tk()
    EditorApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()