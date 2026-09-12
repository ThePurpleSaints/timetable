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
import queue
import re
import sys
import threading
import tkinter as tk
import traceback
import urllib.error
import urllib.parse
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


class _RedirectPreserving(urllib.request.HTTPRedirectHandler):
    """Follow redirects for write methods (PUT/POST/DELETE) without losing the
    method or the body. urllib's default handler refuses redirects unless the
    method is GET/HEAD (or POST on 301/302/303), so a Cloudflare HTTP->HTTPS
    redirect surfaced as a bare 'HTTP 301' error on DELETE. Keeping the method
    matches how curl/requests handle 301/302/307/308 (only 303 rules to GET)."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        method = req.get_method()
        headers_map = dict(req.headers)
        headers_map.pop("Content-Length", None)
        if code in (301, 302, 307, 308) and method not in ("GET", "HEAD"):
            redirected = urllib.request.Request(
                newurl, data=req.data, headers=headers_map,
                origin_req_host=req.origin_req_host, unverifiable=True)
            redirected.method = method
            return redirected
        if code == 303 and method not in ("GET", "HEAD"):
            redirected = urllib.request.Request(
                newurl, headers=headers_map,
                origin_req_host=req.origin_req_host, unverifiable=True)
            redirected.method = "GET"
            return redirected
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class Api:
    def __init__(self, base="http://localhost:8003", token=DEV_TOKEN):
        self.base = base
        self.token = token
        self.on_activity = None   # callable(busy: bool), safely driven from the main thread
        self._lock = threading.Lock()
        self._busy = 0
        self._queue = queue.Queue()  # (fn, args) drained on the Tk main thread
        self._opener = urllib.request.build_opener(_RedirectPreserving())

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
            with self._opener.open(req, timeout=10) as resp:
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

    def async_call(self, method, path, body=None, on_done=None, on_error=None):
        """Run call() on a worker thread; on_done/on_error fire on the main thread."""

        def run():
            error = []
            try:
                result = self.call(method, path, body, on_error=lambda m: error.append(m))
            except Exception as exc:  # noqa: BLE001
                error.append(str(exc))
                result = None
            with self._lock:
                self._busy -= 1
            self._set_busy(self._busy > 0)
            if error:
                if on_error:
                    self._schedule(on_error, error[0])
            elif on_done:
                self._schedule(on_done, result)

        with self._lock:
            self._busy += 1
        self._set_busy(True)
        threading.Thread(target=run, daemon=True).start()

    def _set_busy(self, busy):
        if self.on_activity:
            self._queue.put((self.on_activity, (busy,)))

    def _schedule(self, fn, *args):
        self._queue.put((fn, args))


class EditorApp:
    def __init__(self, root, initial_url=None, initial_token=None):
        self.api = Api()
        self.api.on_activity = self._set_activity
        self.root = root
        self.snapshot = None
        self.section = None
        self._editing_event_id = None
        self._editing_override_id = None
        self._connect_seq = 0
        self._dirty_cells = {}        # (sectionId, day, ordinal) -> staged operation dict
        self._dirty_order = []        # insertion order of staged cell edits
        self._selected_cells = []     # list of (iid, day, ordinal) highlighted cells
        self._overlay = None          # highlight frame for the clicked cell

        root.title("Timetable Admin Editor")
        root.geometry("1020x700")

        self._first_connect = True

        self._build_connection_bar(initial_url, initial_token)
        self._build_notebook()
        self._build_log()
        self._drain_worker_queue()
        root.after(250, self.connect)

    def _drain_worker_queue(self):
        """Run API callbacks on the Tk main thread; never let one break the pump."""
        try:
            while True:
                fn, args = self.api._queue.get_nowait()
                try:
                    fn(*args)
                except Exception:  # noqa: BLE001 - keep the pump alive
                    traceback.print_exc()
        except queue.Empty:
            pass
        try:
            self.root.after(50, self._drain_worker_queue)
        except Exception:  # noqa: BLE001 - window may already be closing
            pass

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
        self.progress = ttk.Progressbar(bar, mode="indeterminate", length=120)
        self.progress.pack(side="left", padx=4)

    def _set_activity(self, busy):
        if busy:
            self.progress.start(12)
        else:
            self.progress.stop()

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
        stamp = datetime.datetime.now().strftime("%H:%M:%S")
        self.log.config(state="normal")
        self.log.insert("end", f"[{stamp}] {text}\n")
        self.log.see("end")
        self.log.config(state="disabled")

    # ---------- Timetable tab --------------------------------------------
    def _build_timetable_tab(self):
        pane = ttk.PanedWindow(self.tab_timetable, orient="horizontal")
        pane.pack(fill="both", expand=True)

        left = ttk.Frame(pane)
        ttk.Label(left, text="Sections (classes)").pack(anchor="w", padx=4)
        filters = ttk.Frame(left)
        filters.pack(fill="x", padx=4, pady=(0, 2))
        ttk.Label(filters, text="Dept:").pack(side="left")
        self.section_dept_var = tk.StringVar()
        self.section_dept = ttk.Combobox(filters, textvariable=self.section_dept_var,
                                         width=12, state="readonly")
        self.section_dept.pack(side="left", padx=3)
        self.section_dept.bind("<<ComboboxSelected>>", lambda _e: self._apply_section_filters())
        ttk.Label(filters, text="Year:").pack(side="left")
        self.section_year_var = tk.StringVar()
        self.section_year = ttk.Combobox(filters, textvariable=self.section_year_var,
                                         width=5, state="readonly")
        self.section_year.pack(side="left", padx=3)
        self.section_year.bind("<<ComboboxSelected>>", lambda _e: self._apply_section_filters())
        self.section_list = tk.Listbox(left, width=34)
        self.section_list.pack(fill="both", expand=True, padx=4, pady=2)
        self.section_list.bind("<<ListboxSelect>>", self._on_section_select)
        ttk.Button(left, text="Reload", command=self.connect).pack(fill="x", padx=4, pady=2)
        ttk.Button(left, text="Add new section...", command=self.add_section).pack(fill="x", padx=4, pady=2)
        ttk.Button(left, text="Rename selected section...", command=self.rename_section).pack(fill="x", padx=4, pady=2)
        ttk.Button(left, text="Departments...", command=self.manage_departments).pack(fill="x", padx=4, pady=2)
        pane.add(left)

        right = ttk.Frame(pane)
        head = ttk.Frame(right)
        head.pack(fill="x", padx=4)
        ttk.Label(head, text="Click a cell to select; Ctrl+click to multi-select; double-click to edit").pack(side="left")
        self.save_button = ttk.Button(head, text="SAVE CHANGES", state="disabled",
                                      command=self._flush_saves)
        self.save_button.pack(side="right", padx=(6, 0))
        self.dirty_label = ttk.Label(head, text="", foreground="#b45309")
        self.dirty_label.pack(side="right", padx=6)
        ttk.Button(head, text="Edit dataset info...", command=self.edit_meta).pack(side="right")
        self.grid = ttk.Treeview(right, columns=(), show="headings")
        self.grid.pack(fill="both", expand=True, padx=4, pady=2)
        self.grid.bind("<Double-1>", self._on_cell_double)
        self.grid.bind("<Button-1>", self._on_cell_click)
        ttk.Button(right, text="Add cell to selected day", command=self.add_cell).pack(fill="x", padx=4, pady=2)
        ttk.Button(right, text="Delete selected cells", command=self.delete_cell).pack(fill="x", padx=4, pady=2)
        pane.add(right, weight=3)

    def _section_meta(self, section):
        """Return (year, department) parsed from a section name like 'II CSE A'."""
        name = (section.get("sectionName") or "").strip()
        upper = name.upper()
        roman = ""
        for token in ("VIII", "VII", "VII", "VI", "IV", "III", "V", "II", "I"):
            if upper == token or upper.startswith(token + " "):
                roman = token
                break
        rest = upper[len(roman):].strip() if roman else name
        parts = [w for w in rest.split() if w]
        if not parts:
            return roman, ""
        dept = parts[0]
        return roman, dept

    def _apply_section_filters(self):
        self._refresh_section_list()

    def _filtered_sections(self):
        dept = self.section_dept_var.get()
        year = self.section_year_var.get()
        out = []
        for section in self.snapshot["sections"]:
            sy, sd = self._section_meta(section)
            if dept and sd and sd != dept:
                continue
            if year and sy and sy != year:
                continue
            out.append(section)
        return out

    def _on_section_select(self, _event):
        selection = self.section_list.curselection()
        if not selection or not self.snapshot:
            return
        self.section = self.snapshot["sections"][selection[0]]
        self._render_grid(self.section)

    def _render_grid(self, section):
        self._clear_overlay()
        self._selected_cells = []
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
                values.append(_short_cell_label(cells[row]) if row < len(cells) else "")
            self.grid.insert("", "end", values=values)

    # ---- cell click / multi-select highlight ----
    def _on_cell_click(self, _event):
        region = self.grid.identify("region", _event.x, _event.y)
        if region != "cell":
            return
        iid = self.grid.identify_row(_event.y)
        if not iid:
            return
        col = int(self.grid.identify_column(_event.x).lstrip("#")) - 1
        if col < 2 or col > 8:
            return
        row = self.grid.index(iid)
        day = DAYS[col - 2]
        ctrl = bool(_event.state & 0x0004)
        if ctrl:
            selected = [s for s in self._selected_cells if s[1] == day and s[2] == row]
            if selected:
                self._selected_cells.remove(selected[0])
            elif self._cell_at(day, row):
                self._selected_cells.append((iid, day, row))
        elif self._cell_at(day, row):
            self._selected_cells = [(iid, day, row)]
        else:
            self._selected_cells = []
        self._apply_highlights()
        return "break"

    def _apply_highlights(self):
        self._clear_overlay()
        self._overlay = []
        for _iid, day, row in self._selected_cells:
            if not self._cell_at(day, row):
                continue
            children = self.grid.get_children()
            if row >= len(children):
                continue
            col = DAYS.index(day) + 2
            self._place_overlay(children[row], col, clear=False)

    def _place_overlay(self, iid, col, clear=True):
        try:
            bbox = self.grid.bbox(iid, col)
        except Exception:
            return
        if not bbox:
            return
        bx, by, bw, bh = bbox
        t = 3
        accent = "#1f6feb"
        if clear:
            self._clear_overlay()
            self._overlay = []
        elif self._overlay is None:
            self._overlay = []
        # Four thin strips drawn just OUTSIDE the cell edges, so the cell text
        # stays visible under the highlight.
        for (x, y, w, h) in (
            (bx - t, by - t, bw + 2 * t, t),      # top
            (bx - t, by + bh, bw + 2 * t, t),     # bottom
            (bx - t, by - t, t, bh + 2 * t),      # left
            (bx + bw, by - t, t, bh + 2 * t),     # right
        ):
            strip = tk.Canvas(self.grid, highlightthickness=0, bg=accent)
            strip.place(x=int(x), y=int(y), width=int(w), height=int(h))
            self._overlay.append(strip)

    def _clear_overlay(self):
        if self._overlay:
            for strip in self._overlay:
                try:
                    strip.destroy()
                except Exception:
                    pass
        self._overlay = None

    def _restore_cell_highlight(self, day, ordinal):
        if day not in DAYS:
            return
        try:
            iid = self.grid.get_children()[ordinal]
        except Exception:
            return
        col = DAYS.index(day) + 2
        self._selected_cells = [(iid, day, ordinal)]
        if self._cell_at(day, ordinal):
            self._place_overlay(iid, col)

    # ---- staged cell edits (SAVE CHANGES) ----
    def _mark_dirty(self):
        count = len(self._dirty_cells)
        if count:
            self.save_button.config(state="normal")
            self.dirty_label.config(text=f"{count} pending")
        else:
            self.save_button.config(state="disabled")
            self.dirty_label.config(text="")

    def _stage_cell_edit(self, day, row, edited):
        weekly = self.section.setdefault("weeklyTimetable", {})
        cells = weekly.setdefault(day, [])
        while len(cells) <= row:
            cells.append({"period": len(cells), "cellType": "theory", "courseName": ""})
        cells[row] = edited
        key = (self.section["sectionId"], day, row)
        self._clean_dirty_key(key)
        self._dirty_cells[key] = {
            "method": "PUT",
            "path": "/api/v1/admin/cell",
            "body": {"sectionId": self.section["sectionId"], "day": day, "ordinal": row, "cell": edited},
        }
        self._dirty_order.append(key)
        self._mark_dirty()
        self._render_grid(self.section)
        self._restore_cell_highlight(day, row)

    def _stage_cell_add(self, day, new_cell):
        weekly = self.section.setdefault("weeklyTimetable", {})
        cells = weekly.setdefault(day, [])
        cells.append(new_cell)
        new_cell["period"] = new_cell.get("period") or len(cells)
        key = (self.section["sectionId"], day, len(cells) - 1)
        self._clean_dirty_key(key)
        self._dirty_cells[key] = {
            "method": "POST",
            "path": "/api/v1/admin/cell",
            "body": {"sectionId": self.section["sectionId"], "day": day, "cell": new_cell},
        }
        self._dirty_order.append(key)
        self._mark_dirty()
        self._render_grid(self.section)
        self._restore_cell_highlight(day, len(cells) - 1)

    def _stage_cell_deletes(self, plan):
        """Stage DELETE ops for the given [(day, row)] cells and flush them.

        Rows are deleted highest-first within each day so the remaining
        ordinals on both the grid and the server stay valid."""
        weekly = self.section.setdefault("weeklyTimetable", {})
        by_day = {}
        for day, row in plan:
            by_day.setdefault(day, []).append(row)
        for day, rows in by_day.items():
            cells = weekly.setdefault(day, [])
            for row in sorted(rows, reverse=True):
                if 0 <= row < len(cells):
                    cells.pop(row)
                key = (self.section["sectionId"], day, row)
                self._clean_dirty_key(key)
                self._dirty_cells[key] = {
                    "method": "DELETE",
                    "path": ("/api/v1/admin/cell?" + urllib.parse.urlencode({
                        "sectionId": self.section["sectionId"], "day": day, "ordinal": row})),
                    "body": None,
                }
                self._dirty_order.append(key)
        self._mark_dirty()
        self._render_grid(self.section)
        self._clear_overlay()
        self._selected_cells = []
        self._flush_saves()

    def _clean_dirty_key(self, key):
        """Drop any staged op that used the same (section, day, ordinal) before adding a new one."""
        self._dirty_cells.pop(key, None)
        if key in self._dirty_order:
            self._dirty_order.remove(key)

    def _flush_saves(self):
        pending = [self._dirty_cells[k] for k in self._dirty_order]
        if not pending:
            return
        self.save_button.config(state="disabled")
        self.dirty_label.config(text=f"saving {len(pending)} ...")
        # deletes last so their (day, ordinal) targets stay valid on the server
        ordered = [op for op in pending if op["method"] != "DELETE"] + \
                  [op for op in pending if op["method"] == "DELETE"]
        self._flush_next(ordered, 0)

    def _flush_next(self, ordered, index):
        if index >= len(ordered):
            self.log_line(f"Saved {len(ordered)} cell change(s)")
            self._dirty_cells.clear()
            self._dirty_order.clear()
            self._mark_dirty()
            self.reload_after_save()
            return
        op = ordered[index]
        self.api.async_call(
            op["method"], op["path"], op["body"],
            on_done=lambda _r, i=index: self._flush_next(ordered, i + 1),
            on_error=lambda m, i=index: self._flush_failed(ordered, i, m))

    def _flush_failed(self, ordered, index, message):
        op = ordered[index] if index < len(ordered) else {}
        label = f"{op.get('method', '?')} {op.get('path', '?')}"[:100]
        self.log_line(f"[error] save failed at change #{index + 1}: {label} -> {message}")
        self._mark_dirty()
        if messagebox.askretrycancel("Save failed", f"{message}\n\nFailed request: {label}\n\nRetry the remaining changes?"):
            self._flush_next(ordered, index)
        else:
            self._clear_pending_staged()

    def _clear_pending_staged(self):
        self.log_line("Discarding remaining pending cell changes (server was not modified).")
        self._dirty_cells.clear()
        self._dirty_order.clear()
        self._mark_dirty()

    def reload_after_save(self):
        if self._dirty_cells:
            self._flush_saves()
        else:
            self._do_reload()

    def _do_reload(self):
        self.connect(reload_only=True)

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
        if col < 2 or col > 8:  # skip # / Time columns, allow Monday..Sunday
            return
        day = DAYS[col - 2]
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
        edited = _edit_cell_dialog(self.root, self.section["sectionId"], day, row, cell, suggestions)
        if edited is not None:
            self._stage_cell_edit(day, row, edited)
            self.log_line(f"Staged {day} #{row}: {edited.get('courseName', '')} â€” click SAVE CHANGES")

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
                    target = [e for e in events if e.get("scheduleId") == schedule]
                    self.api.async_call(
                        "POST", "/api/v1/admin/calendar",
                        {"scheduleId": old_schedule, "events": remaining_old},
                        on_done=lambda r: self._move_then_save(r, schedule, target, date),
                        on_error=self._on_api_error)
                    return
                events.append(updated)
        else:
            events.append(updated)
        target = [e for e in events if e.get("scheduleId") == schedule]
        self._save_calendar(schedule, target, date)

    def _move_then_save(self, response, schedule, target, date):
        if response and response.get("ok"):
            self._save_calendar(schedule, target, date)

    def _save_calendar(self, schedule, target, date):
        def done(response):
            if response and response.get("ok"):
                action = "saved" if self._editing_event_id is not None else "added"
                self.log_line(f"{action.title()} event {date} in {schedule}")
                self.reload_after_save()
        self.api.async_call(
            "POST", "/api/v1/admin/calendar",
            {"scheduleId": schedule, "events": target},
            on_done=done, on_error=self._on_api_error)

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
        self.api.async_call(
            "POST", "/api/v1/admin/calendar",
            {"scheduleId": schedule_id, "events": []},
            on_done=lambda response: self._schedule_created(response, schedule_id),
            on_error=self._on_api_error)

    def _schedule_created(self, response, schedule_id):
        if response and response.get("ok"):
            self.log_line(f"Created schedule {schedule_id}")
            self.reload_after_save()
            self.schedule_combo.set(schedule_id)

    def clear_schedule(self):
        schedule = self.schedule_combo.get()
        if not schedule:
            return
        if not messagebox.askyesno("Clear schedule?",
                                    f"Delete ALL events in {schedule}?\n(Timetable away-days vanish too.)"):
            return
        self.api.async_call(
            "POST", "/api/v1/admin/calendar",
            {"scheduleId": schedule, "events": []},
            on_done=lambda response: self._schedule_cleared(response, schedule),
            on_error=self._on_api_error)

    def _schedule_cleared(self, response, schedule):
        if response and response.get("ok"):
            self.log_line(f"Cleared schedule {schedule}")
            self.reload_after_save()

    def delete_event(self):
        selection = self.event_list.curselection()
        if not selection:
            messagebox.showinfo("Nothing selected", "Select an event first.")
            return
        event = self._event_id_map.get(selection[0])
        if not messagebox.askyesno("Delete?", f"Delete {event.get('details')} on {event.get('date')}?"):
            return
        self.api.async_call(
            "DELETE", f"/api/v1/admin/calendar/{event['id']}",
            on_done=lambda response: self._event_deleted(response, event),
            on_error=self._on_api_error)

    def _event_deleted(self, response, event):
        if response and response.get("ok"):
            self.log_line(f"Deleted event {event['id']}")
            self.reload_after_save()

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
        field("Date(s)", 0)
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
        dates = [d for d in re.split(r"[,\s;]+", self.ov_date.get().strip()) if d]
        if not dates:
            messagebox.showerror("Missing date", "Date is required (yyyy-mm-dd, comma/space for several).")
            return
        if self._editing_override_id is not None:
            dates = dates[:1]
        base = {
            "sectionId": self.ov_section.get().strip() or "",
            "period": int(period_text) if period_text else None,
            "courseCode": self.ov_code.get().strip(),
            "room": self.ov_room.get().strip(),
            "reason": self.ov_reason.get().strip(),
            "cancelled": bool(self.ov_cancelled.get()),
        }
        if self._editing_override_id is not None:
            base["id"] = self._editing_override_id
        self._save_overrides_seq(dates, base, 0)

    def _save_overrides_seq(self, dates, base, index):
        if index >= len(dates):
            self.log_line(f"Saved {len(dates)} override(s)")
            self.prepare_new_override()
            self.reload_after_save()
            return
        override = dict(base)
        override["date"] = dates[index]
        self.api.async_call(
            "POST", "/api/v1/admin/override", {"override": override},
            on_done=lambda _r, i=index: self._save_overrides_seq(dates, base, i + 1),
            on_error=self._on_api_error)

    def delete_override(self):
        selection = self.override_list.curselection()
        if not selection:
            return
        text = self.override_list.get(selection[0])
        override_id = int(text.split(":")[0])
        if not messagebox.askyesno("Delete?", f"Delete override #{override_id}?"):
            return
        self.api.async_call(
            "DELETE", f"/api/v1/admin/override/{override_id}",
            on_done=lambda response: self._override_deleted(response, override_id),
            on_error=self._on_api_error)

    def _override_deleted(self, response, override_id):
        if response and response.get("ok"):
            self.log_line(f"Deleted override #{override_id}")
            self.reload_after_save()

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
        self.professors_list.bind("<<ListboxSelect>>", lambda _e: self._render_professor_details())
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

        details = ttk.LabelFrame(outer, text="Professor workload (from the timetable)", padding=4)
        details.pack(fill="both", expand=True, pady=(4, 0))
        self.prof_details = tk.Text(details, height=7, state="disabled",
                                    wrap="none", font=("Consolas", 9))
        self.prof_details.pack(fill="both", expand=True)

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
        self._render_professor_details()

    def _render_professor_details(self):
        text = self.prof_details
        text.config(state="normal")
        text.delete("1.0", "end")
        selection = self.professors_list.curselection()
        if not selection:
            text.config(state="disabled")
            return
        name = self.professors_list.get(selection[0])
        prof = next((p for p in getattr(self, "_professors", []) if p.get("name") == name), None)
        if not prof:
            text.config(state="disabled")
            return
        lines = [f"{name}  ({prof.get('department', '')})"]
        maps = [m for m in getattr(self, "_prof_courses", []) if m.get("professor_name") == name]
        if maps:
            lines.append("Mapped courses:")
            for m in sorted(maps, key=lambda x: x.get("class_id", "") or ""):
                lines.append(f"   {m.get('class_id', '?')}: {m.get('course_code', '')} "
                             f"{m.get('course_name', '')} {('(room ' + str(m.get('room')) + ')') if m.get('room') else ''}")
        slots = 0
        for section in self.snapshot.get("sections", []):
            sname = section.get("sectionName") or section.get("sectionId") or "?"
            weekly = section.get("weeklyTimetable", {}) or {}
            for day in DAYS:
                for cell in weekly.get(day, []):
                    if (cell.get("inCharge") or "").strip() != name:
                        continue
                    slots += 1
                    course = cell.get("courseName") or cell.get("courseCode") or cell.get("activity") or ""
                    room = cell.get("room")
                    period = cell.get("period")
                    t = cell.get("time")
                    lines.append(f"   {sname:<10} {day:<9} P{period if period is not None else '?'} "
                                 f"{(t or ''):<12} {course} {('[' + str(room) + ']') if room else ''}")
        lines.append(f"Titled classes/slots in timetable: {slots}")
        text.insert("1.0", "\n".join(lines) + "\n")
        text.config(state="disabled")

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
            self.api.async_call(
                "POST", "/api/v1/admin/professor",
                {"name": name, "department": dept_entry.get().strip() or "CSE"},
                on_done=lambda response: self._professor_added(response, dialog, result, name),
                on_error=self._on_api_error)

        ttk.Button(dialog, text="Add", command=save).grid(row=2, column=0, columnspan=2, pady=8)
        dialog.transient(self.root)
        dialog.grab_set()
        self.root.wait_window(dialog)
        return bool(result)

    def _professor_added(self, response, dialog, result, name):
        if response and response.get("ok"):
            result.append(True)
            dialog.destroy()
            self.log_line(f"Added professor {name}")
            self.reload_after_save()

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
        self.api.async_call(
            "DELETE", f"/api/v1/admin/professor/{prof['id']}",
            on_done=lambda response: self._professor_removed(response, name),
            on_error=self._on_api_error)

    def _professor_removed(self, response, name):
        if response and response.get("ok"):
            self.log_line(f"Removed professor {name}")
            self.reload_after_save()

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
            self.api.async_call(
                "POST", "/api/v1/admin/professor-course", {"course": body},
                on_done=lambda response: self._prof_course_saved(response, dialog, result, payload),
                on_error=self._on_api_error)

        ttk.Button(dialog, text="Save", command=save).grid(row=len(fields), column=0, columnspan=2, pady=8)
        dialog.transient(self.root)
        dialog.grab_set()
        self.root.wait_window(dialog)
        return bool(result)

    def _prof_course_saved(self, response, dialog, result, payload):
        if response and response.get("ok"):
            result.append(True)
            dialog.destroy()
            self.log_line(f"Saved mapping {payload['courseCode']} for class {payload['classId']}")
            self.reload_after_save()

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
        self.api.async_call(
            "DELETE", f"/api/v1/admin/professor-course/{mapping['id']}",
            on_done=lambda response: self._prof_course_removed(response, code),
            on_error=self._on_api_error)

    def _prof_course_removed(self, response, code):
        if response and response.get("ok"):
            self.log_line(f"Removed mapping for {code}")
            self.reload_after_save()

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
        if self._dirty_cells and not reload_only:
            self.log_line("Flushing pending cell changes before reload ...")
            self._flush_saves()
            return
        self._connect_seq += 1
        seq = self._connect_seq
        self.api.base = self.server_var.get().strip() or "http://localhost:8003"
        self.api.token = self.token_var.get().strip()
        if not reload_only:
            self.log_line(f"Connecting to {self.api.base} ...")
            self.status_var.set("Connecting ...")
        else:
            self.status_var.set("Reloading ...")
        self.api.async_call(
            "GET", "/api/v1/admin/meta",
            on_done=lambda probe: self._on_meta(probe, seq),
            on_error=lambda m: self._connect_error(m, seq))

    def _connect_error(self, message, seq):
        if seq != self._connect_seq:
            return
        self.status_var.set("Connection failed")
        if self._first_connect:
            self.log_line(f"[error] {message}")
        else:
            self._on_api_error(message)

    def _on_meta(self, probe, seq):
        if seq != self._connect_seq:
            return
        if probe is None:
            self.status_var.set("Connection failed")
            return
        if not probe.get("authenticated"):
            self.status_var.set("Connected â€” authentication failed")
            self._on_api_error("401 Unauthorized: invalid admin token")
            return
        self.status_var.set(f"Authenticated [{self.api.base}]")
        self.api.async_call(
            "GET", "/api/v1/admin/editor",
            on_done=lambda snapshot: self._on_snapshot(snapshot, seq),
            on_error=lambda m: self._connect_error(m, seq))

    def _on_snapshot(self, snapshot, seq):
        if seq != self._connect_seq:
            return
        if not snapshot:
            return
        self._first_connect = False
        self.snapshot = snapshot
        _save_config({"url": self.api.base, "token": self.api.token})
        self.meta = {key: self.snapshot.get(key, "") for key in META_FIELDS}
        raw_departments = self.snapshot.get("departments")
        if isinstance(raw_departments, list):
            self.meta["departments"] = raw_departments
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
        current_id = None
        if self.section is not None and self.section.get("sectionId"):
            current_id = self.section["sectionId"]
        rows = list(self.snapshot["sections"])
        years = sorted({self._section_meta(s)[0] for s in rows if self._section_meta(s)[0]},
                       key=lambda y: (len(y), y))
        depts = sorted({self._section_meta(s)[1] for s in rows if self._section_meta(s)[1]})
        self.section_year["values"] = years
        self.section_dept["values"] = depts
        if self.section_year_var.get() not in years:
            self.section_year_var.set("")
        if self.section_dept_var.get() not in depts:
            self.section_dept_var.set("")
        filtered = self._filtered_sections()
        self.section_list.delete(0, "end")
        for section in filtered:
            sy, sd = self._section_meta(section)
            self.section_list.insert(
                "end",
                f"{section['sectionName']:<18} {sd or '?':<5} {section.get('classroom') or ''}")
        if filtered:
            index = 0
            if current_id:
                for i, section in enumerate(filtered):
                    if section.get("sectionId") == current_id:
                        index = i
                        break
            self.section_list.selection_set(index)
            self.section = filtered[index]
            self._render_grid(self.section)
        else:
            self.section = None

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
        self.api.async_call(
            "PUT", "/api/v1/admin/meta", edited,
            on_done=lambda response: self._meta_saved(response, edited),
            on_error=self._on_api_error)

    def _meta_saved(self, response, edited):
        if response and response.get("ok"):
            self.log_line("Dataset info saved: " + ", ".join(f"{k}={edited[k]}" for k in edited))
            self.reload_after_save()

    # --- section editing ---
    def add_section(self):
        year = text_choice(self.root, "New section", "Year:",
                           ["I", "II", "III", "IV", "V", "VI", "VII", "VIII"])
        if year is None:
            return
        codes = sorted({entry["code"] for entry in self._department_entries()}) or ["CSE"]
        dept_code = text_choice(self.root, "New section", "Department code:", codes)
        if dept_code is None:
            return
        section_id = simpledialog.askstring("New section", "Section ID (e.g. J, K, A2):",
                                            initialvalue="J", parent=self.root)
        if not section_id:
            return
        section_id = section_id.strip().upper()
        classroom = simpledialog.askstring(
            "New section",
            f"Classroom (optional) for {year} {dept_code} {section_id}:",
            initialvalue="", parent=self.root) or ""
        section = {
            "sectionId": section_id,
            "sectionName": f"{year} {dept_code} {section_id}",
            "classroom": classroom.strip(),
            "weeklyTimetable": {},
        }
        self.api.async_call(
            "POST", "/api/v1/admin/section", {"section": section},
            on_done=lambda response: self._section_added(response, section_id),
            on_error=self._on_api_error)

    def _section_added(self, response, section_id):
        if response and response.get("ok"):
            self.log_line(f"Added section {section_id}")
            self.reload_after_save()

    def rename_section(self):
        if not self.section:
            messagebox.showinfo("No section", "Select a section first.")
            return
        values = {"sectionName": self.section.get("sectionName", ""), "classroom": self.section.get("classroom", "")}
        edited = _dict_dialog(self.root, f"Edit section {self.section['sectionId']}",
                              ["sectionName", "classroom"], values)
        if edited is None:
            return
        self.api.async_call(
            "PUT", f"/api/v1/admin/section/{self.section['sectionId']}", edited,
            on_done=lambda response: self._section_renamed(response, self.section['sectionId']),
            on_error=self._on_api_error)

    def _section_renamed(self, response, section_id):
        if response and response.get("ok"):
            self.log_line(f"Section {section_id} updated")
            self.reload_after_save()

    # --- department management ---
    def _department_entries(self):
        """Ordered list of {'code', 'name'} dicts: registered departments from the
        dataset meta, plus any department codes found in existing section names."""
        entries = []
        seen = set()
        registered = self.meta.get("departments", [])
        if isinstance(registered, list):
            for raw in registered:
                if not isinstance(raw, dict):
                    continue
                code = str(raw.get("code", "")).strip().upper()
                name = str(raw.get("name", "")).strip()
                if code and name and code not in seen:
                    seen.add(code)
                    entries.append({"code": code, "name": name})
        for section in self.snapshot.get("sections", []):
            code = self._section_meta(section)[1]
            if code and code not in seen:
                seen.add(code)
                entries.append({"code": code, "name": code})
        return entries

    def manage_departments(self):
        entries = self._department_entries()
        dialog = tk.Toplevel(self.root)
        dialog.title("Departments")
        dialog.transient(self.root)
        listbox = tk.Listbox(dialog, width=50)
        listbox.pack(fill="both", expand=True, padx=8, pady=8)

        def refresh():
            listbox.delete(0, "end")
            for entry in entries:
                listbox.insert("end", f"{entry['code']}  —  {entry['name']}")

        def add():
            edited = _dict_dialog(dialog, "Add department", ["code", "name"],
                                  {"code": "", "name": ""})
            if not edited:
                return
            code = edited["code"].strip().upper()
            name = edited["name"].strip()
            if not code or not name:
                messagebox.showerror("Missing fields", "Both code and name are required.")
                return
            if any(entry["code"] == code for entry in entries):
                messagebox.showerror("Duplicate", f"'{code}' already exists.")
                return
            entries.append({"code": code, "name": name})
            refresh()

        def edit():
            selection = listbox.curselection()
            if not selection:
                messagebox.showinfo("No selection", "Select a department first.")
                return
            entry = entries[selection[0]]
            edited = _dict_dialog(dialog, f"Edit {entry['code']}", ["code", "name"],
                                  {"code": entry["code"], "name": entry["name"]})
            if not edited:
                return
            code = edited["code"].strip().upper()
            name = edited["name"].strip()
            if not code or not name:
                messagebox.showerror("Missing fields", "Both code and name are required.")
                return
            if any(entry is not other and other["code"] == code for other in entries):
                messagebox.showerror("Duplicate", f"'{code}' already exists.")
                return
            entry["code"] = code
            entry["name"] = name
            refresh()

        def remove():
            selection = listbox.curselection()
            if not selection:
                return
            removed = entries.pop(selection[0])
            self.log_line(f"Department {removed['code']} will be removed on save")
            refresh()

        def save():
            self.api.async_call(
                "PUT", "/api/v1/admin/meta", {"departments": list(entries)},
                on_done=lambda response: self._departments_saved(response, dialog),
                on_error=self._on_api_error)

        refresh()
        ttk.Button(dialog, text="Add", command=add).pack(side="left", padx=8, pady=8)
        ttk.Button(dialog, text="Edit", command=edit).pack(side="left", padx=4, pady=8)
        ttk.Button(dialog, text="Remove", command=remove).pack(side="left", padx=4, pady=8)
        ttk.Button(dialog, text="Close", command=dialog.destroy).pack(side="right", padx=4, pady=8)
        ttk.Button(dialog, text="Save to server", command=save).pack(side="right", padx=8, pady=8)
        dialog.grab_set()

    def _departments_saved(self, response, dialog):
        if response and response.get("ok"):
            try:
                dialog.destroy()
            except Exception:
                pass
            self.log_line("Departments saved")
            self.reload_after_save()
        else:
            messagebox.showerror("Save failed", str(response))

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
        self._stage_cell_add(day, new_cell)
        self.log_line(f"Staged new cell in {day} â€” click SAVE CHANGES")

    def delete_cell(self):
        if not self.section:
            return
        if not self._selected_cells:
            messagebox.showinfo("Nothing selected", "Click a cell (or Ctrl+click several) first.")
            return
        plan = []
        seen = set()
        for _iid, day, row in list(self._selected_cells):
            if day not in DAYS or not self._cell_at(day, row):
                continue
            if (day, row) not in seen:
                seen.add((day, row))
                plan.append((day, row))
        if not plan:
            messagebox.showinfo("Nothing selected", "The selected cells are empty; nothing to delete.")
            return
        if len(plan) == 1:
            day, row = plan[0]
            prompt = (f"Delete {self.section['sectionName']} {day} #{row} "
                      f"({_short_cell_label(self._cell_at(day, row))})?")
        else:
            prompt = f"Delete {len(plan)} cells from {self.section['sectionName']}?"
        if not messagebox.askyesno("Delete cells?", prompt):
            return
        self.log_line(f"Deleting {len(plan)} cell(s) ...")
        self._stage_cell_deletes(plan)


# ---------- small helpers ------------------------------------------------
def _acronym(name, max_len=5):
    if not name:
        return ""
    base = re.sub(r"\s*\([Ll][^)]*\)", "", name.strip())
    for suffix in ("Laboratory", "Lab"):
        if base.endswith(" " + suffix):
            base = base[:-(len(suffix) + 1)]
            break
    words = [w for w in re.split(r"[^A-Za-z0-9]+", base) if w]
    if len(words) == 1:
        token = words[0]
        return (token[:max_len] if len(token) > max_len else token).upper()
    stop = {"and", "of", "the", "for", "with", "in", "to", "a", "an", "at"}
    significant = [w for w in words if w.lower() not in stop] or words
    letters = [w[0].upper() for w in significant]
    if not letters:
        return ""
    joined = "".join(letters)
    return joined if len(joined) <= max_len else joined[:max_len]


def _short_cell_label(cell):
    room = (cell.get("room") or "").strip()
    if cell.get("cellType") == "break":
        label = _acronym(cell.get("courseName")) or "BREAK"
        return f"{label}{(' Â· ' + room) if room else ''}"
    if cell.get("cellType") == "activity":
        label = (cell.get("activity") or "ACT").strip()
        return f"[{label}]{(' ' + room) if room else ''}"
    label = _acronym(cell.get("courseName")) or (cell.get("courseCode") or "").strip() or "?"
    return f"{label}{(' Â· ' + room) if room else ''}"


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


def _edit_cell_dialog(parent, section, day, ordinal, cell, suggestions=None):
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