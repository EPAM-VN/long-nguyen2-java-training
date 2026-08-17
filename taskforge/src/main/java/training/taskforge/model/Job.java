package training.taskforge.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public record Job(UUID id,
                  long seq,
                  JobType type,
                  JobPriority priority,
                  Map<String, String> payload,
                  Instant createdAt
) implements Comparable<Job> {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    // Defensive copy of the payload map to ensure immutability
    public Job {
        payload = Map.copyOf(payload);
    }

    public static Job create(JobType type, JobPriority priority, Map<String, String> payload) {
        return new Job(UUID.randomUUID(), SEQUENCE.incrementAndGet(), type, priority, payload, Instant.now());
    }

    // Comparator for natural ordering of jobs: higher priority first, then by creation time, then by sequence number
    private static final Comparator<Job> NATURAL_ORDER =
            Comparator.comparingInt((Job j) -> j.priority().weight()).reversed()
                    .thenComparing(Job::createdAt)
                    .thenComparingLong(Job::seq);

    @Override
    public int compareTo(Job other) {
        return NATURAL_ORDER.compare(this, other);
    }

    public String payloadAsString() {
        return payload.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }
}