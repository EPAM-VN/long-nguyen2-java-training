package epam.training.demo.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// A separate service class, not a private method on TaskService - same
// self-invocation pitfall as ProjectService.createBatchUnsafe /
// ProjectBatchService (see that pair's comments), just for propagation
// instead of the default REQUIRED: TaskService.update() calling
// this.recordConflict(...) directly would never go through this bean's
// Spring AOP proxy, so the @Transactional(REQUIRES_NEW) below would be
// silently ignored and recordConflict() would just join update()'s own
// transaction like any other call - defeating the entire point, which is
// that this write must survive even when the caller's transaction doesn't.
@Service
public class TaskConflictLogService {

    private final TaskConflictLogRepository taskConflictLogRepository;

    public TaskConflictLogService(TaskConflictLogRepository taskConflictLogRepository) {
        this.taskConflictLogRepository = taskConflictLogRepository;
    }

    // REQUIRES_NEW: TaskService.update() calls this from inside its own
    // still-open @Transactional method, right before throwing
    // ObjectOptimisticLockingFailureException - an exception that's about
    // to mark that outer transaction rollback-only. If this method just
    // joined that transaction (the default, PROPAGATION_REQUIRED), this
    // conflict log row would roll back right along with it, so the one
    // piece of evidence that a conflict happened would vanish along with
    // the failed update it's meant to record. REQUIRES_NEW suspends the
    // caller's transaction, opens a brand new one on a separate physical
    // connection, commits THIS write here, then resumes the caller's
    // transaction - so this row survives regardless of what update() does
    // next.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordConflict(Long taskId, Long projectId, Long attemptedVersion, Long actualVersion) {
        TaskConflictLog conflictLog = new TaskConflictLog();
        conflictLog.setTaskId(taskId);
        conflictLog.setProjectId(projectId);
        conflictLog.setAttemptedVersion(attemptedVersion);
        conflictLog.setActualVersion(actualVersion);
        conflictLog.setOccurredAt(Instant.now());
        taskConflictLogRepository.save(conflictLog);
    }
}
