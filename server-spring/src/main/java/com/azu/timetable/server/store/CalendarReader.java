package com.azu.timetable.server.store;

import com.azu.timetable.server.core.JsonCodec;
import com.azu.timetable.server.core.PathResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class CalendarReader {

    private static final Pattern HOLIDAY = Pattern.compile("(Holiday|No Class Work|Pooja Holidays)");
    private static final Pattern DATE_FORMAT = Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2})$");

    private final PathResolver paths;

    public CalendarReader(PathResolver paths) {
        this.paths = paths;
    }

    public List<Map<String, Object>> events() {
        if (!Files.isRegularFile(paths.calendarJson())) {
            return List.of();
        }
        try {
            String raw = Files.readString(paths.calendarJson(), StandardCharsets.UTF_8);
            List<?> items = (List<?>) JsonCodec.parse(raw);
            List<Map<String, Object>> events = new ArrayList<>();
            for (Object itemValue : items) {
                Map<String, Object> item = (Map<String, Object>) itemValue;
                String details = String.valueOf(item.getOrDefault("Details", "")).trim();
                boolean holiday = HOLIDAY.matcher(details).find();
                String dateField = String.valueOf(item.getOrDefault("Date", "")).trim();
                for (String part : dateField.split("\\s*&\\s*")) {
                    String iso = toIsoDate(part);
                    if (iso != null) {
                        Map<String, Object> event = new LinkedHashMap<>();
                        event.put("date", iso);
                        event.put("day", item.getOrDefault("Day", ""));
                        event.put("details", details);
                        event.put("holiday", holiday);
                        events.add(event);
                    }
                }
            }
            events.sort(Comparator.comparing(event -> String.valueOf(event.get("date"))));
            return events;
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static String toIsoDate(String value) {
        var matcher = DATE_FORMAT.matcher(value.strip());
        if (!matcher.matches()) {
            return null;
        }
        int day = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int year = Integer.parseInt(matcher.group(3));
        if (year < 100) {
            year += 2000;
        }
        return String.format("%04d-%02d-%02d", year, month, day);
    }
}