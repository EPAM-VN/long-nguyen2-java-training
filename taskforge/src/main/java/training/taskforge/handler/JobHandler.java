package training.taskforge.handler;

import training.taskforge.error.JobExecutionException;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;

@FunctionalInterface
public interface JobHandler {
    JobResult handle(Job job) throws JobExecutionException;

    default JobHandler andThen(JobHandler next) {
        return job -> switch (handle(job)) {
            case JobResult.Success ignored -> next.handle(job);
            // Short-circuit
            case JobResult.Failure failure -> failure;
        };
    }
}
