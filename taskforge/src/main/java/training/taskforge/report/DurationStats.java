package training.taskforge.report;

import training.taskforge.model.JobType;

import java.time.Duration;

public record DurationStats(double averageMillis, Duration max, JobType slowestType) {
    public static DurationStats empty() {
        return new DurationStats(0.0, Duration.ZERO, null);
    }

    public String slowestTypeName() {
        return slowestType == null ? "n/a" : slowestType.name();
    }
}
