package com.azu.timetable.server.web;

import com.azu.timetable.server.core.ApiException;
import com.azu.timetable.server.core.PathResolver;
import com.azu.timetable.server.store.DatasetState;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SpaController {

    private final PathResolver paths;
    private final DatasetState state;

    public SpaController(PathResolver paths, DatasetState state) {
        this.paths = paths;
        this.state = state;
    }

    @GetMapping("/**")
    public ResponseEntity<?> route(HttpServletRequest request) {
        String relative = request.getRequestURI().substring(request.getContextPath().length()).replaceFirst("^/", "");
        if (!hasSpa()) {
            if (relative.isEmpty()) {
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootInfo());
            }
            throw new ApiException(404, "Not Found");
        }
        if (relative.isEmpty()) {
            return indexResponse();
        }
        Path base = paths.webDir.toAbsolutePath().normalize();
        Path candidate = base.resolve(relative).normalize();
        if (Files.isRegularFile(candidate) && candidate.startsWith(base)) {
            return fileResponse(candidate);
        }
        return indexResponse();
    }

    private boolean hasSpa() {
        return Files.isDirectory(paths.webDir) && Files.isRegularFile(indexFile());
    }

    private Path indexFile() {
        return paths.webDir.resolve("index.html");
    }

    private ResponseEntity<byte[]> indexResponse() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/html"));
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache");
        headers.add("CDN-Cache-Control", "no-cache");
        return read(indexFile(), headers);
    }

    private ResponseEntity<byte[]> fileResponse(Path file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType(file)));
        return read(file, headers);
    }

    private ResponseEntity<byte[]> read(Path file, HttpHeaders headers) {
        try {
            return new ResponseEntity<>(Files.readAllBytes(file), headers, HttpStatus.OK);
        } catch (IOException e) {
            throw new ApiException(404, "Not Found");
        }
    }

    private Map<String, Object> rootInfo() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("meta", "/api/v1/meta");
        endpoints.put("timetable", "/api/v1/timetable");
        endpoints.put("timetable_by_class", "/api/v1/timetable?class=A");
        endpoints.put("raw_json", "/timetable.json");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "Timetable Update Server");
        info.put("version", state.version());
        info.put("updated_at", state.updatedAt());
        info.put("endpoints", endpoints);
        return info;
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html";
        if (name.endsWith(".js")) return "text/javascript";
        if (name.endsWith(".mjs")) return "text/javascript";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".map")) return "application/json";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".woff")) return "font/woff";
        if (name.endsWith(".ttf")) return "font/ttf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}