package com.azu.timetable.server.web;

import com.azu.timetable.server.core.ApiException;
import com.azu.timetable.server.store.DatasetState;
import com.azu.timetable.server.store.SqliteStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminApiController {

    private static final List<String> DAY_ORDER =
            List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    private final DatasetState state;
    private final SqliteStore sqlite;
    private final String adminToken;

    public AdminApiController(DatasetState state, SqliteStore sqlite) {
        this.state = state;
        this.sqlite = sqlite;
        this.adminToken = System.getenv().getOrDefault("TIMETABLE_ADMIN_TOKEN", "insecure-development-token");
    }

    @PutMapping("/day")
    public Map<String, Object> replaceDay(@RequestBody Map<String, Object> payload,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        String sectionId = lower(String.valueOf(payload.get("sectionId")));
        Object day = payload.get("day");
        Object cells = payload.get("cells");
        if (!(cells instanceof List)) {
            throw new ApiException(400, "'cells' must be a list");
        }

        Map<String, Object> working = state.workingCopy();
        Map<String, Object> target = null;
        for (Object rawSection : (List<?>) working.getOrDefault("sections", List.of())) {
            Map<String, Object> section = (Map<String, Object>) rawSection;
            if (lower(String.valueOf(section.getOrDefault("sectionId", ""))).equals(sectionId)) {
                target = section;
                break;
            }
        }
        if (target == null) {
            throw new ApiException(404, "Section '" + sectionId + "' not found");
        }
        Map<String, Object> weekly =
                (Map<String, Object>) target.computeIfAbsent("weeklyTimetable", key -> new LinkedHashMap<>());
        if (!weekly.containsKey(day)) {
            String shown = day == null ? "None" : String.valueOf(day);
            throw new ApiException(400, "Invalid day '" + shown + "'");
        }
        weekly.put(String.valueOf(day), cells);

        persist(working);
        return okResponse();
    }

    @PostMapping("/section")
    public Map<String, Object> addSection(@RequestBody Map<String, Object> payload,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        Object rawSection = payload.get("section");
        if (!(rawSection instanceof Map)) {
            throw new ApiException(400, "'section' object required");
        }
        Map<String, Object> section = (Map<String, Object>) rawSection;
        String sectionId = lower(String.valueOf(section.get("sectionId")));

        Map<String, Object> working = state.workingCopy();
        for (Object existing : (List<?>) working.getOrDefault("sections", List.of())) {
            Map<String, Object> existingSection = (Map<String, Object>) existing;
            if (lower(String.valueOf(existingSection.getOrDefault("sectionId", ""))).equals(sectionId)) {
                throw new ApiException(409, "Section '" + sectionId + "' already exists");
            }
        }
        ((List<Object>) working.computeIfAbsent("sections", key -> new java.util.ArrayList<>())).add(section);

        persist(working);
        return okResponse();
    }

    @GetMapping("/meta")
    public Map<String, Object> adminMeta(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        boolean authenticated = false;
        try {
            requireAdmin(authorization);
            authenticated = true;
        } catch (ApiException e) {
            authenticated = false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", authenticated);
        return body;
    }

    @PutMapping("/meta")
    public Map<String, Object> updateMeta(@RequestBody Map<String, Object> payload,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        Map<String, Object> working = state.workingCopy();
        for (String key : List.of("timetableId", "academicYear", "department", "semester", "year")) {
            if (payload.containsKey(key)) {
                working.put(key, payload.get(key));
            }
        }
        persist(working);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.putAll(state.headerMeta());
        return body;
    }

    @PutMapping("/section/{sectionId}")
    public Map<String, Object> updateSection(@PathVariable String sectionId,
                                             @RequestBody Map<String, Object> payload,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        String id = lower(String.valueOf(sectionId));
        Map<String, Object> working = state.workingCopy();
        for (Object rawSection : (List<?>) working.getOrDefault("sections", List.of())) {
            Map<String, Object> section = (Map<String, Object>) rawSection;
            if (lower(String.valueOf(section.getOrDefault("sectionId", ""))).equals(id)) {
                for (Map.Entry<String, Object> entry : payload.entrySet()) {
                    if ("sectionId".equals(entry.getKey())) {
                        continue;
                    }
                    section.put(entry.getKey(), entry.getValue());
                }
                persist(working);
                return okResponse();
            }
        }
        throw new ApiException(404, "Section '" + sectionId + "' not found");
    }

    @GetMapping("/editor")
    public Map<String, Object> editorSnapshot(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        try {
Map<String, Object> snapshot = sqlite.editorSnapshot();
        snapshot.putAll(state.headerMeta());
        for (String key : List.of("academicYear", "department", "semester", "year")) {
            if (state.dataset().containsKey(key)) {
                snapshot.put(key, state.dataset().get(key));
            }
        }
        return snapshot;
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
    }

    @PutMapping("/cell")
    public Map<String, Object> updateCell(@RequestBody Map<String, Object> payload,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        String sectionId = String.valueOf(payload.get("sectionId"));
        String day = String.valueOf(payload.get("day"));
        requireValidDay(day);
        Object rawCell = payload.get("cell");
        if (!(rawCell instanceof Map)) {
            throw new ApiException(400, "'cell' must be an object");
        }
        int ordinal = requireOrdinal(payload.get("ordinal"));

        Map<String, Object> dataset;
        try {
            if (!sqlite.sectionExists(sectionId)) {
                throw new ApiException(404, "Section '" + sectionId + "' not found");
            }
            boolean updated = sqlite.updateCell(sectionId, day, ordinal, (Map<String, Object>) rawCell);
            if (!updated) {
                throw new ApiException(404, "Cell #" + ordinal + " not found for " + sectionId + " " + day);
            }
            try (var connection = sqlite.connect()) {
                sqlite.ensureSchema(connection);
                dataset = sqlite.exportJson(connection);
            }
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
        persist(dataset);
        return okResponse();
    }

    @PostMapping("/cell")
    public Map<String, Object> addCell(@RequestBody Map<String, Object> payload,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        String sectionId = String.valueOf(payload.get("sectionId"));
        String day = String.valueOf(payload.get("day"));
        requireValidDay(day);
        Object rawCell = payload.get("cell");
        if (!(rawCell instanceof Map)) {
            throw new ApiException(400, "'cell' must be an object");
        }

        int ordinal;
        Map<String, Object> dataset;
        try {
            if (!sqlite.sectionExists(sectionId)) {
                throw new ApiException(404, "Section '" + sectionId + "' not found");
            }
            ordinal = sqlite.appendCell(sectionId, day, (Map<String, Object>) rawCell);
            try (var connection = sqlite.connect()) {
                sqlite.ensureSchema(connection);
                dataset = sqlite.exportJson(connection);
            }
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
        persist(dataset);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("ordinal", ordinal);
        body.putAll(state.headerMeta());
        return body;
    }

    @DeleteMapping("/cell")
    public Map<String, Object> deleteCell(
            @RequestParam("sectionId") String sectionId,
            @RequestParam("day") String day,
            @RequestParam("ordinal") int ordinal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        requireValidDay(day);
        Map<String, Object> dataset;
        try {
            sqlite.deleteCell(sectionId, day, ordinal);
            try (var connection = sqlite.connect()) {
                sqlite.ensureSchema(connection);
                dataset = sqlite.exportJson(connection);
            }
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
        persist(dataset);
        return okResponse();
    }

    @PostMapping("/override")
    public Map<String, Object> upsertOverride(@RequestBody Map<String, Object> payload,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        Object rawOverride = payload.get("override");
        if (!(rawOverride instanceof Map)) {
            throw new ApiException(400, "'override' object required");
        }
        try {
            sqlite.upsertOverride((Map<?, ?>) rawOverride);
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
        return okResponse();
    }

    @DeleteMapping("/override/{overrideId}")
    public Map<String, Object> deleteOverride(
            @PathVariable String overrideId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        try {
            sqlite.deleteOverride(Integer.parseInt(overrideId));
        } catch (java.sql.SQLException | NumberFormatException e) {
            throw new ApiException(500, "Database error");
        }
        return okResponse();
    }

    @GetMapping("/calendar")
    public Map<String, Object> calendarOverview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            body.put("scheduleIds", sqlite.calendarScheduleIds());
            body.put("events", sqlite.allCalendarEvents());
            body.putAll(state.headerMeta());
            return body;
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
    }

    @PostMapping("/calendar")
    public Map<String, Object> replaceCalendar(@RequestBody Map<String, Object> payload,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        String scheduleId = String.valueOf(payload.get("scheduleId"));
        Object rawEvents = payload.get("events");
        if (!(rawEvents instanceof List)) {
            throw new ApiException(400, "'events' must be a list");
        }
        try {
            List<Map<?, ?>> events = new java.util.ArrayList<>();
            for (Object event : (List<?>) rawEvents) {
                if (event instanceof Map) {
                    events.add((Map<?, ?>) event);
                }
            }
            sqlite.replaceCalendar(scheduleId, events);
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
        return okResponse();
    }

    @DeleteMapping("/calendar/{eventId}")
    public Map<String, Object> deleteCalendarEvent(
            @PathVariable int eventId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        try {
            sqlite.deleteCalendarEvent(eventId);
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
        return okResponse();
    }

    // ---------- professors -------------------------------------------------

    @GetMapping("/professors")
    public Map<String, Object> professorsSnapshot(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("professors", sqlite.allProfessors());
            body.put("courses", sqlite.allProfessorCourses());
            return body;
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
    }

    @PostMapping("/professor")
    public Map<String, Object> addProfessor(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        String name = String.valueOf(payload.getOrDefault("name", "")).trim();
        String department = String.valueOf(payload.getOrDefault("department", "CSE")).trim();
        if (name.isEmpty()) {
            throw new ApiException(400, "'name' is required");
        }
        try {
            int id = sqlite.addProfessor(name, department);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("id", id);
            return body;
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
    }

    @DeleteMapping("/professor/{professorId}")
    public Map<String, Object> deleteProfessor(
            @PathVariable int professorId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        try {
            sqlite.deleteProfessor(professorId);
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
        return okResponse();
    }

    @PostMapping("/professor-course")
    public Map<String, Object> upsertProfessorCourse(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        Object rawCourse = payload.get("course");
        if (!(rawCourse instanceof Map)) {
            throw new ApiException(400, "'course' object required");
        }
        try {
            int id = sqlite.upsertProfessorCourse((Map<?, ?>) rawCourse);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("id", id);
            return body;
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
    }

    @DeleteMapping("/professor-course/{courseId}")
    public Map<String, Object> deleteProfessorCourse(
            @PathVariable int courseId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        try {
            sqlite.deleteProfessorCourse(courseId);
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
        return okResponse();
    }

    private void requireAdmin(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ApiException(401, "Missing authorization header");
        }
        if (!authorization.startsWith("Bearer ")) {
            throw new ApiException(401, "Invalid authorization scheme");
        }
        if (!authorization.substring(7).trim().equals(adminToken)) {
            throw new ApiException(401, "Invalid admin token");
        }
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static void requireValidDay(Object day) {
        String shown = day == null ? "None" : String.valueOf(day);
        if (!DAY_ORDER.contains(shown)) {
            throw new ApiException(400, "Invalid day '" + shown + "'");
        }
    }

    private static int requireOrdinal(Object value) {
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ApiException(400, "'ordinal' must be an integer");
        }
    }

    private Map<String, Object> okResponse() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.putAll(state.headerMeta());
        return body;
    }

    private void persist(Map<String, Object> dataset) {
        try {
            state.persist(dataset);
        } catch (IOException e) {
            throw new ApiException(500, "Failed to write dataset");
        }
    }
}