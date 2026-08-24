package epam.training.demo.common;

// Shared across every feature (project, task, comment, ...) - anything with
// an id-based lookup throws this instead of returning/propagating null.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
