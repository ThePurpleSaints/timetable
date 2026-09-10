#!/usr/bin/env python3
"""
Timetable Admin Editor

A small tkinter front-end for the Spring Boot admin API. Edit everything:

  - Timetable: every cell of every section (theory / lab / activity / break),
    section names / classrooms, add new sections, and the dataset headers
    (timetableId, academic year, department, semester, year).
  - Calendar: add / edit / delete events, create or clear whole schedules.
  - Overrides: add, edit and delete date-based overrides.

Pure stdlib:  python editor.py [--url http://localhost:8003] [--token ...]

Connects automatically on start. In a controlled/dev environment the default
token matches the Spring server's built-in dev default
("insecure-development-token"); override it with the --token flag or
the TIMETABLE_ADMIN_TOKEN environment variable.
"""

import datetime
import json
import os
import sys
import tkinter as tk
import urllib.error
import urllib.request
from tkinter import messagebox, simpledialog, ttk

DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
DEV_TOKEN = os.environ.get("TIMETABLE_ADMIN_TOKEN", "insecure-development-token")

CONFIG_PATH = os.path.join(os.path.expanduser("~"), ".timetable_admin_editor.json")

META_FIELDS = ["timetableId", "academicYear", "department", "semester", "year"]


def _load_config():
    try:
        with open(CONFIG_PATH, "r", encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        return {}


def _save_config(updated):
    cfg = _load_config()
    cfg.update(updated)
    try:
        with open(CONFIG_PATH, "w", encoding="utf-8") as fh:
            json.dump(cfg, fh)
    except Exception:
        pass


class Api:
    def __init__(self, base="http://localhost:8003", token=DEV_TOKEN):
        self.base = base
        self.token = token

    def call(self, method, path, body=None, on_error=None):
        url = self.base.rstrip("/") + path
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        if self.token:
            req.add_header("Authorization", "Bearer " + self.token)
        req.add_header("Content-Type", "application/json")
        req.add_header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/126.0 Safari/537.36",
        )
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
        except Exception as exc:  # noqa: BLE001 - controlled tool
            if on_error:
                on_error(str(exc))
            return None


