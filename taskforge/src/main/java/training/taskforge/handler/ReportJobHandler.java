package training.taskforge.handler;

import training.taskforge.error.JobExecutionException;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;

import java.time.Duration;

@Retryable(maxRetries = 3, onlyFor = JobExecutionException.TimedOut.class)
public class ReportJobHandler extends AbstractSimulatedHandler {
    private static final long TIME_BUDGET_MS = 2_000;

    @Override
    public JobResult handle(Job job) throws JobExecutionException {
        long millis = simulatedMillis(job, 300, 2_100);

        if (millis > TIME_BUDGET_MS) {
            sleepMillis(TIME_BUDGET_MS);
            throw new JobExecutionException.TimedOut("timeout after 2s");
        }

        sleepMillis(millis);
        return new JobResult.Success(
                job.id(),
                Duration.ofMillis(millis),
                "report built for " + job.payload().getOrDefault("range", "unknown range"));
    }
}