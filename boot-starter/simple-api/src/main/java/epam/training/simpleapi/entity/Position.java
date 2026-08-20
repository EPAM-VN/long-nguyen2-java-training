package epam.training.simpleapi.entity;

public enum Position {
    HOUSEKEEPING,
    FRONT_DESK,
    MAINTENANCE,
    MANAGEMENT,
    CONCIERGE,
    SECURITY,
    OTHER;

    public String toString() {
        return switch (this) {
            case HOUSEKEEPING -> "House Keeping";
            case FRONT_DESK -> "Front Desk";
            case MAINTENANCE -> "Maintenance";
            case MANAGEMENT -> "Management";
            case CONCIERGE -> "Concierge";
            case SECURITY -> "Security";
            case OTHER -> "Other";
            default -> "";
        };
    }
}