class EditorApp:
    def __init__(self, root, initial_url=None, initial_token=None):
        self.api = Api()
        self.root = root
        self.snapshot = None
        self.section = None
        self._editing_event_id = None
        self._editing_override_id = None
        self.override_id_holder = None

        root.title("Timetable Admin Editor")
        root.geometry("1020x700")

        self._build_connection_bar(initial_url, initial_token)
        self._build_notebook()
        self._build_log()
        root.after(250, self.connect)

    # ---------- UI scaffolding -------------------------------------------
    def _build_connection_bar(self, initial_url, initial_token):
        cfg = _load_config()
        bar = ttk.Frame(self.root, padding=6)
        bar.pack(fill="x")
        ttk.Label(bar, text="Server:").pack(side="left")
        self.server_var = tk.StringVar(value=initial_url or cfg.get("url") or self.api.base)
        ttk.Entry(bar, textvariable=self.server_var, width=30).pack(side="left", padx=4)
        ttk.Label(bar, text="Token:").pack(side="left")
        self.token_var = tk.StringVar(
            value=initial_token or os.environ.get("TIMETABLE_ADMIN_TOKEN") or cfg.get("token") or DEV_TOKEN
        )
        self.token_entry = ttk.Entry(bar, textvariable=self.token_var, width=24, show="*")
        self.token_entry.pack(side="left", padx=4)
        ttk.Button(bar, text="Show", command=self._toggle_token_show).pack(side="left")
        ttk.Button(bar, text="Connect", command=self.connect).pack(side="left", padx=4)
        self.status_var = tk.StringVar(value="Not connected")
        ttk.Label(bar, textvariable=self.status_var, foreground="#2b6cb0").pack(side="left", padx=8)

    def _toggle_token_show(self):
        self.token_entry.config(show="" if self.token_entry.cget("show") == "*" else "*")

    def _build_notebook(self):
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill="both", expand=True, padx=6, pady=4)

        self.tab_timetable = ttk.Frame(self.notebook)
        self.tab_calendar = ttk.Frame(self.notebook)
        self.tab_overrides = ttk.Frame(self.notebook)
        self.tab_professors = ttk.Frame(self.notebook)
        self.notebook.add(self.tab_timetable, text="Timetable")
        self.notebook.add(self.tab_calendar, text="Calendar")
        self.notebook.add(self.tab_overrides, text="Overrides")
        self.notebook.add(self.tab_professors, text="Professors")

        self._build_timetable_tab()
        self._build_calendar_tab()
        self._build_overrides_tab()
        self._build_professors_tab()

    def _build_log(self):
        self.log = tk.Text(self.root, height=6, state="disabled", bg="#111", fg="#7CFC00")
        self.log.pack(fill="x", padx=6, pady=4)

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
        self.section_list = tk.Listbox(left, width=32)
        self.section_list.pack(fill="both", expand=True, padx=4, pady=2)
        self.section_list.bind("<<ListboxSelect>>", self._on_section_select)
        ttk.Button(left, text="Reload", command=self.connect).pack(fill="x", padx=4, pady=2)
        ttk.Button(left, text="Add new section...", command=self.add_section).pack(fill="x", padx=4, pady=2)
        ttk.Button(left, text="Rename selected section...", command=self.rename_section).pack(fill="x", padx=4, pady=2)
        pane.add(left)

        right = ttk.Frame(pane)
        head = ttk.Frame(right)
        head.pack(fill="x", padx=4)
        ttk.Label(head, text="Schedule grid — double-click a cell to edit").pack(side="left")
        ttk.Button(head, text="Edit dataset info...", command=self.edit_meta).pack(side="right")
        self.grid = ttk.Treeview(right, columns=(), show="headings")
        self.grid.pack(fill="both", expand=True, padx=4, pady=2)
        self.grid.bind("<Double-1>", self._on_cell_double)
        ttk.Button(right, text="Add cell to selected day", command=self.add_cell).pack(fill="x", padx=4, pady=2)
        ttk.Button(right, text="Delete selected cell", command=self.delete_cell).pack(fill="x", padx=4, pady=2)
        pane.add(right, weight=3)

    def _on_section_select(self, _event):
        selection = self.section_list.curselection()
        if not selection or not self.snapshot:
            return
        self.section = self.snapshot["sections"][selection[0]]
        self._render_grid(self.section)

    def _render_grid(self, section):
        weekly = section.get("weeklyTimetable", {}) or {}
        self.grid.delete(*self.grid.get_children())
        self.grid["columns"] = [f"c{i}" for i in range(9)]
        self.grid["show"] = "headings"
        headers = ["#", "Time"] + DAYS
        for i, header in enumerate(headers):
            self.grid.heading(f"c{i}", text=header)
            self.grid.column(f"c{i}", width=120 if header in DAYS else 55, anchor="w")
        max_cells = max((len(weekly.get(d, [])) for d in DAYS), default=0)
        for row in range(max_cells):
            monday = weekly.get(DAYS[0], [])
            time_col = monday[row].get("time", "") if row < len(monday) else ""
            values = [str(row), time_col]
            for day in DAYS:
                cells = weekly.get(day, [])
                values.append(_cell_label(cells[row]) if row < len(cells) else "")
            self.grid.insert("", "end", values=values)

    def _cell_at(self, day, row):
        weekly = self.section.get("weeklyTimetable", {}) or {}
        cells = weekly.get(day, [])
        return cells[row] if row < len(cells) else None

    def _on_cell_double(self, _event):
        if not self.section:
            return
        region = self.grid.identify("region", _event.x, _event.y)
        if region != "cell":
            return
        iid = self.grid.identify_row(_event.y)
        if not iid:
            return
        row = self.grid.index(iid)
        col = int(self.grid.identify_column(_event.x).lstrip("#")) - 1
        if col < 2 or col > 7:  # ignore # / Time columns
            return
        day = DAYS[col - 1]
        cell = self._cell_at(day, row)
        if not cell:
            return
        suggestions = []
        if hasattr(self, "_prof_courses"):
            seen = set()
            for mapping in self._prof_courses:
                if mapping.get("class_id") != self.section["sectionId"]:
                    continue
                code = mapping.get("course_code")
                if not code or code in seen:
                    continue
                seen.add(code)
                suggestions.append({
                    "courseCode": code,
                    "courseName": mapping.get("course_name", ""),
                    "room": mapping.get("room"),
                    "inCharge": mapping.get("professor_name"),
                })
        edited = _edit_cell_dialog(self.root, self.section["sectionId"], day, row, cell, row, suggestions)
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

        row0 = ttk.Frame(frame)
        row0.grid(row=0, column=0, columnspan=3, sticky="ew")
        ttk.Label(row0, text="Schedule:").pack(side="left")
        self.schedule_combo = ttk.Combobox(row0, state="readonly", width=26)
        self.schedule_combo.pack(side="left", padx=4)
        self.schedule_combo.bind("<<ComboboxSelected>>", lambda _e: self._render_calendar())
        ttk.Button(row0, text="New schedule...", command=self.new_schedule).pack(side="left", padx=4)
        ttk.Button(row0, text="Clear schedule", command=self.clear_schedule).pack(side="left", padx=4)
        ttk.Label(row0, text="(IDs like CAL-ODD-26-27-Y2 / TT-ODD-26-27-Y2-CSE)",
                  foreground="#666").pack(side="left", padx=12)

        self.event_list = tk.Listbox(frame, height=13)
        self.event_list.grid(row=1, column=0, columnspan=3, sticky="nsew", pady=6)
        self.event_list.bind("<Double-1>", lambda _e: self.edit_selected_event())
        self._event_id_map = {}

        form = ttk.LabelFrame(frame, text="Add / edit event", padding=6)
        form.grid(row=2, column=0, columnspan=3, sticky="ew", pady=4)
        ttk.Label(form, text="Schedule").grid(row=0, column=0, sticky="w")
        self.ev_schedule = ttk.Combobox(form, state="readonly", width=24)
        self.ev_schedule.grid(row=0, column=1, padx=4, sticky="w")
        ttk.Label(form, text="Date (yyyy-mm-dd)").grid(row=0, column=2, sticky="w", padx=(12, 0))
        self.ev_date = ttk.Entry(form, width=12)
        self.ev_date.grid(row=0, column=3, padx=4)
        ttk.Label(form, text="Day").grid(row=0, column=4, sticky="w")
        self.ev_day = ttk.Entry(form, width=12)
        self.ev_day.grid(row=0, column=5, padx=4)
        ttk.Label(form, text="Details").grid(row=1, column=0, sticky="w")
        self.ev_details = ttk.Entry(form, width=46)
        self.ev_details.grid(row=1, column=1, columnspan=4, padx=4, sticky="w")
        self.ev_holiday = tk.BooleanVar()
        ttk.Checkbutton(form, text="Holiday", variable=self.ev_holiday).grid(row=1, column=5, padx=4)
        ttk.Button(form, text="Save event", command=self.save_event).grid(row=1, column=6, padx=8)

        btns = ttk.Frame(frame)
        btns.grid(row=3, column=0, columnspan=3, sticky="w")
        ttk.Button(btns, text="Edit selected event", command=self.edit_selected_event).pack(side="left", padx=(0, 4))
        ttk.Button(btns, text="Delete selected event", command=self.delete_event).pack(side="left")
        ttk.Button(btns, text="New event", command=self.prepare_new_event).pack(side="left", padx=8)

        frame.columnconfigure(0, weight=1)

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
            self._event_id_map[len(self._event_id_map)] = event

    def _load_event_form(self, event):
        self._editing_event_id = event.get("id")
        self._set_schedule_combo_values()
        self.ev_schedule.set(event.get("scheduleId", ""))
        self.ev_date.delete(0, "end")
        self.ev_date.insert(0, event.get("date", ""))
        self.ev_day.delete(0, "end")
        self.ev_day.insert(0, event.get("day", ""))
        self.ev_details.delete(0, "end")
        self.ev_details.insert(0, event.get("details", ""))
        self.ev_holiday.set(bool(event.get("holiday")))

    def prepare_new_event(self):
        self._editing_event_id = None
        self._set_schedule_combo_values()
        today = datetime.date.today().isoformat()
        self.ev_schedule.set(self.schedule_combo.get() or "")
        self.ev_date.delete(0, "end")
        self.ev_date.insert(0, today)
        self.ev_day.delete(0, "end")
        self.ev_day.insert(0, "")
        self.ev_details.delete(0, "end")
        self.ev_details.insert(0, "")
        self.ev_holiday.set(False)

    def _set_schedule_combo_values(self):
        ids = sorted({e.get("scheduleId") for e in self._calendar_events})
        self.ev_schedule["values"] = ids + ([self.meta.get("timetableId")] if self.meta and self.meta.get("timetableId") else [])
        if not self.ev_schedule.get() and ids:
            self.ev_schedule.set(ids[0])

    def save_event(self):
        schedule = self.ev_schedule.get().strip()
        date = self.ev_date.get().strip()
        if not schedule:
            messagebox.showerror("Missing schedule", "Pick a schedule for the event.")
            return
        if not date:
            messagebox.showerror("Missing date", "Date is required (yyyy-mm-dd).")
            return
        updated = {
            "date": date,
            "day": self.ev_day.get().strip(),
            "details": self.ev_details.get().strip(),
            "holiday": bool(self.ev_holiday.get()),
            "scheduleId": schedule,
        }
        events = list(self._calendar_events)
        if self._editing_event_id is not None:
            old = next((e for e in events if e.get("id") == self._editing_event_id), None)
            if old is None:
                events.append(updated)
            else:
                old_schedule = old.get("scheduleId")
                events.remove(old)
                if old_schedule != schedule:
                    remaining_old = [e for e in events if e.get("scheduleId") == old_schedule]
                    r = self.api.call("POST", "/api/v1/admin/calendar",
                                      {"scheduleId": old_schedule, "events": remaining_old}, self._on_api_error)
                    if not (r and r.get("ok")):
                        return
                events.append(updated)
        else:
            events.append(updated)
        target = [e for e in events if e.get("scheduleId") == schedule]
        response = self.api.call("POST", "/api/v1/admin/calendar",
                                 {"scheduleId": schedule, "events": target}, self._on_api_error)
        if response and response.get("ok"):
            action = "saved" if self._editing_event_id is not None else "added"
            self.log_line(f"{action.title()} event {date} in {schedule}")
            self.connect(reload_only=True)

    def edit_selected_event(self):
        selection = self.event_list.curselection()
        if not selection:
            messagebox.showinfo("Nothing selected", "Select an event first.")
            return
        event = self._event_id_map.get(selection[0])
        if event:
            self._load_event_form(event)

    def new_schedule(self):
        schedule_id = simpledialog.askstring("New schedule", "Schedule ID:",
                                             initialvalue="CAL-ODD-26-27-Y2", parent=self.root)
        if not schedule_id:
            return
        schedule_id = schedule_id.strip().upper()
        response = self.api.call("POST", "/api/v1/admin/calendar",
                                 {"scheduleId": schedule_id, "events": []}, self._on_api_error)
        if response and response.get("ok"):
            self.log_line(f"Created schedule {schedule_id}")
            self.connect(reload_only=True)
            self.schedule_combo.set(schedule_id)

    def clear_schedule(self):
        schedule = self.schedule_combo.get()
        if not schedule:
            return
        if not messagebox.askyesno("Clear schedule?",
                                    f"Delete ALL events in {schedule}?\n(Timetable away-days vanish too.)"):
            return
        response = self.api.call("POST", "/api/v1/admin/calendar",
                                 {"scheduleId": schedule, "events": []}, self._on_api_error)
        if response and response.get("ok"):
            self.log_line(f"Cleared schedule {schedule}")
            self.connect(reload_only=True)

    def delete_event(self):
        selection = self.event_list.curselection()
        if not selection:
            messagebox.showinfo("Nothing selected", "Select an event first.")
            return
        event = self._event_id_map.get(selection[0])
        if not messagebox.askyesno("Delete?", f"Delete {event.get('details')} on {event.get('date')}?"):
            return
        response = self.api.call(
            "DELETE", f"/api/v1/admin/calendar/{event['id']}", on_error=self._on_api_error
        )
        if response and response.get("ok"):
            self.log_line(f"Deleted event {event['id']}")
            self.connect(reload_only=True)

    # ---------- Overrides tab --------------------------------------------
    def _build_overrides_tab(self):
        frame = ttk.Frame(self.tab_overrides, padding=6)
        frame.pack(fill="both", expand=True)
        self.override_list = tk.Listbox(frame, height=10)
        self.override_list.pack(fill="both", expand=True, pady=4)
        self.override_list.bind("<Double-1>", lambda _e: self.edit_selected_override())
        form = ttk.LabelFrame(frame, text="Add / edit override", padding=6)
        form.pack(fill="x")

        def field(label, col):
            ttk.Label(form, text=label).grid(row=0, column=col, sticky="w")
        field("Date", 0)
        self.ov_date = ttk.Entry(form, width=12)
        self.ov_date.grid(row=0, column=1, padx=4)
        field("Section", 2)
        self.ov_section = ttk.Entry(form, width=10)
        self.ov_section.grid(row=0, column=3, padx=4)
        field("Period", 4)
        self.ov_period = ttk.Entry(form, width=6)
        self.ov_period.grid(row=0, column=5, padx=4)
        field("Course code", 6)
        self.ov_code = ttk.Entry(form, width=12)
        self.ov_code.grid(row=0, column=7, padx=4)
        field("Room", 8)
        self.ov_room = ttk.Entry(form, width=12)
        self.ov_room.grid(row=0, column=9, padx=4)
        field("Reason", 10)
        self.ov_reason = ttk.Entry(form, width=16)
        self.ov_reason.grid(row=0, column=11, padx=4)
        self.ov_cancelled = tk.BooleanVar()
        ttk.Checkbutton(form, text="Cancelled", variable=self.ov_cancelled).grid(row=0, column=12, padx=4)
        ttk.Button(form, text="Save override", command=self.save_override).grid(row=0, column=13, padx=8)

        frame.columnconfigure(0, weight=1)

    def _load_override_form(self, override):
        self._editing_override_id = override.get("id")
        self.ov_date.delete(0, "end")
        self.ov_date.insert(0, override.get("override_date", ""))
        self.ov_section.delete(0, "end")
        self.ov_section.insert(0, override.get("section_id", ""))
        self.ov_period.delete(0, "end")
        period = override.get("period")
        self.ov_period.insert(0, "" if period is None else str(period))
        self.ov_code.delete(0, "end")
        self.ov_code.insert(0, override.get("course_code", ""))
        self.ov_room.delete(0, "end")
        self.ov_room.insert(0, override.get("room", ""))
        self.ov_reason.delete(0, "end")
        self.ov_reason.insert(0, override.get("reason", ""))
        self.ov_cancelled.set(bool(override.get("is_cancelled", False)))

    def edit_selected_override(self):
        selection = self.override_list.curselection()
        if not selection:
            return
        text = self.override_list.get(selection[0])
        override_id = int(text.split(":")[0])
        override = next((ov for ov in self.snapshot.get("overrides", []) if ov.get("id") == override_id), None)
        if override:
            self._load_override_form(override)

    def prepare_new_override(self):
        self._editing_override_id = None
        for entry in (self.ov_date, self.ov_section, self.ov_period, self.ov_code, self.ov_room, self.ov_reason):
            entry.delete(0, "end")
        self.ov_cancelled.set(False)

    def save_override(self):
        period_text = self.ov_period.get().strip()
        if period_text and not period_text.isdigit():
            messagebox.showerror("Bad period", "Period must be a number.")
            return
        override = {
            "date": self.ov_date.get().strip(),
            "sectionId": self.ov_section.get().strip() or "",
            "period": int(period_text) if period_text else None,
            "courseCode": self.ov_code.get().strip(),
            "room": self.ov_room.get().strip(),
            "reason": self.ov_reason.get().strip(),
            "cancelled": bool(self.ov_cancelled.get()),
        }
        if self._editing_override_id is not None:
            override["id"] = self._editing_override_id
        response = self.api.call("POST", "/api/v1/admin/override", {"override": override}, self._on_api_error)
        if response and response.get("ok"):
            self.log_line(f"Saved override for {override['sectionId'] or '?'} on {override['date']}")
            self.prepare_new_override()
            self.connect(reload_only=True)

    def delete_override(self):
        selection = self.override_list.curselection()
        if not selection:
            return
        text = self.override_list.get(selection[0])
        override_id = int(text.split(":")[0])
        if not messagebox.askyesno("Delete?", f"Delete override #{override_id}?"):
            return
        response = self.api.call(
            "DELETE", f"/api/v1/admin/override/{override_id}", on_error=self._on_api_error
        )
        if response and response.get("ok"):
            self.log_line(f"Deleted override #{override_id}")
            self.connect(reload_only=True)

    # ---------- Professors tab --------------------------------------------
    def _build_professors_tab(self):
        outer = ttk.Frame(self.tab_professors, padding=6)
        outer.pack(fill="both", expand=True)

        top = ttk.Frame(outer)
        top.pack(fill="x", pady=(0, 4))
        ttk.Label(top, text="Department:").pack(side="left")
        self.prof_dept = ttk.Combobox(top, width=30, state="readonly")
        self.prof_dept.pack(side="left", padx=4)
        self.prof_dept.bind("<<ComboboxSelected>>", lambda _e: self._prof_department_changed())
        ttk.Label(top, text="Class:").pack(side="left", padx=(12, 0))
        self.prof_class = ttk.Combobox(top, width=8, state="readonly")
        self.prof_class.pack(side="left", padx=4)
        self.prof_class.bind("<<ComboboxSelected>>", lambda _e: self._prof_class_changed())

        pane = ttk.PanedWindow(outer, orient="horizontal")
        pane.pack(fill="both", expand=True)

        left = ttk.LabelFrame(pane, text="Professors in department", padding=4)
        self.professors_list = tk.Listbox(left, height=14)
        self.professors_list.pack(fill="both", expand=True, pady=2)
        self.professors_list.bind("<Double-1>", lambda _e: self._add_prof_course_dialog())
        left_buttons = ttk.Frame(left)
        left_buttons.pack(fill="x")
        ttk.Button(left_buttons, text="Add professor...", command=self._add_professor_dialog).pack(side="left")
        ttk.Button(left_buttons, text="Remove professor", command=self._remove_professor).pack(side="left", padx=4)
        pane.add(left)

        right = ttk.LabelFrame(pane, text="Courses for class (select a course code to fill timetable cells)",
                               padding=4)
        self.prof_courses_list = tk.Listbox(right, height=14)
        self.prof_courses_list.pack(fill="both", expand=True, pady=2)
        self.prof_courses_list.bind("<Double-1>", lambda _e: self._add_prof_course_dialog())
        right_buttons = ttk.Frame(right)
        right_buttons.pack(fill="x")
        ttk.Button(right_buttons, text="Add course...", command=self._add_prof_course_dialog).pack(side="left")
        ttk.Button(right_buttons, text="Edit course", command=self._add_prof_course_dialog).pack(side="left", padx=4)
        ttk.Button(right_buttons, text="Remove course", command=self._remove_prof_course).pack(side="left", padx=4)
        pane.add(right)

    def _render_professors(self):
        depts = sorted({p.get("department") for p in self._professors if p.get("department")})
        current_dept = self.prof_dept.get() or (depts[0] if depts else "")
        self.prof_dept["values"] = depts
        if current_dept in depts:
            self.prof_dept.set(current_dept)
        elif depts:
            self.prof_dept.set(depts[0])
        classes = sorted({s["sectionId"] for s in self.snapshot["sections"]})
        current_class = self.prof_class.get() or (classes[0] if classes else "")
        self.prof_class["values"] = classes
        if current_class in classes:
            self.prof_class.set(current_class)
        elif classes:
            self.prof_class.set(classes[0])
        self._render_professors_list()
        self._render_prof_courses_list()

    def _prof_department_changed(self):
        self._render_professors_list()
        self._render_prof_courses_list()

    def _prof_class_changed(self):
        self._render_prof_courses_list()

    def _selected_department(self):
        return self.prof_dept.get()

    def _selected_class(self):
        return self.prof_class.get()

    def _render_professors_list(self):
        self.professors_list.delete(0, "end")
        dept = self._selected_department()
        for prof in self._professors:
            if dept and prof.get("department") != dept:
                continue
            self.professors_list.insert("end", prof.get("name", ""))
        if self.professors_list.size():
            self.professors_list.selection_set(0)

    def _mappings_for_class(self, class_id):
        return [m for m in self._prof_courses
                if (not class_id or m.get("class_id") == class_id)]

    def _render_prof_courses_list(self):
        self.prof_courses_list.delete(0, "end")
        class_id = self._selected_class()
        for m in self._mappings_for_class(class_id):
            code = m.get("course_code", "")
            name = m.get("course_name", "")
            room = m.get("room") or ""
            prof = m.get("professor_name", "")
            extra = f" ({room})" if room else ""
            self.prof_courses_list.insert("end", f"{code}  {name}  ->  {prof}{extra}")

    def _add_professor_dialog(self):
        dept = self._selected_department() or "CSE"
        dialog = tk.Toplevel(self.root)
        dialog.title("Add professor")
        ttk.Label(dialog, text="Name").grid(row=0, column=0, sticky="e", padx=6, pady=3)
        name_entry = ttk.Entry(dialog, width=40)
        name_entry.grid(row=0, column=1, padx=6, pady=3)
        ttk.Label(dialog, text="Department").grid(row=1, column=0, sticky="e", padx=6, pady=3)
        dept_entry = ttk.Entry(dialog, width=40)
        dept_entry.insert(0, dept)
        dept_entry.grid(row=1, column=1, padx=6, pady=3)
        result = []

        def save():
            name = name_entry.get().strip()
            if not name:
                messagebox.showerror("Bad name", "Name is required.")
                return
            response = self.api.call(
                "POST", "/api/v1/admin/professor",
                {"name": name, "department": dept_entry.get().strip() or "CSE"},
                self._on_api_error)
            if response and response.get("ok"):
                result.append(True)
                dialog.destroy()
                self.log_line(f"Added professor {name}")
                self.connect(reload_only=True)

        ttk.Button(dialog, text="Add", command=save).grid(row=2, column=0, columnspan=2, pady=8)
        dialog.transient(self.root)
        dialog.grab_set()
        self.root.wait_window(dialog)
        return bool(result)

    def _remove_professor(self):
        selection = self.professors_list.curselection()
        if not selection:
            return
        name = self.professors_list.get(selection[0])
        prof = next((p for p in self._professors if p.get("name") == name), None)
        if not prof:
            return
        if not messagebox.askyesno("Remove?", f"Remove professor '{name}' and all their course mappings?"):
            return
        response = self.api.call(
            "DELETE", f"/api/v1/admin/professor/{prof['id']}", on_error=self._on_api_error)
        if response and response.get("ok"):
            self.log_line(f"Removed professor {name}")
            self.connect(reload_only=True)

    def _add_prof_course_dialog(self):
        class_id = self._selected_class()
        dept = self._selected_department()
        professors = [p for p in self._professors
                      if not dept or p.get("department") == dept]
        if not professors and dept:
            professors = self._professors
        if not professors:
            messagebox.showinfo("No professors", "Add a professor first.")
            return

        selection = self.prof_courses_list.curselection()
        editing = None
        if selection:
            line = self.prof_courses_list.get(selection[0])
            code = line.split("  ")[0]
            editing = next((m for m in self._mappings_for_class(class_id)
                            if m.get("course_code") == code), None)

        dialog = tk.Toplevel(self.root)
        dialog.title("Edit professor course mapping")
        names = [p.get("name") for p in professors]
        default_prof = editing.get("professor_name") if editing else professors[0].get("name")
        default_prof = default_prof if default_prof in names else names[0]

        fields = {"professor": ("Professor", names), "classId": ("Class", [s["sectionId"] for s in self.snapshot["sections"]]),
                  "courseCode": ("Course code", None), "courseName": ("Course name", None), "room": ("Room", None)}
        entries = {}
        for row, (key, (label, options)) in enumerate(fields.items()):
            ttk.Label(dialog, text=label).grid(row=row, column=0, sticky="e", padx=6, pady=3)
            if options is None:
                entry = ttk.Entry(dialog, width=40)
            else:
                entry = ttk.Combobox(dialog, values=options, state="readonly", width=38)
                entry.set(default_prof if key == "professor" else class_id)
            entries[key] = entry
            entry.grid(row=row, column=1, padx=6, pady=3)
        if editing:
            entries["courseCode"].insert(0, editing.get("course_code", ""))
            entries["courseName"].insert(0, editing.get("course_name", ""))
            entries["room"].insert(0, editing.get("room", ""))
        result = []

        def save():
            payload = {key: entries[key].get().strip() for key in fields}
            if not payload["courseCode"] or not payload["professor"]:
                messagebox.showerror("Bad mapping", "Professor and course code are required.")
                return
            prof = next((p for p in self._professors if p.get("name") == payload["professor"]), None)
            if not prof:
                return
            body = {
                "professorId": prof["id"],
                "classId": payload["classId"],
                "courseCode": payload["courseCode"],
                "courseName": payload["courseName"],
                "room": payload["room"],
            }
            if editing and editing.get("id"):
                body["id"] = editing["id"]
            response = self.api.call("POST", "/api/v1/admin/professor-course",
                                     {"course": body}, self._on_api_error)
            if response and response.get("ok"):
                result.append(True)
                dialog.destroy()
                self.log_line(f"Saved mapping {payload['courseCode']} for class {payload['classId']}")
                self.connect(reload_only=True)

        ttk.Button(dialog, text="Save", command=save).grid(row=len(fields), column=0, columnspan=2, pady=8)
        dialog.transient(self.root)
        dialog.grab_set()
        self.root.wait_window(dialog)
        return bool(result)

    def _remove_prof_course(self):
        selection = self.prof_courses_list.curselection()
        if not selection:
            return
        class_id = self._selected_class()
        line = self.prof_courses_list.get(selection[0])
        code = line.split("  ")[0]
        mapping = next((m for m in self._mappings_for_class(class_id)
                        if m.get("course_code") == code), None)
        if not mapping:
            return
        if not messagebox.askyesno("Remove?", f"Remove mapping for course '{code}'?"):
            return
        response = self.api.call(
            "DELETE", f"/api/v1/admin/professor-course/{mapping['id']}", on_error=self._on_api_error)
        if response and response.get("ok"):
            self.log_line(f"Removed mapping for {code}")
            self.connect(reload_only=True)

    # ---------- actions ---------------------------------------------------
    def _on_api_error(self, message):
        self.log_line(f"[error] {message}")
        if "401" in message or "authorization" in message.lower():
            messagebox.showerror("Authentication failed",
                                 "Server rejected the token.\nSet the admin token at the top "
                                 "(the server's TIMETABLE_ADMIN_TOKEN or its dev default).")
        else:
            messagebox.showerror("API error", message)

    def connect(self, reload_only=False):
        self.api.base = self.server_var.get().strip() or "http://localhost:8003"
        self.api.token = self.token_var.get().strip()
        if not reload_only:
            self.log_line(f"Connecting to {self.api.base} ...")
        probe = self.api.call("GET", "/api/v1/admin/meta", on_error=self._on_api_error)
        if probe is None:
            self.status_var.set("Connection failed")
            return
        if not probe.get("authenticated"):
            self.status_var.set("Connected — authentication failed")
            self._on_api_error("401 Unauthorized: invalid admin token")
            return
        self.status_var.set(f"Authenticated [{self.api.base}]")
        self.snapshot = self.api.call("GET", "/api/v1/admin/editor", on_error=self._on_api_error)
        if not self.snapshot:
            return
        _save_config({"url": self.api.base, "token": self.api.token})
        self.meta = {key: self.snapshot.get(key, "") for key in META_FIELDS}
        self._calendar_events = self.snapshot.get("calendar", [])
        ids = sorted({e.get("scheduleId") for e in self._calendar_events})
        self.schedule_combo["values"] = ids
        if ids and not self.schedule_combo.get():
            self.schedule_combo.set(ids[0])
        self._refresh_section_list()
        self._render_calendar()
        self._render_overrides()
        self._professors = self.snapshot.get("professors", [])
        self._prof_courses = self.snapshot.get("professorCourses", [])
        self._render_professors()
        timetable_id = self.meta.get("timetableId", "?")
        self.log_line(f"Loaded: {len(self.snapshot['sections'])} sections, "
                      f"timetableId={timetable_id}, "
                      f"calendar events={len(self._calendar_events)}, "
                      f"overrides={len(self.snapshot.get('overrides', []))}, "
                      f"professors={len(self._professors)}, "
                      f"course mappings={len(self._prof_courses)}")

    def _refresh_section_list(self):
        current = self.section_list.curselection()
        self.section_list.delete(0, "end")
        for section in self.snapshot["sections"]:
            self.section_list.insert("end", f"{section['sectionId']}  —  {section['sectionName']}")
        if self.snapshot["sections"]:
            index = max(0, current[0] if current else 0)
            index = min(index, len(self.snapshot["sections"]) - 1)
            self.section_list.selection_set(index)
            self.section = self.snapshot["sections"][index]
            self._render_grid(self.section)

    def _render_overrides(self):
        self.override_list.delete(0, "end")
        for ov in self.snapshot.get("overrides", []):
            tag = "CANCELLED " if ov.get("is_cancelled") else ""
            period = ov.get("period")
            self.override_list.insert(
                "end",
                f"{ov.get('id')}: {tag}{ov.get('override_date')} {ov.get('section_id')} "
                f"{'P' + str(period) if period is not None else 'all'} "
                f"{ov.get('course_code', '')} {ov.get('reason', '')}",
            )

    # --- dataset headers ----
    def edit_meta(self):
        values = {key: str(self.meta.get(key, "")) for key in META_FIELDS}
        edited = _dict_dialog(self.root, "Dataset info", META_FIELDS, values)
        if edited is None:
            return
        response = self.api.call("PUT", "/api/v1/admin/meta", edited, self._on_api_error)
        if response and response.get("ok"):
            self.log_line("Dataset info saved: " + ", ".join(f"{k}={edited[k]}" for k in edited))
            self.connect(reload_only=True)

    # --- section editing ---
    def add_section(self):
        section_id = simpledialog.askstring("New section", "Section ID (e.g. J, K, A2):",
                                            initialvalue="J", parent=self.root)
        if not section_id:
            return
        section_id = section_id.strip().upper()
        section_name = simpledialog.askstring("New section", "Section name (e.g. II CSE J):",
                                              initialvalue=f"II CSE {section_id}", parent=self.root)
        if not section_name:
            return
        classroom = simpledialog.askstring("New section", "Classroom (optional):",
                                           initialvalue="", parent=self.root) or ""
        section = {
            "sectionId": section_id,
            "sectionName": section_name.strip(),
            "classroom": classroom.strip(),
            "weeklyTimetable": {},
        }
        response = self.api.call("POST", "/api/v1/admin/section", {"section": section}, self._on_api_error)
        if response and response.get("ok"):
            self.log_line(f"Added section {section_id}")
            self.connect(reload_only=True)

    def rename_section(self):
        if not self.section:
            messagebox.showinfo("No section", "Select a section first.")
            return
        values = {"sectionName": self.section.get("sectionName", ""), "classroom": self.section.get("classroom", "")}
        edited = _dict_dialog(self.root, f"Edit section {self.section['sectionId']}",
                              ["sectionName", "classroom"], values)
        if edited is None:
            return
        response = self.api.call("PUT", f"/api/v1/admin/section/{self.section['sectionId']}",
                                 edited, self._on_api_error)
        if response and response.get("ok"):
            self.log_line(f"Section {self.section['sectionId']} updated")
            self.connect(reload_only=True)

    # --- cell editing ---
    def add_cell(self):
        if not self.section:
            return
        day = text_choice(self.root, "Day", "Which day?", DAYS)
        if day is None:
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


