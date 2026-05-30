package com.mpfm.backend.application.audit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 后端日志读取服务，按行尾部读取应用日志供 root 调试页面展示。
 */
@Service
public class BackendLogReadService {
    private final Path appLogPath;

    public BackendLogReadService(
            @Value("${mpfm.admin.logs.app-log-path:}") String appLogPathConfig,
            @Value("${spring.application.name:mpfm-backend}") String appName) {
        String resolved = appLogPathConfig == null || appLogPathConfig.isBlank()
                ? "./logs/" + appName + ".log"
                : appLogPathConfig;
        this.appLogPath = Paths.get(resolved).normalize();
    }

    public String logFile() {
        return appLogPath.toString();
    }

    public String activeLogFileName() {
        Path fileName = appLogPath.getFileName();
        return fileName == null ? appLogPath.toString() : fileName.toString();
    }

    public Path logPath() {
        return appLogPath;
    }

    public boolean logExists() {
        return Files.exists(appLogPath);
    }

    public boolean logExists(Path path) {
        return path != null && Files.exists(path) && Files.isRegularFile(path);
    }

    public Optional<Path> resolveLogPath(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.of(appLogPath);
        }
        Path baseDir = appLogPath.getParent();
        if (baseDir == null) {
            return Optional.empty();
        }
        String normalized = fileName.trim();
        if (normalized.contains("..") || normalized.contains("/") || normalized.contains("\\") || normalized.contains(":")) {
            return Optional.empty();
        }
        Path candidate = baseDir.resolve(normalized).normalize();
        if (!candidate.startsWith(baseDir.normalize())) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    public List<String> listLogFiles() {
        Path baseDir = appLogPath.getParent();
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return List.of(activeLogFileName());
        }
        String prefix = activeLogFileName();
        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(baseDir)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(prefix))
                    .sorted(Comparator.reverseOrder())
                    .forEach(result::add);
        } catch (Exception ex) {
            return List.of(activeLogFileName());
        }
        if (result.isEmpty()) {
            return List.of(activeLogFileName());
        }
        return result;
    }

    public List<String> readAllLines() {
        return readAllLines(appLogPath);
    }

    public List<String> readAllLines(Path path) {
        if (!logExists(path)) {
            return List.of();
        }
        try {
            String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".gz")) {
                try (GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(path));
                     InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
                     BufferedReader bufferedReader = new BufferedReader(reader)) {
                    return bufferedReader.lines().toList();
                }
            }
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<String> readTailLines(int maxLines) {
        return readTailLines(appLogPath, maxLines);
    }

    public List<String> readTailLines(Path path, int maxLines) {
        if (!logExists(path) || maxLines <= 0) {
            return List.of();
        }
        try {
            List<String> all = readAllLines(path);
            if (all.size() <= maxLines) {
                return all;
            }
            return all.subList(all.size() - maxLines, all.size());
        } catch (Exception ex) {
            return List.of();
        }
    }
}
