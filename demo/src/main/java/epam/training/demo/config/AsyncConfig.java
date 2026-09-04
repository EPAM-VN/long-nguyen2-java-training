package epam.training.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    // Named explicitly and referenced by name (@Async("taskAuditExecutor")),
    // not registered as the framework-wide default via
    // getAsyncExecutor() - an @Async method with no matching executor bean
    // falls back to SimpleAsyncTaskExecutor, which spins up a brand-new,
    // never-reused thread per invocation with no cap. Fine for a single
    // demo call; under real load that's an unbounded number of threads and
    // an easy way to take a JVM down. A small bounded pool here makes that
    // choice explicit instead of accidental.
    @Bean
    public Executor taskAuditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("task-audit-");
        executor.initialize();
        return executor;
    }

    // A void @Async method (TaskAuditListener.onTaskCreated) has no Future
    // for any caller to inspect - an exception thrown inside it doesn't
    // propagate anywhere, it's just swallowed by the executor. Without this
    // handler that failure is invisible; with it, it becomes a real ERROR
    // log line instead of a silent no-op.
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("Uncaught exception in async method '{}'", method.getName(), ex);
    }
}
