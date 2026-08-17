package training.taskforge.handler;

import training.taskforge.error.JobExecutionException;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;

import java.time.Duration;

public class CleanupJobHandler extends AbstractSimulatedHandler {
    @Override
    public JobResult handle(Job job) throws JobExecutionException {
        String age = job.payload().getOrDefault("olderThanDays", "0");

        long millis = simulatedMillis(job, 40, 80);
        sleepMillis(millis);

        return new JobResult.Success(
                job.id(),
                Duration.ofMillis(millis),
                "removed files older than " + age + " days");
    }
}
