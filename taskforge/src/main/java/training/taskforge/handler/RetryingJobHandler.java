package training.taskforge.handler;

import training.taskforge.error.JobExecutionException;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;

import java.util.function.Consumer;

public class RetryingJobHandler implements JobHandler {
    private final JobHandler delegate;
    private final int maxRetries;
    private final Class<? extends JobExecutionException> onlyFor;
    private final Consumer<String> log;

    public RetryingJobHandler(JobHandler delegate, Consumer<String> log) {
        this.delegate = delegate;
        this.log = log;
        // Check if the delegate has a @Retryable annotation and extract its parameters
        Retryable meta = delegate.getClass().getAnnotation(Retryable.class);
        // If the annotation is present, use its parameters; otherwise, use default values
        if (meta != null) {
            this.maxRetries = meta.maxRetries();
            this.onlyFor = meta.onlyFor();
        } else {
            this.maxRetries = 1;
            this.onlyFor = JobExecutionException.class; // Default to retrying for all JobExecutionExceptions
        }
    }

    @Override
    public JobResult handle(Job job) throws JobExecutionException {
        JobExecutionException last = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                JobResult result = delegate.handle(job);
                if (attempt > 1) {
                    log.accept("[retry] %s %s attempt %d/%d succeeded"
                            .formatted(job.type(), job.shortId(), attempt, maxRetries));
                }
                return result;
            } catch (JobExecutionException e) {
                // If the exception is not of the type we want to retry for, rethrow it immediately
                if (!onlyFor.isInstance(e)) {
                    throw e;
                }
                last = e;
                log.accept("[retry] %s %s attempt %d/%d failed: %s"
                        .formatted(job.type(), job.shortId(), attempt, maxRetries, e.getMessage()));
            }
        }
        // If we exhausted all retries, throw the last exception
        throw last;
    }
}