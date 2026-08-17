package training.taskforge.runner;

import training.taskforge.error.JobExecutionException;
import training.taskforge.handler.HandlerRegistry;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;
import training.taskforge.store.JobHistory;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractJobRunner implements JobRunner {
    protected final HandlerRegistry registry;
    private final AtomicLong processed = new AtomicLong();
    private final JobHistory history;

    public AbstractJobRunner(HandlerRegistry registry, JobHistory history) {
        this.registry = registry;
        this.history = history;
    }

    protected JobResult runOne(Job job) {
        long startNanos = System.nanoTime();
        JobResult result;
        try {
            result = registry.forType(job.type()).handle(job);
        } catch (JobExecutionException e) {
            result = new JobResult.Failure(job.id(), elapsed(startNanos), e.getMessage());
        } catch (RuntimeException e) {
            result = new JobResult.Failure(job.id(), elapsed(startNanos),
                    "unexpected " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        history.record(job, result);
        processed.incrementAndGet();
        return result;
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    @Override
    public long processedCount() {
        return processed.get();
    }

    // Unwraps the cause of a CompletionException or ExecutionException, if present.
    // CompletableFuture and Future.get() wrap exceptions in these types.
    protected static Throwable unwrap(Throwable t) {
        if ((t instanceof CompletionException || t instanceof ExecutionException)
                && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }
}
