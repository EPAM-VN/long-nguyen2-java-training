package training.taskforge.report;

import training.taskforge.model.HistoryEntry;
import training.taskforge.model.JobResult;
import training.taskforge.model.JobStatus;
import training.taskforge.model.JobType;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportGenerator {
    public String generate(List<HistoryEntry> history) {
        if (history.isEmpty()) {
            return "TaskForge job report\nnothing has run yet.\n";
        }

        Map<Boolean, Long> outcome = countByOutcome(history);
        long succeeded = outcome.getOrDefault(true, 0L);
        long failed = outcome.getOrDefault(false, 0L);

        DurationStats stats = durationStats(history);

        return """
                TaskForge job report
                generated: %s
                
                total: %d   succeeded: %d   failed: %d
                
                by type:
                %s
                duration: avg %.1fms   max %dms (%s)
                %s"""
                .formatted(
                        Instant.now(),
                        history.size(), succeeded, failed,
                        renderByType(history),
                        stats.averageMillis(),
                        stats.max().toMillis(),
                        stats.slowestTypeName(),
                        renderFailures(history));
    }

    private Map<Boolean, Long> countByOutcome(List<HistoryEntry> history) {
        return history.stream().collect(Collectors.partitioningBy(
                entry -> entry.status() == JobStatus.SUCCEEDED,
                Collectors.counting()));
    }

    private Map<JobType, Map<Boolean, Long>> countByTypeAndOutcome(List<HistoryEntry> history) {
        return history.stream().collect(Collectors.groupingBy(
                entry -> entry.job().type(),
                () -> new EnumMap<JobType, Map<Boolean, Long>>(JobType.class),
                Collectors.partitioningBy(
                        entry -> entry.status() == JobStatus.SUCCEEDED,
                        Collectors.counting())));
    }

    public DurationStats durationStats(List<HistoryEntry> history) {
        if (history.isEmpty()) {
            return DurationStats.empty();
        }
        return history.stream().collect(Collectors.teeing(
                Collectors.averagingLong(HistoryEntry::durationMillis),
                Collectors.maxBy(Comparator.comparingLong((HistoryEntry e) -> e.durationMillis())),
                (avg, slowest) -> new DurationStats(
                        avg,
                        slowest.map(e -> e.result().duration()).orElse(java.time.Duration.ZERO),
                        slowest.map(e -> e.job().type()).orElse(null))));
    }

    private String renderFailures(List<HistoryEntry> history) {
        return history.stream()
                .filter(entry -> entry.result() instanceof JobResult.Failure)
                .map(entry -> "  %-13s %-22s -> %s".formatted(
                        entry.job().type(),
                        truncate(entry.job().payloadAsString(), 22),
                        entry.message()))
                .collect(Collectors.joining("\n", "\nfailures:\n", "\n"));
    }

    private String renderByType(List<HistoryEntry> history) {
        Map<JobType, Map<Boolean, Long>> byType = countByTypeAndOutcome(history);

        return byType.entrySet().stream()
                .map(e -> {
                    long ok = e.getValue().getOrDefault(true, 0L);
                    long bad = e.getValue().getOrDefault(false, 0L);
                    return "  %-14s %d  (%d ok, %d failed)".formatted(e.getKey(), ok + bad, ok, bad);
                })
                .collect(Collectors.joining("\n"));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "\u2026";
    }
}
