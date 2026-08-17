package training.taskforge.model;

import java.time.Duration;
import java.util.UUID;

public sealed interface JobResult {
    UUID jobId();
    Duration duration();
    String describe();
    JobStatus status();

    record Success(UUID jobId, Duration duration, String output) implements JobResult {
        @Override
        public String describe() {
            String shortId = jobId.toString().substring(0, 8);
            return "Job %s done in %dms: %s".formatted(shortId, duration.toMillis(), output);
        }

        @Override
        public JobStatus status() {
            return JobStatus.SUCCEEDED;
        }
    }
    record Failure(UUID jobId, Duration duration, String error) implements JobResult {
        @Override
        public String describe() {
            String shortId = jobId.toString().substring(0, 8);
            return "Job %s failed after %dms: %s".formatted(shortId, duration.toMillis(), error);
        }

        @Override
        public JobStatus status() {
            return JobStatus.FAILED;
        }
    }
}