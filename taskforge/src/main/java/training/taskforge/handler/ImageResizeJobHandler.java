package training.taskforge.handler;

import training.taskforge.error.JobExecutionException;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

public class ImageResizeJobHandler extends AbstractSimulatedHandler {
    private static final Set<String> SUPPORTED = Set.of(".jpg", ".jpeg", ".png");

    @Override
    public JobResult handle(Job job) throws JobExecutionException {
        String file = job.payload().get("file");
        if (file == null || file.isBlank()) {
            throw new JobExecutionException.HandlerCrashed("handler crashed: no file specified");
        }

        // Locale.ROOT is not paranoia. The default-locale toLowerCase() maps a dotted capital I
        // to a dotless i in Turkish, so ".PNG" would not match ".png" on a Turkish machine. This
        // is the classic "Turkish I problem"; C#'s ToLowerInvariant() exists for the same reason.
        String lower = file.toLowerCase(Locale.ROOT);
        boolean supported = SUPPORTED.stream().anyMatch(lower::endsWith);
        if (!supported) {
            throw new JobExecutionException.HandlerCrashed("handler crashed: unsupported format");
        }

        long millis = simulatedMillis(job, 80, 120);
        sleepMillis(millis);

        String width = job.payload().getOrDefault("width", "auto");
        return new JobResult.Success(
                job.id(),
                Duration.ofMillis(millis),
                "resized " + file + " to width " + width);
    }
}
