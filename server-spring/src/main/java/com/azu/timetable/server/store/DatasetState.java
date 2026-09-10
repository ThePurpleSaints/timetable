package com.azu.timetable.server.store;

import com.azu.timetable.server.core.CanonicalJson;
import com.azu.timetable.server.core.JsonCodec;
import com.azu.timetable.server.core.PathResolver;
import com.azu.timetable.server.core.Timestamp;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DatasetState {

    private static final Map<String, Integer> ROMAN_YEARS = Map.of(
            "I", 1, "II", 2, "III", 3, "IV", 4, "V", 5, "VI", 6, "VII", 7, "VIII", 8
    );

    private final PathResolver paths;
    private final SqliteStore sqlite;
    private final CalendarReader calendar;
    private final Object writeLock = new Object();

    private volatile Map<String, Object> dataset;
    private volatile String version;
    private volatile String updatedAt;
    private volatile String minClientVersion = "";
    private volatile String sunsetMessage = "";

    public DatasetState(PathResolver paths, SqliteStore sqlite, CalendarReader calendar) {
        this.paths = paths;
        this.sqlite = sqlite;
        this.calendar = calendar;
    }

    @PostConstruct
    void start() {
        synchronized (writeLock) {
            loadFromDisk();
            loadVersionPolicy();
            seedDatabase();
        }
    }

    public Map<String, Object> dataset() {
        return dataset;
    }

    public String version() {
        return version;
    }

    public String updatedAt() {
        return updatedAt;
    }

    public List<?> sections() {
        return (List<?>) dataset.getOrDefault("sections", List.of());
    }

    public Map<String, Object> headerMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("version", version);
        meta.put("updated_at", updatedAt);
        String timetableId = String.valueOf(dataset.getOrDefault("timetableId", "")).trim();
        if (!timetableId.isEmpty()) {
            meta.put("timetableId", timetableId);
        }
        if (!minClientVersion.isEmpty()) {
            meta.put("min_client_version", minClientVersion);
        }
        if (!sunsetMessage.isEmpty()) {
            meta.put("sunset_message", sunsetMessage);
        }
        return meta;
    }

    public Map<String, Object> workingCopy() {
        synchronized (writeLock) {
            return (Map<String, Object>) JsonCodec.deepCopy(dataset);
        }
    }

    public void persist(Map<String, Object> next) throws IOException {
        synchronized (writeLock) {
            String raw = CanonicalJson.canonical(next);
            Files.writeString(paths.timetableJson(), raw, StandardCharsets.UTF_8);
            for (java.nio.file.Path mirror : paths.mirroredJsonFiles) {
                try {
                    Files.writeString(mirror, raw, StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                }
            }
            importIntoDatabase(next);
            dataset = next;
            version = CanonicalJson.versionOf(raw);
            updatedAt = Timestamp.now();
        }
    }

    private void loadFromDisk() {
        try {
            String raw = Files.readString(paths.timetableJson(), StandardCharsets.UTF_8);
            dataset = (Map<String, Object>) JsonCodec.parse(raw);
            version = CanonicalJson.version(dataset);
            updatedAt = Timestamp.fromFile(paths.timetableJson());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + paths.timetableJson(), e);
        }
    }

    private void loadVersionPolicy() {
        try {
            var policy = paths.versionPolicy();
            if (policy != null && Files.isRegularFile(policy)) {
                String raw = Files.readString(policy, StandardCharsets.UTF_8);
                Map<String, Object> parsed = (Map<String, Object>) JsonCodec.parse(raw);
                minClientVersion = String.valueOf(parsed.getOrDefault("min_client_version", "")).trim();
                sunsetMessage = String.valueOf(parsed.getOrDefault("sunset_message", "")).trim();
            }
        } catch (Exception failure) {
            LOG.log(java.util.logging.Level.WARNING, "Could not read version policy", failure);
        }
    }

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger("DatasetState");

    private void seedDatabase() {
        try {
            try (var connection = sqlite.connect()) {
                sqlite.ensureSchema(connection);
                if (sqlite.countSchedule(connection) == 0 && !sections().isEmpty()) {
                    sqlite.importJson(connection, dataset);
                }
                seedCalendar(connection);
                seedProfessors(connection);
            }
        } catch (Exception failure) {
            LOG.log(java.util.logging.Level.SEVERE, "Database seeding failed", failure);
        }
    }

    private void seedCalendar(java.sql.Connection connection) {
        try {
            if (sqlite.countCalendarEvents(connection) == 0
                    && paths.calendarJson() != null && Files.isRegularFile(paths.calendarJson())) {
                List<Map<String, Object>> events = calendar.events();
                if (!events.isEmpty()) {
                    sqlite.replaceCalendar(computeCalendarScheduleId(dataset, events), events);
                    LOG.info("Seeded " + events.size() + " calendar events into the database");
                }
            }
        } catch (Exception failure) {
            LOG.log(java.util.logging.Level.SEVERE, "Calendar seeding failed", failure);
        }
    }

    private void seedProfessors(java.sql.Connection connection) {
        try {
            String department = String.valueOf(dataset.getOrDefault("department", "CSE")).trim();
            sqlite.seedProfessors(connection, department);
            LOG.info("Professor seeding complete");
        } catch (Exception failure) {
            LOG.log(java.util.logging.Level.SEVERE, "Professor seeding failed", failure);
        }
    }

    static String computeCalendarScheduleId(Map<String, Object> dataset, List<Map<String, Object>> events) {
        String academicYear = String.valueOf(dataset.getOrDefault("academicYear", "")).trim();
        String years = "";
        String[] parts = academicYear.split("-");
        if (parts.length >= 2 && parts[0].length() >= 2 && parts[1].length() >= 2) {
            years = parts[0].substring(parts[0].length() - 2) + "-" + parts[1].substring(parts[1].length() - 2);
        }
        String term = "EVE";
        if (!events.isEmpty()) {
            String firstDate = String.valueOf(events.get(0).get("date"));
            if (firstDate.length() >= 7) {
                int month = Integer.parseInt(firstDate.substring(5, 7));
                if (month >= 7) {
                    term = "ODD";
                }
            }
        }
        String yearOfStudy = String.valueOf(dataset.getOrDefault("year", "")).trim();
        int yearIndex = ROMAN_YEARS.getOrDefault(yearOfStudy.toUpperCase(Locale.ROOT), 1);
        return "CAL-" + term + "-" + years + "-Y" + yearIndex;
    }

    private void importIntoDatabase(Map<String, Object> next) {
        try {
            sqlite.importJson(next);
        } catch (Exception failure) {
            LOG.log(java.util.logging.Level.SEVERE, "Database import failed", failure);
        }
    }
}