package training.taskforge.handler;

import training.taskforge.error.JobExecutionException;
import training.taskforge.model.Job;

public abstract class AbstractSimulatedHandler implements JobHandler {
    protected long simulatedMillis(Job job, long minMs, long maxMs) {
        long span = maxMs - minMs + 1;
        return minMs + Math.floorMod(job.id().getLeastSignificantBits(), span);
    }

    protected void sleepMillis(long millis) throws JobExecutionException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // restore the flag we just consumed
            throw new JobExecutionException.HandlerCrashed("interrupted while working", e);
        }
    }
}
