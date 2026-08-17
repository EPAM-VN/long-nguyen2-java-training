package training.taskforge.error;

public class InvalidJobConfigException extends RuntimeException {
    public InvalidJobConfigException(String message) {
        super(message);
    }

    public InvalidJobConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
