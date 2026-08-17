package training.taskforge.handler;

import training.taskforge.error.InvalidJobConfigException;
import training.taskforge.model.JobType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class HandlerRegistry {
    private final Map<JobType, JobHandler> handlers;

    public HandlerRegistry() {
        this(System.out::println);
    }

    public HandlerRegistry(Consumer<String> log) {
        this.handlers = new EnumMap<JobType, JobHandler>(Map.of(
                JobType.EMAIL, new RetryingJobHandler(new EmailJobHandler(), log),
                JobType.REPORT, new RetryingJobHandler(new ReportJobHandler(), log),
                JobType.CLEANUP, new RetryingJobHandler(new CleanupJobHandler(), log),
                JobType.IMAGE_RESIZE, new RetryingJobHandler(new ImageResizeJobHandler(), log)
        ));
    }

    public JobHandler forType(JobType type) {
        return Optional.ofNullable(handlers.get(type))
                .orElseThrow(() -> new InvalidJobConfigException("no handler registered for " + type));
    }
}