# ---------- small helpers ------------------------------------------------
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


def _dict_dialog(parent, title, keys, values):
    dialog = tk.Toplevel(parent)
    dialog.title(title)
    entries = {}
    for row, key in enumerate(keys):
        ttk.Label(dialog, text=key).grid(row=row, column=0, sticky="e", padx=6, pady=3)
        entry = ttk.Entry(dialog, width=46)
        entry.insert(0, str(values.get(key, "")))
        entry.grid(row=row, column=1, padx=6, pady=3)
        entries[key] = entry
    result = []

    def save():
        result.append({key: entries[key].get().strip() for key in keys})
        dialog.destroy()

    ttk.Button(dialog, text="Save", command=save).grid(row=len(keys), column=0, columnspan=2, pady=8)
    dialog.transient(parent)
    dialog.grab_set()
    parent.wait_window(dialog)
    return result[0] if result else None


def _edit_cell_dialog(parent, section, day, ordinal, cell, default_ordinal, suggestions=None):
    dialog = tk.Toplevel(parent)
    dialog.title(f"Edit {section} {day} #{ordinal}")
    row_offset = 0
    if suggestions:
        ttk.Label(dialog, text="Fill from course").grid(row=0, column=0, sticky="e", padx=6, pady=3)
        autofill = ttk.Combobox(
            dialog, state="readonly",
            values=[s["courseCode"] for s in suggestions if s.get("courseCode")], width=48)
        autofill.grid(row=0, column=1, padx=6, pady=3)
        row_offset = 1
    fields = {
        "cellType": "type (theory | laboratory | activity | break)",
        "courseName": "course name",
        "courseCode": "course code",
        "activity": "activity (if activity)",
        "room": "room",
        "inCharge": "in charge",
        "time": "time",
        "period": "period",
    }
    entries = {}
    for row, (key, label) in enumerate(fields.items()):
        ttk.Label(dialog, text=label).grid(row=row + row_offset, column=0, sticky="e", padx=6, pady=3)
        entry = ttk.Entry(dialog, width=48)
        entry.insert(0, "" if cell.get(key) is None else str(cell.get(key)))
        entry.grid(row=row + row_offset, column=1, padx=6, pady=3)
        entries[key] = entry
    result = []

    if suggestions:

        def fill(_event):
            selected = autofill.get()
            match = next((s for s in suggestions if s.get("courseCode") == selected), None)
            if not match:
                return
            for source, key in (("courseCode", "courseCode"),
                                ("courseName", "courseName"),
                                ("room", "room"),
                                ("inCharge", "inCharge")):
                entries[key].delete(0, "end")
                entries[key].insert(0, match.get(source) or "")

        autofill.bind("<<ComboboxSelected>>", fill)

    def save():
        payload = {key: entries[key].get().strip() for key in fields}
        if payload["cellType"] not in ("theory", "laboratory", "activity", "break"):
            messagebox.showerror("Bad type", "cellType must be theory | laboratory | activity | break")
            return
        try:
            payload["period"] = int(payload["period"]) if payload["period"] else 0
        except ValueError:
            messagebox.showerror("Bad period", "period must be a number")
            return
        result.append(payload)
        dialog.destroy()

    ttk.Button(dialog, text="Save", command=save).grid(
        row=len(fields) + row_offset, column=0, columnspan=2, pady=8)
    dialog.transient(parent)
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
    url, token = None, None
    args = sys.argv[1:]
    if "--url" in args:
        url = args[args.index("--url") + 1]
    if "--token" in args:
        token = args[args.index("--token") + 1]
    root = tk.Tk()
    EditorApp(root, initial_url=url, initial_token=token)
    root.mainloop()


if __name__ == "__main__":
    main()