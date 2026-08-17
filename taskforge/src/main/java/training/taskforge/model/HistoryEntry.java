package training.taskforge.model;

import java.time.Instant;

public record HistoryEntry(Instant finishedAt, Job job, JobResult result) {
    public HistoryEntry {
        if (finishedAt == null || job == null || result == null) {
            throw new IllegalArgumentException("history entry fields must not be null");
        }
    }

    public JobStatus status() {
        return result.status();
    }

    public long durationMillis() {
        return result.duration().toMillis();
    }

    public String message() {
        return switch (result) {
            case JobResult.Success s -> s.output();
            case JobResult.Failure f -> f.error();
        };
    }
}
