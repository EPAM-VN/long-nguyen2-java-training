package epam.training.demo.task;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TaskAuditListener {

    private static final Logger log = LoggerFactory.getLogger(TaskAuditListener.class);

    private final MeterRegistry meterRegistry;

    public TaskAuditListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // AFTER_COMMIT, not a plain @EventListener: TaskService.create()
    // publishes this event from inside its own @Transactional method,
    // before that transaction has actually committed. A plain
    // @EventListener runs synchronously right then, inside the
    // not-yet-committed transaction - if something later in that same
    // transaction rolled it back, an audit entry would already exist for a
    // task that was never really persisted. AFTER_COMMIT defers the
    // listener until the transaction is guaranteed to have succeeded. One
    // gotcha this brings with it: fallbackExecution defaults to false, so
    // if this event were ever published with no transaction active at all,
    // the listener would silently never run rather than running inline -
    // fine here since create() is always @Transactional, but worth knowing
    // before reusing this pattern somewhere that isn't.
    //
    // @Async on top moves the actual listener invocation onto
    // taskAuditExecutor's pool instead of running inline on the thread that
    // just committed the transaction - a slow or failing audit write can
    // never add latency to, or fail, a request whose response already went
    // out.
    @Async("taskAuditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent event) {
        // No per-task/per-project tag here on purpose - project/task ids
        // are effectively unbounded (a new one exists for every row ever
        // created), and a Micrometer tag value becomes a distinct time
        // series per unique value seen. Tagging by an unbounded id is
        // exactly how a metrics backend's cardinality blows up; a plain,
        // untagged counter (just "how many tasks total") is the safe
        // choice here.
        //
        // Incremented BEFORE the log line, not after - a test observing
        // this event happen (e.g. polling for the log line via a Logback
        // ListAppender, as TaskAuditListenerTest does) runs on a different
        // thread than this method. Logging first would let that test
        // observe "the log line exists" while "the counter was
        // incremented" is still just a program-order guarantee on THIS
        // thread, with no happens-before edge yet connecting the two -
        // technically racy. Incrementing first means the log line (which
        // the test synchronizes on through the appender's own internal
        // lock) can never become visible before the increment is.
        meterRegistry.counter("task.created").increment();
        log.info("[audit] task {} '{}' created in project {}",
                event.taskId(), event.title(), event.projectId());
    }
}
