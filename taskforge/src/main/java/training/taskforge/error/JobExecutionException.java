package training.taskforge.error;

public sealed abstract class JobExecutionException extends Exception
        permits JobExecutionException.TimedOut, JobExecutionException.HandlerCrashed {

    protected JobExecutionException(String message) {
        super(message);
    }

    protected JobExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public static final class TimedOut extends JobExecutionException {
        public TimedOut(String message) {
            super(message);
        }
    }

    public static final class HandlerCrashed extends JobExecutionException {
        public HandlerCrashed(String message) {
            super(message);
        }

        public HandlerCrashed(String message, Throwable cause) {
            super(message, cause);
        }
    }
}