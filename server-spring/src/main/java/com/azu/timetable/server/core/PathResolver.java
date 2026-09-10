package com.azu.timetable.server.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class PathResolver {

    public final Path dataDir;
    public final Path webDir;
    public final List<Path> mirroredJsonFiles;
    public final Path sqliteFile;

    public PathResolver() {
        Path repoRoot = findRepoRoot();
        String dataOverride = System.getenv("TIMETABLE_DATA_DIR");
        String webOverride = System.getenv("TIMETABLE_WEB_DIR");
        String sqliteOverride = System.getenv("TIMETABLE_SQLITE_FILE");
        if (repoRoot == null && dataOverride == null) {
            throw new IllegalStateException(
                    "Could not locate the repository root (server/data/timetable.json) and TIMETABLE_DATA_DIR is unset");
        }
        dataDir = dataOverride != null
                ? Paths.get(dataOverride)
                : repoRoot.resolve("server").resolve("data");
        webDir = webOverride != null
                ? Paths.get(webOverride)
                : repoRoot.resolve("server").resolve("webdist");
        sqliteFile = sqliteOverride != null
                ? Paths.get(sqliteOverride)
                : dataDir.resolve("timetable.db");
        mirroredJsonFiles = repoRoot == null
                ? List.of()
                : List.of(
                        repoRoot.resolve("app").resolve("src").resolve("main").resolve("assets").resolve("timetable.json"),
                        repoRoot.resolve("app").resolve("applet").resolve("app").resolve("src").resolve("main").resolve("assets").resolve("timetable.json")
                );
    }

    public Path timetableJson() {
        return dataDir.resolve("timetable.json");
    }

    public Path calendarJson() {
        return dataDir.resolve("calender.json");
    }

    public Path versionPolicy() {
        return dataDir.resolve("version_policy.json");
    }

    private static Path findRepoRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 8; depth++) {
            Path marker = current.resolve("server").resolve("data").resolve("timetable.json");
            if (Files.isRegularFile(marker)) {
                return current;
            }
            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return null;
    }
}