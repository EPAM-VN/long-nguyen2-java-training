package epam.training.demo.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TaskAuditListener {

    private static final Logger log = LoggerFactory.getLogger(TaskAuditListener.class);

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
        log.info("[audit] task {} '{}' created in project {}",
                event.taskId(), event.title(), event.projectId());
    }
}
