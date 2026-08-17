package training.taskforge.runner;

import training.taskforge.handler.HandlerRegistry;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;
import training.taskforge.store.JobHistory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncJobRunner extends AbstractJobRunner {
    private static final int POOL_SIZE = 8;

    public AsyncJobRunner(HandlerRegistry registry, JobHistory history) {
        super(registry, history);
    }

    @Override
    public List<JobResult> runAll(List<Job> jobs) {
        try (ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE)) {
            List<CompletableFuture<JobResult>> futures = jobs.stream()
                    .map(job -> CompletableFuture
                            .supplyAsync(() -> runOne(job), pool)
                            .exceptionally(ex -> new JobResult.Failure(
                                    job.id(),
                                    Duration.ZERO,
                                    String.valueOf(unwrap(ex).getMessage()))))
                    .toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        }
    }

    @Override
    public String name() {
        return "AsyncJobRunner";
    }
}
