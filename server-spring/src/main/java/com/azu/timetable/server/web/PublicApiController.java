package com.azu.timetable.server.web;

import com.azu.timetable.server.core.ApiException;
import com.azu.timetable.server.store.DatasetState;
import com.azu.timetable.server.store.SqliteStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
public class PublicApiController {

    private final DatasetState state;
    private final SqliteStore sqlite;

    public PublicApiController(DatasetState state, SqliteStore sqlite) {
        this.state = state;
        this.sqlite = sqlite;
    }

    @GetMapping("/api/v1/meta")
    public Map<String, Object> meta() {
        List<Object> sectionIds = new ArrayList<>();
        for (Object section : state.sections()) {
            sectionIds.add(((Map<?, ?>) section).getOrDefault("sectionId", null));
        }
        Map<String, Object> body = new LinkedHashMap<>(state.headerMeta());
        body.put("section_count", state.sections().size());
        body.put("section_ids", sectionIds);
        return body;
    }

    @GetMapping("/timetable.json")
    public Object rawJson() {
        return state.dataset();
    }

    @GetMapping("/api/v1/timetable")
    public Map<String, Object> timetable(
            @RequestParam(value = "class", required = false) String className) {
        Map<String, Object> body = new LinkedHashMap<>(state.headerMeta());
        if (className != null) {
            for (Object rawSection : state.sections()) {
                Map<String, Object> section = (Map<String, Object>) rawSection;
                String sectionId = String.valueOf(section.getOrDefault("sectionId", "")).toLowerCase(Locale.ROOT);
                if (sectionId.equals(className.toLowerCase(Locale.ROOT))) {
                    body.put("section", section);
                    return body;
                }
            }
            throw new ApiException(404, "Section '" + className + "' not found");
        }
        body.put("dataset", state.dataset());
        return body;
    }

    @GetMapping("/api/v1/classes")
    public Map<String, Object> classes() {
        Map<String, Object> body = new LinkedHashMap<>(state.headerMeta());
        String department = String.valueOf(state.dataset().getOrDefault("department", "")).trim();
        List<Object> years = new ArrayList<>();
        List<Object> classList = new ArrayList<>();
        for (Object raw : state.sections()) {
            Map<String, Object> section = (Map<String, Object>) raw;
            String sectionId = String.valueOf(section.getOrDefault("sectionId", ""));
            String sectionName = String.valueOf(section.getOrDefault("sectionName", ""));
            String year = DatasetState.yearFromSectionName(sectionName);
            if (!year.isEmpty() && !years.contains(year)) {
                years.add(year);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sectionId", sectionId);
            entry.put("sectionName", sectionName);
            entry.put("classroom", section.getOrDefault("classroom", ""));
            entry.put("department", department);
            entry.put("year", year);
            classList.add(entry);
        }
        Map<String, Integer> roman = DatasetState.romanOrdinal();
        years.sort((a, b) -> Integer.compare(
                roman.getOrDefault(String.valueOf(a), 99),
                roman.getOrDefault(String.valueOf(b), 99)));
        body.put("departments", List.of(department));
        body.put("years", years);
        body.put("classes", classList);
        return body;
    }

    @GetMapping("/api/v1/calendar")
    public Map<String, Object> calendar() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            body.put("events", sqlite.allCalendarEvents());
            body.putAll(state.headerMeta());
            return body;
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
    }

    @GetMapping("/api/v1/overrides")
    public Map<String, Object> overrides() {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("overrides", sqlite.allOverrides());
            body.putAll(state.headerMeta());
            return body;
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
    }

    @GetMapping("/api/v1/db/timetable")
    public Map<String, Object> timetableFromDatabase(
            @RequestParam(value = "class", required = false) String className) {
        Map<String, Object> body = new LinkedHashMap<>(state.headerMeta());
        try {
            if (className != null) {
                Map<String, Object> result = sqlite.sectionForClass(className);
                if (result == null) {
                    throw new ApiException(404, "Section '" + className + "' not found");
                }
                body.put("section", result.get("section"));
                body.put("overrides", result.get("overrides"));
                return body;
            }
            try (Connection connection = sqlite.connect()) {
                sqlite.ensureSchema(connection);
                body.put("dataset", sqlite.exportJson(connection));
            }
            body.put("overrides", List.of());
            return body;
        } catch (java.sql.SQLException e) {
            throw new ApiException(500, "Database error");
        }
    }
}