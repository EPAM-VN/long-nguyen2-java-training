package training.taskforge.runner;

import training.taskforge.handler.HandlerRegistry;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;
import training.taskforge.store.JobHistory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class VirtualThreadJobRunner extends AbstractJobRunner {
    public VirtualThreadJobRunner(HandlerRegistry registry, JobHistory history) {
        super(registry, history);
    }

    @Override
    public List<JobResult> runAll(List<Job> jobs) {
        try(var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<JobResult>> futures = jobs.stream()
                    .map(job -> CompletableFuture
                            .supplyAsync(() -> runOne(job), executor)
                            .exceptionally(ex -> new JobResult.Failure(
                                    job.id(),
                                    Duration.ZERO,
                                    String.valueOf(unwrap(ex).getMessage()))))
                    .toList();

            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    @Override
    public String name() {
        return "VirtualThreadJobRunner";
    }
}
