package training.taskforge.model;

import training.taskforge.error.InvalidJobConfigException;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum JobType {
    EMAIL,
    REPORT,
    CLEANUP,
    IMAGE_RESIZE;

    public static JobType parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidJobConfigException(
                    "invalid type '" + raw + "' (expected " + legalValues() + ")", e);
        }
    }

    private static String legalValues() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}