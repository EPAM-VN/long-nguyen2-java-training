package training.taskforge.handler;

import training.taskforge.error.JobExecutionException;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Retryable {
    int maxRetries() default 3;
    // Only retry when the thrown exception is an instance of this type.
    Class<? extends JobExecutionException> onlyFor() default JobExecutionException.class;
}
