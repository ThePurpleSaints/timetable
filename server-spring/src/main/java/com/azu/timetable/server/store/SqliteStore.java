package com.azu.timetable.server.store;

import com.azu.timetable.server.core.PathResolver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class SqliteStore {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS courses (
                course_code TEXT NOT NULL,
                course_type TEXT NOT NULL CHECK (course_type IN ('theory','laboratory')),
                course_name TEXT NOT NULL,
                PRIMARY KEY (course_code, course_type)
            );

            CREATE TABLE IF NOT EXISTS sections (
                section_id TEXT PRIMARY KEY,
                section_name TEXT NOT NULL,
                classroom TEXT
            );

            CREATE TABLE IF NOT EXISTS schedule (
                section_id TEXT NOT NULL REFERENCES sections(section_id),
                day TEXT NOT NULL,
                ordinal INTEGER NOT NULL,
                period INTEGER NOT NULL,
                time TEXT NOT NULL,
                cell_type TEXT NOT NULL CHECK (cell_type IN ('theory','laboratory','activity','break')),
                course_code TEXT,
                course_name TEXT,
                activity TEXT,
                lab_groups TEXT,
                room TEXT,
                in_charge TEXT,
                PRIMARY KEY (section_id, day, ordinal)
            );

            CREATE TABLE IF NOT EXISTS overrides (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                override_date TEXT NOT NULL,
                section_id TEXT NOT NULL REFERENCES sections(section_id),
                period INTEGER,
                cell_type TEXT,
                course_code TEXT,
                activity TEXT,
                room TEXT,
                is_cancelled INTEGER NOT NULL DEFAULT 0,
                reason TEXT,
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            );

            CREATE INDEX IF NOT EXISTS idx_schedule_section_day ON schedule(section_id, day);
            CREATE INDEX IF NOT EXISTS idx_schedule_section ON schedule(section_id);
            CREATE INDEX IF NOT EXISTS idx_overrides_date_section ON overrides(override_date, section_id);
            CREATE INDEX IF NOT EXISTS idx_overrides_section_date ON overrides(section_id, override_date);

            CREATE TABLE IF NOT EXISTS calendar_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                schedule_id TEXT NOT NULL,
                event_date TEXT NOT NULL,
                day TEXT NOT NULL,
                details TEXT NOT NULL,
                is_holiday INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            );

            CREATE INDEX IF NOT EXISTS idx_calendar_schedule_date ON calendar_events(schedule_id, event_date);
            """;

    private static final Pattern LAB_GROUPS = Pattern.compile("\\(((?:L\\d+)(?:,\\s*L\\d+)*)\\)$");
    private static final List<String> META_KEYS =
            List.of("department", "year", "semester", "academicYear", "timetableId");
    private static final List<String> DAY_ORDER =
            List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    private final PathResolver paths;

    public SqliteStore(PathResolver paths) {
        this.paths = paths;
    }

    public Connection connect() throws SQLException {
        String url = "jdbc:sqlite:" + paths.sqliteFile.toString().replace('\\', '/');
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public void ensureSchema(Connection connection) throws SQLException {
        String[] statements = SCHEMA.split(";\\s*\\n");
        for (String raw : statements) {
            String statement = raw.trim();
            if (statement.isEmpty()) {
                continue;
            }
            try (Statement operation = connection.createStatement()) {
                operation.execute(statement);
            }
        }
    }

    public void importJson(Map<String, Object> dataset) throws SQLException {
        try (Connection connection = connect()) {
            importJson(connection, dataset);
        }
    }

    public void importJson(Connection connection, Map<String, Object> dataset) throws SQLException {
        ensureSchema(connection);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM schedule");
            statement.executeUpdate("DELETE FROM sections");
            statement.executeUpdate("DELETE FROM courses");
            statement.executeUpdate("DELETE FROM meta");
        }

        for (String key : META_KEYS) {
            Object value = dataset.get(key);
            if (value != null) {
                execute(connection, "INSERT INTO meta (key, value) VALUES (?, ?)", key, String.valueOf(value));
            }
        }

        Map<String, String> courses = new LinkedHashMap<>();
        List<?> sections = (List<?>) dataset.getOrDefault("sections", List.of());
        for (Object rawSection : sections) {
            Map<String, Object> section = (Map<String, Object>) rawSection;
            execute(connection,
                    "INSERT INTO sections (section_id, section_name, classroom) VALUES (?, ?, ?)",
                    String.valueOf(section.get("sectionId")),
                    String.valueOf(section.getOrDefault("sectionName", "")),
                    String.valueOf(section.getOrDefault("classroom", "")));

            @SuppressWarnings("unchecked")
            Map<String, Object> weekly = (Map<String, Object>) section.get("weeklyTimetable");
            if (weekly == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : weekly.entrySet()) {
                String day = entry.getKey();
                List<?> cells = (List<?>) entry.getValue();
                int ordinal = 0;
                for (Object rawCell : cells) {
                    Map<String, Object> cell = (Map<String, Object>) rawCell;
                    String cellType = String.valueOf(cell.getOrDefault("cellType", ""));
                    String[] split = splitLabGroups(String.valueOf(cell.getOrDefault("courseName", "")));
                    String name = split[0];
                    String groups = split[1];
                    if (!cellType.equals("laboratory")) {
                        groups = null;
                    }
                    if (cellType.equals("theory") || cellType.equals("laboratory")) {
                        Object code = cell.get("courseCode");
                        if (code != null) {
                            courses.put(code + "\u0001" + cellType, name);
                        }
                    }
                    execute(connection,
                            "INSERT INTO schedule "
                                    + "(section_id, day, ordinal, period, time, cell_type, course_code, "
                                    + "course_name, activity, lab_groups, room, in_charge) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            String.valueOf(section.get("sectionId")),
                            day,
                            ordinal,
                            intOrZero(cell.get("period")),
                            String.valueOf(cell.getOrDefault("time", "")),
                            cellType,
                            nullableString(cell.get("courseCode")),
                            name,
                            nullableString(cell.get("activity")),
                            groups,
                            nullableString(cell.get("room")),
                            nullableString(cell.get("inCharge")));
                    ordinal++;
                }
            }
        }

        List<String> sortedCourses = new ArrayList<>(courses.keySet());
        sortedCourses.sort((a, b) -> {
            String[] left = a.split("\u0001", 2);
            String[] right = b.split("\u0001", 2);
            int byCode = left[0].compareTo(right[0]);
            return byCode != 0 ? byCode : left[1].compareTo(right[1]);
        });
        for (String key : sortedCourses) {
            String[] parts = key.split("\u0001", 2);
            execute(connection,
                    "INSERT OR REPLACE INTO courses (course_code, course_type, course_name) VALUES (?, ?, ?)",
                    parts[0], parts[1], courses.get(key));
        }
    }

    public Map<String, Object> exportJson(Connection connection) throws SQLException {
        ensureSchema(connection);
        Map<String, Object> dataset = new LinkedHashMap<>();
        List<Map<String, Object>> metaRows = select(connection,
                "SELECT key, value FROM meta WHERE key IN (%s)"
                        .formatted(String.join(",", java.util.Collections.nCopies(META_KEYS.size(), "?"))),
                META_KEYS.toArray());
        for (Map<String, Object> row : metaRows) {
            dataset.put(String.valueOf(row.get("key")), row.get("value"));
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Map<String, Object> sectionRow : select(connection, "SELECT * FROM sections ORDER BY section_id")) {
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("sectionId", sectionRow.get("section_id"));
            section.put("sectionName", sectionRow.get("section_name"));
            section.put("classroom", sectionRow.get("classroom"));
            Map<String, List<Object>> weekly = new LinkedHashMap<>();
            for (Map<String, Object> row : select(connection,
                    "SELECT * FROM schedule WHERE section_id = ? ORDER BY day, ordinal",
                    sectionRow.get("section_id"))) {
                String day = String.valueOf(row.get("day"));
                weekly.computeIfAbsent(day, k -> new ArrayList<>()).add(cellFromRow(row));
            }
            section.put("weeklyTimetable", weekly);
            sections.add(section);
        }
        dataset.put("sections", sections);
        return dataset;
    }

    public Map<String, Object> sectionForClass(String classId) throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            Map<String, Object> row = selectOne(connection,
                    "SELECT * FROM sections WHERE section_id = ? COLLATE NOCASE", classId);
            if (row == null) {
                return null;
            }
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("sectionId", row.get("section_id"));
            section.put("sectionName", row.get("section_name"));
            section.put("classroom", row.get("classroom"));
            Map<String, List<Object>> weekly = new LinkedHashMap<>();
            for (Map<String, Object> scheduleRow : select(connection,
                    "SELECT * FROM schedule WHERE section_id = ? COLLATE NOCASE ORDER BY day, ordinal",
                    classId)) {
                String day = String.valueOf(scheduleRow.get("day"));
                weekly.computeIfAbsent(day, k -> new ArrayList<>()).add(cellFromRow(scheduleRow));
            }
            section.put("weeklyTimetable", weekly);
            List<Map<String, Object>> overrides = select(connection,
                    "SELECT * FROM overrides WHERE section_id = ? COLLATE NOCASE ORDER BY override_date",
                    classId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("section", section);
            result.put("overrides", overrides);
            return result;
        }
    }

    public List<Map<String, Object>> allOverrides() throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            return select(connection, "SELECT * FROM overrides ORDER BY override_date, section_id");
        }
    }

    public boolean sectionExists(String sectionId) throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            return selectOne(connection, "SELECT 1 AS present FROM sections WHERE section_id = ?", sectionId) != null;
        }
    }

    public Map<String, Object> editorSnapshot() throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            List<Map<String, Object>> sections = new ArrayList<>();
            for (Map<String, Object> sectionRow : select(connection, "SELECT * FROM sections ORDER BY section_id")) {
                Map<String, List<Object>> byDay = new LinkedHashMap<>();
                for (String day : DAY_ORDER) {
                    byDay.put(day, new ArrayList<>());
                }
                for (Map<String, Object> scheduleRow : select(connection,
                        "SELECT * FROM schedule WHERE section_id=? ORDER BY ordinal",
                        sectionRow.get("section_id"))) {
                    String day = String.valueOf(scheduleRow.get("day"));
                    if (byDay.containsKey(day)) {
                        byDay.get(day).add(cellFromRow(scheduleRow));
                    }
                }
                Map<String, Object> section = new LinkedHashMap<>();
                section.put("sectionId", sectionRow.get("section_id"));
                section.put("sectionName", sectionRow.get("section_name"));
                section.put("classroom", sectionRow.get("classroom"));
                Map<String, Object> weekly = new LinkedHashMap<>();
                for (String day : DAY_ORDER) {
                    if (!byDay.get(day).isEmpty()) {
                        weekly.put(day, byDay.get(day));
                    }
                }
                section.put("weeklyTimetable", weekly);
                sections.add(section);
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("sections", sections);
            snapshot.put("courses", select(connection, "SELECT * FROM courses ORDER BY course_code, course_type"));
            snapshot.put("overrides", select(connection, "SELECT * FROM overrides ORDER BY override_date, section_id"));
            Map<String, Object> meta = new LinkedHashMap<>();
            for (Map<String, Object> row : select(connection,
                    "SELECT key, value FROM meta WHERE key IN (%s)"
                            .formatted(String.join(",", java.util.Collections.nCopies(META_KEYS.size(), "?"))),
                    META_KEYS.toArray())) {
                meta.put(String.valueOf(row.get("key")), row.get("value"));
            }
            snapshot.put("meta", meta);
            List<Map<String, Object>> calendarEvents = new ArrayList<>();
            for (Map<String, Object> row : select(connection,
                    "SELECT * FROM calendar_events ORDER BY event_date")) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("id", row.get("id"));
                event.put("scheduleId", row.get("schedule_id"));
                event.put("date", row.get("event_date"));
                event.put("day", row.get("day"));
                event.put("details", row.get("details"));
                event.put("holiday", toInt(row.get("is_holiday")) != 0);
                calendarEvents.add(event);
            }
            snapshot.put("calendar", calendarEvents);
            return snapshot;
        }
    }

    public boolean updateCell(String sectionId, String day, int ordinal, Map<String, Object> cell) throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            Map<String, Object> existing = selectOne(connection,
                    "SELECT 1 AS present FROM schedule WHERE section_id=? AND day=? AND ordinal=?",
                    sectionId, day, ordinal);
            if (existing == null) {
                return false;
            }
            String cellType = String.valueOf(cell.getOrDefault("cellType", ""));
            String[] split = splitLabGroups(String.valueOf(cell.getOrDefault("courseName", "")));
            String name = split[0];
            String groups = cellType.equals("laboratory") ? split[1] : null;
            execute(connection,
                    "UPDATE schedule SET period=?, time=?, cell_type=?, course_code=?, course_name=?, "
                            + "activity=?, lab_groups=?, room=?, in_charge=? "
                            + "WHERE section_id=? AND day=? AND ordinal=?",
                    intOrZero(cell.get("period")),
                    String.valueOf(cell.getOrDefault("time", "")),
                    cellType,
                    nullableString(cell.get("courseCode")),
                    name,
                    nullableString(cell.get("activity")),
                    groups,
                    nullableString(cell.get("room")),
                    nullableString(cell.get("inCharge")),
                    sectionId, day, ordinal);
            return true;
        }
    }

    public int appendCell(String sectionId, String day, Map<String, Object> cell) throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            Map<String, Object> count = selectOne(connection,
                    "SELECT COALESCE(MAX(ordinal), -1) + 1 AS n FROM schedule WHERE section_id=? AND day=?",
                    sectionId, day);
            int ordinal = toInt(count.get("n"));
            String cellType = String.valueOf(cell.getOrDefault("cellType", ""));
            String[] split = splitLabGroups(String.valueOf(cell.getOrDefault("courseName", "")));
            String name = split[0];
            String groups = cellType.equals("laboratory") ? split[1] : null;
            execute(connection,
                    "INSERT INTO schedule "
                            + "(section_id, day, ordinal, period, time, cell_type, course_code, "
                            + "course_name, activity, lab_groups, room, in_charge) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    sectionId, day, ordinal,
                    intOrZero(cell.get("period")),
                    String.valueOf(cell.getOrDefault("time", "")),
                    cellType,
                    nullableString(cell.get("courseCode")),
                    name,
                    nullableString(cell.get("activity")),
                    groups,
                    nullableString(cell.get("room")),
                    nullableString(cell.get("inCharge")));
            return ordinal;
        }
    }

    public void deleteCell(String sectionId, String day, int ordinal) throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            execute(connection, "DELETE FROM schedule WHERE section_id=? AND day=? AND ordinal=?",
                    sectionId, day, ordinal);
            execute(connection,
                    "UPDATE schedule SET ordinal = ordinal - 1 WHERE section_id=? AND day=? AND ordinal > ?",
                    sectionId, day, ordinal);
        }
    }

    public void upsertOverride(Map<?, ?> override) throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            Object cancelled = override.get("cancelled");
            boolean isCancelled = Boolean.TRUE.equals(cancelled)
                    || (cancelled instanceof Number n && n.intValue() != 0);
            Object id = override.get("id");
            String overrideDate = String.valueOf(override.get("date"));
            String sectionId = String.valueOf(override.get("sectionId"));
            Object period = override.get("period");
            String cellType = nullableString(override.get("cellType"));
            String courseCode = nullableString(override.get("courseCode"));
            String activity = nullableString(override.get("activity"));
            String room = nullableString(override.get("room"));
            String reason = nullableString(override.get("reason"));
            int cancelledValue = isCancelled ? 1 : 0;

            if (id != null) {
                execute(connection,
                        "UPDATE overrides SET override_date=?, section_id=?, period=?, cell_type=?, "
                                + "course_code=?, activity=?, room=?, is_cancelled=?, reason=? WHERE id=?",
                        overrideDate, sectionId, period, cellType, courseCode, activity, room,
                        cancelledValue, reason, toInt(id));
            } else {
                execute(connection,
                        "INSERT INTO overrides (override_date, section_id, period, cell_type, course_code, "
                                + "activity, room, is_cancelled, reason) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        overrideDate, sectionId, period, cellType, courseCode, activity, room,
                        cancelledValue, reason);
            }
        }
    }

    public void deleteOverride(int id) throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            execute(connection, "DELETE FROM overrides WHERE id=?", id);
        }
    }

    public List<Map<String, Object>> allCalendarEvents() throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            List<Map<String, Object>> events = new ArrayList<>();
            for (Map<String, Object> row : select(connection,
                    "SELECT * FROM calendar_events ORDER BY event_date")) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("date", row.get("event_date"));
                event.put("day", row.get("day"));
                event.put("details", row.get("details"));
                event.put("holiday", toInt(row.get("is_holiday")) != 0);
                event.put("scheduleId", row.get("schedule_id"));
                event.put("id", row.get("id"));
                events.add(event);
            }
            return events;
        }
    }

    public List<String> calendarScheduleIds() throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            List<String> ids = new ArrayList<>();
            for (Map<String, Object> row : select(connection,
                    "SELECT DISTINCT schedule_id FROM calendar_events ORDER BY schedule_id")) {
                ids.add(String.valueOf(row.get("schedule_id")));
            }
            return ids;
        }
    }

    public int countCalendarEvents(Connection connection) throws SQLException {
        Map<String, Object> row = selectOne(connection, "SELECT COUNT(*) AS n FROM calendar_events");
        return toInt(row.get("n"));
    }

    public void replaceCalendar(String scheduleId, List<?> events) throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            execute(connection, "DELETE FROM calendar_events WHERE schedule_id = ?", scheduleId);
            for (Object rawEvent : events) {
                Map<?, ?> event = (Map<?, ?>) rawEvent;
                boolean holiday = Boolean.TRUE.equals(event.get("holiday"))
                        || (event.get("holiday") instanceof Number n && n.intValue() != 0);
                String day = String.valueOf(event.get("day") == null ? "" : event.get("day"));
                String details = String.valueOf(event.get("details") == null ? "" : event.get("details"));
                execute(connection,
                        "INSERT INTO calendar_events (schedule_id, event_date, day, details, is_holiday) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        scheduleId,
                        String.valueOf(event.get("date")),
                        day,
                        details,
                        holiday ? 1 : 0);
            }
        }
    }

    public void deleteCalendarEvent(int id) throws SQLException {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            execute(connection, "DELETE FROM calendar_events WHERE id = ?", id);
        }
    }

    public int countSchedule(Connection connection) throws SQLException {
        Map<String, Object> row = selectOne(connection, "SELECT COUNT(*) AS n FROM schedule");
        return toInt(row.get("n"));
    }

    static Map<String, Object> cellFromRow(Map<String, Object> row) {
        String cellType = String.valueOf(row.get("cell_type"));
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("period", row.get("period"));
        cell.put("time", row.get("time"));
        cell.put("cellType", cellType);
        if (row.get("course_code") != null) {
            cell.put("courseCode", row.get("course_code"));
        }
        switch (cellType) {
            case "laboratory" -> {
                String name = String.valueOf(row.get("course_name") == null ? "" : row.get("course_name"));
                Object groups = row.get("lab_groups");
                if (groups != null && !String.valueOf(groups).isEmpty()) {
                    name = name + " (" + groups + ")";
                }
                cell.put("courseName", name);
            }
            case "theory" -> {
                Object name = row.get("course_name");
                cell.put("courseName", name == null ? "" : String.valueOf(name));
            }
            case "activity" -> {
                Object activity = row.get("activity");
                cell.put("activity", activity == null ? "" : String.valueOf(activity));
            }
            case "break" -> {
                Object name = row.get("course_name");
                cell.put("courseName", name == null || String.valueOf(name).isEmpty() ? "Break" : String.valueOf(name));
            }
            default -> {
                //
            }
        }
        if (row.get("room") != null) {
            cell.put("room", row.get("room"));
        }
        if (row.get("in_charge") != null) {
            cell.put("inCharge", row.get("in_charge"));
        }
        return cell;
    }

    private static String[] splitLabGroups(String name) {
        if (name == null || name.isEmpty()) {
            return new String[]{name, null};
        }
        Matcher matcher = LAB_GROUPS.matcher(name);
        if (!matcher.find()) {
            return new String[]{name, null};
        }
        String rest = name.substring(0, matcher.start()).trim();
        return new String[]{rest, matcher.group(1)};
    }

    private static Map<String, Object> selectOne(Connection connection, String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = select(connection, sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static List<Map<String, Object>> select(Connection connection, String sql, Object... params) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (result.next()) {
                    rows.add(readRow(result));
                }
                return rows;
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... params) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        }
    }

    private static void bind(java.sql.PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            setParam(statement, i + 1, params[i]);
        }
    }

    private static void setParam(java.sql.PreparedStatement statement, int index, Object value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else if (value instanceof Long || value instanceof Integer) {
            statement.setInt(index, toInt(value));
        } else if (value instanceof Double) {
            statement.setDouble(index, ((Number) value).doubleValue());
        } else {
            statement.setObject(index, String.valueOf(value));
        }
    }

    private static int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private static int intOrZero(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> readRow(ResultSet result) throws SQLException {
        ResultSetMetaData metadata = result.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            row.put(metadata.getColumnLabel(i), result.getObject(i));
        }
        return row;
    }
}