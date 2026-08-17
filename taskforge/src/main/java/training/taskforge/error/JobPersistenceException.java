package training.taskforge.error;

public class JobPersistenceException extends RuntimeException {
    public JobPersistenceException(String message) {
        super(message);
    }
    public JobPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
