package training.taskforge.util;

import training.taskforge.error.InvalidJobConfigException;
import training.taskforge.error.JobPersistenceException;
import training.taskforge.model.Job;
import training.taskforge.model.JobPriority;
import training.taskforge.model.JobType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

public final class JobHelper {
    public static LoadResult loadCSV(Path path) throws IOException {
        List<String> lines;

        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new JobPersistenceException("CSV file not found: " + path, e);
        } catch (IOException e) {
            throw new JobPersistenceException("Error reading CSV file: " + path, e);
        }

        List<Job> jobs = new ArrayList<>();
        List<String> rejectedRows = new ArrayList<>();

        // Skip the header row and process each subsequent line
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue; // Skip empty lines
            }
            try {
                jobs.add(parseRow(line));
            } catch (InvalidJobConfigException e) {
                rejectedRows.add("Line " + (i + 1) + ": " + e.getMessage());
            }
        }

        return new LoadResult(jobs, rejectedRows);
    }

    private static Job parseRow(String line) {
        String[] parts = line.split(",", 3);
        if (parts.length < 2) {
            throw new InvalidJobConfigException("Invalid format, expected: type,priority,payload");
        }

        JobType type = JobType.parse(parts[0]);
        JobPriority priority = JobPriority.parse(parts[1]);
        Map<String, String> payload = parsePayload(parts.length == 3 ? parts[2] : "");

        return Job.create(type, priority, payload);
    }

    // use LinkedHashMap to preserve the order of insertion
    public static Map<String, String> parsePayload(String raw) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return payload;   // empty is legal
        }
        String[] pairs = raw.split(";");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq == -1) {
                throw new InvalidJobConfigException("Invalid payload format, expected key=value pairs separated by semicolons");
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (key.isEmpty()) {
                throw new InvalidJobConfigException("Payload key cannot be empty");
            }
            payload.put(key, value);
        }
        return payload;
    }
}
